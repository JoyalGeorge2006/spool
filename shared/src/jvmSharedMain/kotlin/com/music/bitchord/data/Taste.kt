package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.TopPickMood
import com.music.bitchord.data.model.normalizedArtistName
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * What the app knows about the listener's own taste, and the mood-station
 * maths that turns it into a better queue.
 *
 * ## Where the profile comes from
 *
 * Nothing here asks the listener anything. The snapshot is assembled on the
 * app side (see TasteProfile in the app module) from what the phone already
 * knows: the Replay chart this device keeps, the likes and dislikes handed
 * to YouTube, and the skips [ListeningRecorder] counts. It never leaves the
 * device — the only requests a mood tap makes are the same YouTube Music
 * ones it always made.
 *
 * ## What a mood station does with it
 *
 * The mood still comes from YouTube's own mood shelves — that catalogue is
 * the ceiling on what any mood station can play. What changes is the pick
 * and the order: shelves are matched against the listener's own scenes and
 * genres instead of the phone's region alone, and the assembled queue is
 * reranked so the opening stretch leans on the listener's own artists,
 * capped so the station keeps its mood rather than becoming a replay of the
 * library.
 *
 * Negatives are kept honest about their strength. A thumbed-down *track*
 * leaves the queue — that is exactly what the listener said. An *artist* is
 * only ever demoted toward the back, never banned: one bad song is not a
 * verdict on the person, and even two thumbed-down songs are a mood, not a
 * policy.
 *
 * Every step is fail-closed: an empty profile, an empty queue after
 * filtering, or any missing signal returns today's behaviour exactly, so a
 * fresh install and a signed-out listener get the stations they always had.
 */
object Taste {

    /** One of the listener's own chart toppers, kept small enough to persist. */
    @Serializable
    data class TasteSong(
        val videoId: String,
        val title: String,
        val artist: String,
        val artistId: String? = null,
        val thumbnailUrl: String? = null,
    ) {
        fun toSong(): Song = Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            artistId = artistId,
        )
    }

    /**
     * The profile, best-first everywhere. Persisted beside the Top Picks row
     * as JSON, so a mood tapped before this hour's rebuild still knows the
     * listener.
     */
    @Serializable
    data class Snapshot(
        /** Normalised artist names, most listened first. */
        val artists: List<String> = emptyList(),
        /** Scene words derived from the listener's own genres, e.g. "malayalam". */
        val languages: List<String> = emptyList(),
        /** The genres they were derived from, lowercased, most listened first. */
        val genres: List<String> = emptyList(),
        /** Tracks thumbed up this session's known ratings. */
        val likedIds: List<String> = emptyList(),
        /** Tracks thumbed down — those exact tracks leave a mood queue. */
        val dislikedIds: List<String> = emptyList(),
        /** The listener's own chart toppers, for blending into mood queues. */
        val topSongs: List<TasteSong> = emptyList(),
        /** Track id to times skipped before the play threshold. */
        val skippedIds: Map<String, Int> = emptyMap(),
        /**
         * Normalised artist names pushed toward the back of a mood queue —
         * demotion, never removal: the evidence behind them is an inference,
         * and an inference does not get to ban a musician.
         */
        val demotedArtists: List<String> = emptyList(),
    ) {
        /** Nothing learned yet — every caller falls back to today's behaviour. */
        val isEmpty: Boolean
            get() = artists.isEmpty() && topSongs.isEmpty() && likedIds.isEmpty() && genres.isEmpty()

        /** The words a mood shelf is matched against: scenes first, then raw genres. */
        fun hintWords(): List<String> = (languages + genres).distinct()
    }

    // ---- Languages and scenes ------------------------------------------------

    /**
     * Scene words YouTube's mood shelf titles use, looked for in the
     * listener's own Replay genres. This replaces the old bet that the
     * phone's region is the listener's taste: an Indian phone whose owner
     * listens to Malayalam music gets "malayalam", not just "bollywood".
     *
     * These are hints, not facts: a genre names a scene with confidence, but
     * taste is mixed, so the words only ever nudge a shelf score (+1 against
     * the mood's own doubled words) and the raw genres stay in the running
     * alongside them.
     */
    private val SCENE_WORDS = listOf(
        "malayalam", "tamil", "telugu", "kannada", "bollywood", "hindi",
        "punjabi", "desi", "indian", "pakistani", "urdu", "k-pop", "korean",
        "j-pop", "japanese", "latin", "reggaeton", "sertanejo", "afrobeats",
        "arabic", "turkish", "french", "german", "spanish", "indie", "lo-fi",
    )

    /**
     * The scenes found in [genres], in the order the listener plays them. A
     * genre that names no known scene still earns its place through
     * [Snapshot.hintWords], which keeps the raw genre as a weak hint.
     */
    fun deriveLanguages(genres: List<String>): List<String> {
        val found = LinkedHashSet<String>()
        genres.forEach { genre ->
            val lower = genre.lowercase(Locale.ROOT)
            SCENE_WORDS.forEach { scene -> if (scene in lower) found += scene }
        }
        return found.toList()
    }

    // ---- Shelf scoring -------------------------------------------------------

    /**
     * How well a mood category shelf fits: the mood's own words weigh double,
     * the listener's hints single. Kept pure so the steering is testable
     * without a network.
     */
    internal fun shelfScore(shelfTitle: String, mood: TopPickMood, hints: List<String>): Int {
        val title = shelfTitle.lowercase(Locale.ROOT)
        return 2 * mood.shelfWords.count { it in title } +
            hints.count { it.isNotBlank() && it in title }
    }

    // ---- Queue shaping ---------------------------------------------------------

    /** The most own-top-songs a mood queue picks up; the mood stays in charge. */
    internal const val BLEND_CAP = 10

    /** A track skipped at least this often stops showing up near the front. */
    internal const val SKIP_DEMOTE_COUNT = 2

    /** A track skipped this often is not blended back in at all. */
    internal const val SKIP_BLEND_LIMIT = 3

    /** Below this many songs a filtered station would be over before it got going. */
    internal const val MIN_QUEUE = 12

    /** The keys an artist match is tried on: the lead credit and every linked name. */
    private fun artistKeys(song: Song): Set<String> = buildSet {
        val primary = TrackMatcher.primaryArtist(song.artist)
        if (primary.isNotEmpty()) add(normalizedArtistName(primary))
        song.artists.forEach { add(normalizedArtistName(it.name)) }
        if (isEmpty()) add(normalizedArtistName(song.artist))
    }

    private fun isFamiliar(song: Song, sets: Sets): Boolean =
        song.videoId in sets.liked || song.videoId in sets.topIds ||
            artistKeys(song).any { it in sets.artists }

    /** Lookup sets built once per rerank rather than per song. */
    private class Sets(taste: Snapshot) {
        val artists = taste.artists.toHashSet()
        val liked = taste.likedIds.toHashSet()
        val topIds = taste.topSongs.mapTo(HashSet()) { it.videoId }
        val disliked = taste.dislikedIds.toHashSet()
        val demoted = taste.demotedArtists.toHashSet()
    }

    /**
     * A mood queue reshaped around the listener: their own artists and liked
     * songs open the station, capped near 60% of the first half so the mood
     * survives; thumbed-down tracks leave; twice-skipped songs and demoted
     * artists sink to the back.
     *
     * Fail-closed in both directions: nothing learned, nothing changed;
     * filtered down to a stub, the unfiltered queue comes back.
     */
    fun rerank(songs: List<Song>, taste: Snapshot?): List<Song> {
        if (taste == null || taste.isEmpty || songs.isEmpty()) return songs
        val sets = Sets(taste)
        val kept = songs.filter { it.videoId !in sets.disliked }
        if (kept.size < MIN_QUEUE) return songs

        val familiar = kept.filter { isFamiliar(it, sets) }
        val fresh = kept.filterNot { isFamiliar(it, sets) }
        if (familiar.isEmpty() || fresh.isEmpty()) return demote(kept, taste, sets)

        val half = kept.size / 2
        // The opening half leans familiar without becoming a library replay:
        // familiar tracks take at most three of every five slots there.
        val familiarUpFront = minOf(familiar.size, (half * 3 + 4) / 5)
        val front = interleave(
            familiar.take(familiarUpFront),
            fresh.take(half - familiarUpFront),
        )
        val back = familiar.drop(familiarUpFront) + fresh.drop(half - familiarUpFront)
        return demote(front + back, taste, sets)
    }

    /** Round-robin two lists, first list first; the longer one's tail simply appends. */
    private fun interleave(first: List<Song>, second: List<Song>): List<Song> {
        val out = ArrayList<Song>(first.size + second.size)
        val longer = maxOf(first.size, second.size)
        for (i in 0 until longer) {
            if (i < first.size) out += first[i]
            if (i < second.size) out += second[i]
        }
        return out
    }

    /** Skipped tracks and demoted artists behind everything else, order kept. */
    private fun demote(songs: List<Song>, taste: Snapshot, sets: Sets): List<Song> {
        if (taste.skippedIds.isEmpty() && sets.demoted.isEmpty()) return songs
        val (back, front) = songs.partition { song ->
            (taste.skippedIds[song.videoId] ?: 0) >= SKIP_DEMOTE_COUNT ||
                artistKeys(song).any { it in sets.demoted }
        }
        return if (back.isEmpty() || front.isEmpty()) songs else front + back
    }

    /**
     * Up to [BLEND_CAP] of the listener's own chart toppers folded into a mood
     * queue — only ones whose artist the mood's own playlists already carry,
     * which is what keeps a Chill station chill. No extra requests: the songs
     * come from the profile, and YouTube already proved the artist fits.
     */
    fun blend(queue: List<Song>, taste: Snapshot?): List<Song> {
        if (taste == null || taste.isEmpty || queue.isEmpty()) return queue
        val sets = Sets(taste)
        val inQueue = queue.flatMap { artistKeys(it) }.toHashSet()
        val ids = queue.mapTo(HashSet()) { it.videoId }
        val additions = taste.topSongs
            .asSequence()
            .filter { it.videoId !in ids && it.videoId !in sets.disliked }
            .filter { (taste.skippedIds[it.videoId] ?: 0) < SKIP_BLEND_LIMIT }
            .filter { song ->
                val key = normalizedArtistName(TrackMatcher.primaryArtist(song.artist))
                key.isNotEmpty() && key in inQueue && key !in sets.demoted
            }
            .take(BLEND_CAP)
            .map { it.toSong() }
            .toList()
        if (additions.isEmpty()) return queue
        // Spread through the queue rather than stacked on top of it.
        val out = queue.toMutableList()
        val stride = (queue.size / (additions.size + 1)).coerceAtLeast(1)
        additions.forEachIndexed { index, song ->
            out.add(minOf(stride * (index + 1) + index, out.size), song)
        }
        return out
    }

    /** Blend first, then rerank: the order the mood tap applies them. */
    fun personalize(queue: List<Song>, taste: Snapshot?): List<Song> =
        rerank(blend(queue, taste), taste)

    /** Mood playback's exact negative contract, including sparse profiles.
     * Positive blending/ranking keeps its existing minimum-size safeguards.
     * An explicit dislike never comes back merely to fill a small station.
     * Skips and artist patterns only demote, never exclude.
     */
    fun personalizeMood(queue: List<Song>, taste: Snapshot?): List<Song> {
        if (taste == null) return queue
        val sets = Sets(taste)
        val kept = personalize(queue, taste).filter { it.videoId !in sets.disliked }
        return if (taste.isEmpty) demote(kept, taste, sets) else kept
    }

    // ---- Storage ---------------------------------------------------------------

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: Snapshot): String = json.encodeToString(Snapshot.serializer(), snapshot)

    fun decode(text: String): Snapshot? = runCatching {
        json.decodeFromString(Snapshot.serializer(), text)
    }.getOrNull()
}
