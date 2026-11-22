package com.music.bitchord.data.stats

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.Taste
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.normalizedArtistName

/**
 * Assembles the listener's [Taste.Snapshot] from what this device already
 * knows — the Replay chart, the session's likes and dislikes, and the skips
 * [ListeningRecorder] counts. No new requests, no new permissions, and the
 * result never leaves the phone: the only network a mood station makes is
 * the YouTube Music one it always made.
 *
 * The build is deliberately cheap and defensive: it runs on Home's Top Picks
 * refresh, so every source is read with a fallback, and any piece that fails
 * simply contributes nothing — a thinner profile, never a broken one.
 */
object TasteProfile {

    /** Enough artists to recognise a queue's familiar faces without persisting a chart. */
    private const val MAX_ARTISTS = 40

    /** The chart toppers a mood station may blend in from. */
    private const val MAX_TOP_SONGS = 15

    /** Recent genres carry more of the answer than the library's whole life. */
    private const val MAX_GENRES = 8

    /** Likes persisted for matching; the session rarely knows more. */
    private const val MAX_LIKED = 200
    private const val MAX_SKIPPED = 300

    /** This month's listening counts treble against the all-time chart. */
    private const val RECENT_WEIGHT = 3.0

    /**
     * An artist needs this many skips before the skipping says something —
     * one skipped track is a mood, not a verdict.
     */
    private const val HEAVY_SKIP_MIN = 4

    /**
     * Disliked tracks by one artist before the *artist* is read as a negative
     * — and even then only demoted, never banned (see [Taste]).
     */
    private const val ARTIST_DISLIKE_MIN = 2

    suspend fun current(): Taste.Snapshot = current(
        readSummary = { ListeningStats.summary(it) },
        readSkips = { ListeningStats.skips(ReplayPeriod.ALL_TIME) },
    )

    /** Injectable source reads let regressions suspend at the real rebuild boundary. */
    internal suspend fun current(
        readSummary: suspend (ReplayPeriod) -> ReplaySummary?,
        readSkips: suspend () -> Map<String, Int>,
    ): Taste.Snapshot {
        val generation = com.music.bitchord.data.innertube.Innertube.listenerGeneration
        suspend fun reconcile(snapshot: Taste.Snapshot): Taste.Snapshot {
            currentCoroutineContext().ensureActive()
            return com.music.bitchord.data.TopPicks.reconcileTaste(snapshot, generation)
                ?: throw CancellationException("Listener changed during taste rebuild")
        }
        suspend fun <T> sourceOrNull(read: suspend () -> T): T? = try { read() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        val month = sourceOrNull { readSummary(ReplayPeriod.THIS_MONTH) }
        val allTime = sourceOrNull { readSummary(ReplayPeriod.ALL_TIME) }
        // A missing chart is not evidence that the listener undid a dislike.
        // The bound cache supplies known exact negatives; live ratings win.
        val skips = sourceOrNull { readSkips() }.orEmpty()
        val dislikedIds = reconcile(Taste.Snapshot()).dislikedIds
        if (month == null && allTime == null) return reconcile(Taste.Snapshot(dislikedIds = dislikedIds))
        val ratings = LikeState.overrides.value

        // Artists: the all-time chart weighted once, this month's treble, so a
        // new obsession outranks an old one without a month of waiting.
        val artistScores = LinkedHashMap<String, Double>()
        fun credit(name: String, ms: Long, weight: Double) {
            val key = normalizedArtistName(name)
            if (key.isEmpty()) return
            artistScores[key] = (artistScores[key] ?: 0.0) + ms * weight
        }
        allTime?.artists?.forEach { credit(it.title, it.ms, 1.0) }
        month?.artists?.forEach { credit(it.title, it.ms, RECENT_WEIGHT) }
        val artists = artistScores.entries
            .sortedByDescending { it.value }
            .take(MAX_ARTISTS)
            .map { it.key }

        // Genres drive the scene words a mood shelf is matched against.
        val genres = (month?.genres.orEmpty() + allTime?.genres.orEmpty())
            .map { it.title.lowercase() }
            .distinct()
            .take(MAX_GENRES)

        // The listener's own toppers, this month first — what a mood station
        // blends in when the mood's playlists already carry the artist.
        val topSongs = (month?.songs.orEmpty() + allTime?.songs.orEmpty())
            .distinctBy { it.song.videoId }
            .take(MAX_TOP_SONGS)
            .map {
                Taste.TasteSong(
                    videoId = it.song.videoId,
                    title = it.song.title,
                    artist = it.song.artist,
                    artistId = it.song.artistId,
                    thumbnailUrl = it.song.thumbnailUrl,
                )
            }

        val likedIds = ratings.filterValues { it == LikeStatus.LIKE }.keys.take(MAX_LIKED)

        // Demotions, and only demotions: an artist earns one from a pattern —
        // two or more thumbed-down tracks, or skips outnumbering plays two to
        // one past a floor. A single disliked track removes that track (the
        // listener said so, about that song) and says nothing about its maker.
        val knownSongs = (month?.songs.orEmpty() + allTime?.songs.orEmpty())
            .associateBy { it.song.videoId }
        fun artistOf(videoId: String): String? = knownSongs[videoId]?.song?.artist
            ?.let(ListeningStats::primaryArtist)
            ?.let(::normalizedArtistName)
            ?.takeIf { it.isNotEmpty() }
        val demoted = LinkedHashSet<String>()
        val dislikesByArtist = HashMap<String, Int>()
        dislikedIds.forEach { id -> artistOf(id)?.let { dislikesByArtist.merge(it, 1, Int::plus) } }
        dislikesByArtist.filterValues { it >= ARTIST_DISLIKE_MIN }.keys.forEach { demoted += it }
        val skipByArtist = HashMap<String, Int>()
        val playsByArtist = HashMap<String, Int>()
        skips.forEach { (id, count) -> artistOf(id)?.let { skipByArtist.merge(it, count, Int::plus) } }
        knownSongs.values.forEach { ranked ->
            artistOf(ranked.song.videoId)?.let { playsByArtist.merge(it, ranked.plays, Int::plus) }
        }
        skipByArtist.forEach { (artist, skipCount) ->
            val plays = playsByArtist[artist] ?: 0
            if (skipCount >= HEAVY_SKIP_MIN && plays * 2 <= skipCount) demoted += artist
        }

        return reconcile(Taste.Snapshot(
            artists = artists,
            languages = Taste.deriveLanguages(genres),
            genres = genres,
            likedIds = likedIds,
            dislikedIds = dislikedIds,
            topSongs = topSongs,
            skippedIds = skips.entries
                .sortedByDescending { it.value }
                .take(MAX_SKIPPED)
                .associate { it.toPair() },
            demotedArtists = demoted.toList(),
        ))
    }
}
