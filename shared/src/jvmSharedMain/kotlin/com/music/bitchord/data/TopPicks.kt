package com.music.bitchord.data

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.TopPick
import com.music.bitchord.data.model.TopPickKind
import com.music.bitchord.data.model.TopPickMood
import com.music.bitchord.data.model.WatchTarget
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import com.music.bitchord.data.model.normalizedArtistName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.Locale
import kotlin.random.Random

/**
 * Builds Home's "Top Picks for You" row out of what the account already tells
 * YouTube Music, and starts the queue behind a card when it is tapped.
 *
 * Every card comes from YouTube's own data:
 *  - the listener's top artists are ranked from their YouTube listening
 *    history (the feed Recents was just built from, so normally no request),
 *    with this app's own play counts as a second opinion;
 *  - the three top artists' landing pages (one request each, in parallel)
 *    supply the latest release, the artist photos, related artists, and the
 *    header's Mix and Shuffle queues;
 *  - My Supermix, Discover Mix and New Release Mix come off Home's Mixed for
 *    you shelf when it is already loaded, or the Mixed for you page if not;
 *  - a mood is a fixed category id, so it costs nothing until tapped.
 *
 * A card with nothing behind it is left out rather than filled with a
 * placeholder. Queues are fetched only on tap.
 */
object TopPicks {

    private const val TAG = "BitChord"

    /** How many artists get a page fetched — and so a card. */
    private const val ARTISTS = 3

    /**
     * How far down the listener's chart those artists are drawn from. Every
     * build draws afresh, weighted toward the top, so the row changes each
     * time it is built without leaving the listener's own taste.
     */
    private const val ARTIST_POOL = 10

    /** Below this, a filtered station would be over before it got going; keep it unfiltered. */
    private const val MIN_FILTERED_QUEUE = 12

    /**
     * The listener's taste profile, handed over by the app when the Top Picks
     * row is built or the stored one is read back. Null until then — and a
     * mood queue built without it behaves exactly as it always has, steered
     * by region alone.
     */
    private data class BoundTaste(val generation: Long, val snapshot: Taste.Snapshot?)
    @Volatile
    private var boundTaste: BoundTaste? = null

    /** Every reader, including mood steering, checks the immutable binding. */
    fun currentTaste(): Taste.Snapshot? {
        val bound = boundTaste ?: return null
        return bound.snapshot.takeIf { bound.generation == com.music.bitchord.data.innertube.Innertube.listenerGeneration }
    }
    // Preserve the existing positive-profile steering defaults. Exact negatives
    // remain available to mood playback through the raw bound snapshot.
    private val taste: Taste.Snapshot? get() = currentTaste()?.takeUnless { it.isEmpty }

    fun updateTaste(snapshot: Taste.Snapshot?) = updateTaste(snapshot, com.music.bitchord.data.innertube.Innertube.listenerGeneration)

    fun updateTaste(snapshot: Taste.Snapshot?, generation: Long) {
        com.music.bitchord.data.innertube.Innertube.ifCurrentListener(generation) {
            boundTaste = BoundTaste(generation, snapshot)
        }
    }

    /** Exact track exclusions for local AutoPlay carrying a mood's provenance.
     * Session ratings override the cached profile, including an undone dislike.
     * Parties keep their shared policy; other Home stations and explicit radio
     * without inherited mood provenance are unchanged. No small-batch fallback.
     */
    fun moodAutoplayDislikedIds(seed: Song, inParty: Boolean): Set<String> {
        if (inParty || seed.playbackSourceType != com.music.bitchord.data.model.PlaybackSourceType.HOME ||
            TopPickMood.entries.none { it.name == seed.playbackSourceId }) return emptySet()
        val bound = boundTaste
        if (bound != null && bound.generation != com.music.bitchord.data.innertube.Innertube.listenerGeneration) return emptySet()
        return moodDislikedIds(bound?.snapshot)
    }

    /** Carry known exact negatives into a same-listener profile rebuild. */
    fun knownMoodDislikedIds(): Set<String> {
        val bound = boundTaste
        if (bound != null && bound.generation != com.music.bitchord.data.innertube.Innertube.listenerGeneration) return emptySet()
        return moodDislikedIds(bound?.snapshot)
    }

    /** Re-read exact negatives at the final same-listener publication boundary. */
    fun reconcileTaste(snapshot: Taste.Snapshot, generation: Long): Taste.Snapshot? {
        var result: Taste.Snapshot? = null
        com.music.bitchord.data.innertube.Innertube.ifCurrentListener(generation) {
            val known = currentTaste()?.dislikedIds.orEmpty()
            result = snapshot.copy(dislikedIds = moodDislikedIds(
                snapshot.copy(dislikedIds = (snapshot.dislikedIds + known).distinct()),
            ).toList())
        }
        return result
    }

    /** Same-listener taste publication, not an arbitrary identity-lock callback. */
    fun publishTaste(snapshot: Taste.Snapshot, generation: Long): Taste.Snapshot? {
        var published: Taste.Snapshot? = null
        com.music.bitchord.data.innertube.Innertube.ifCurrentListener(generation) {
            reconcileTaste(snapshot, generation)?.let { latest ->
                boundTaste = BoundTaste(generation, latest)
                published = latest
            }
        }
        return published
    }

    /** Prepare off-lock; only the final same-directory atomic rename is fenced.
     * Ratings changed during preparation retry. LikeState is not serialized by
     * the identity lock: a rating concurrent with final rename remains a live
     * override and is captured on the next save, not promised durable here.
     * Existing destination is never deleted when preparation/publication fails.
     */
    suspend fun persistTaste(
        destination: java.io.File, listener: String?, snapshot: Taste.Snapshot, generation: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        repeat(3) {
            currentCoroutineContext().ensureActive()
            val ratings = LikeState.overrides.value
            val baseline = currentTaste()
            val latest = reconcileTaste(snapshot, generation) ?: return@withContext false
            val temporary = java.io.File.createTempFile("taste-prepared-", ".json", destination.parentFile)
            try {
                temporary.writeText(encodeTaste(listener, latest))
                currentCoroutineContext().ensureActive()
                var installed = false
                com.music.bitchord.data.innertube.Innertube.ifCurrentListener(generation) {
                    if (ratings == LikeState.overrides.value && baseline == currentTaste()) {
                        java.nio.file.Files.move(temporary.toPath(), destination.toPath(),
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        boundTaste = BoundTaste(generation, latest)
                        installed = true
                    }
                }
                if (installed) return@withContext true
                if (generation != com.music.bitchord.data.innertube.Innertube.listenerGeneration)
                    return@withContext false
            } finally { temporary.delete() }
        }
        false
    }

    private fun moodDislikedIds(snapshot: Taste.Snapshot?): Set<String> {
        val excluded = snapshot?.dislikedIds.orEmpty().toMutableSet()
        LikeState.overrides.value.forEach { (id, rating) ->
            if (rating == com.music.bitchord.data.model.LikeStatus.DISLIKE) excluded += id
            else excluded -= id
        }
        return excluded
    }

    /**
     * The steering words a mood station built right now should carry — the
     * profile's scenes and genres when one is learned, null otherwise, so
     * the caller can fall back to [regionHints].
     */
    fun tasteHints(): List<String>? = taste?.hintWords()

    /**
     * The station behind the For you chip when the row has no Personal
     * Station card to offer — a radio off the track the listener's own
     * record says the most about, which is the same idea the card's fallback
     * builds. Null only when there is nothing at all to seed from.
     */
    suspend fun forYouStation(): TopPick? {
        val seed = taste?.topSongs?.firstOrNull()?.let { song ->
            WatchTarget(playlistId = "RDAMVM${song.videoId}", videoId = song.videoId)
        } ?: YtMusicRepository.recentHistory().getOrNull().orEmpty().firstOrNull()?.let { song ->
            WatchTarget(playlistId = "RDAMVM${song.videoId}", videoId = song.videoId)
        } ?: return null
        return TopPick(kind = TopPickKind.PERSONAL_STATION, watch = seed)
    }

    /**
     * What this app knows about the listener's taste without asking YouTube:
     * its own Replay chart. Second to the account's history when signed in,
     * and all there is when signed out.
     */
    data class LocalTaste(
        val artists: List<ArtistRef> = emptyList(),
        val songs: List<Song> = emptyList(),
        val genres: List<String> = emptyList(),
    )

    suspend fun build(
        homeShelves: List<HomeShelf>,
        local: LocalTaste,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<TopPick> = coroutineScope {
        val historyAsync = async { YtMusicRepository.recentHistory().getOrNull().orEmpty() }
        val mixesAsync = async { findMixes(homeShelves) }

        val history = historyAsync.await()
        val ranked = weightedSample(rankArtists(history, local.artists).take(ARTIST_POOL), ARTISTS)
        val pages = ranked.map { artist ->
            async { YtMusicRepository.artistLanding(artist.browseId!!).getOrNull() }
        }.awaitAll()
        val artists = ranked.zip(pages).mapNotNull { (ref, page) ->
            page?.let { ArtistCard(it.copy(name = it.name ?: ref.name), ref.browseId!!) }
        }
            // One person can be two channels (an artist's own and a "Topic"
            // one, or a spelling of their name); two of them would be the
            // same face on two cards.
            .distinctBy { normalizedArtistName(it.name) }
        // Every artist face on the row is Apple Music's photo of them, not
        // YouTube's channel art: the artists themselves, and the people their
        // fans also play. Looked up together, a few at a time.
        val photos = applePhotos(artists)
        val mixes = mixesAsync.await()
        Log.d(TAG, "topPicks: history=${history.size} artists=${artists.map { it.name }} mixes=${mixes.keys}")

        // The mood cards: the hour's own first, then the rest of the card
        // set — every one a real Find Your Mood station. Each asks its
        // category page for one real playlist cover; without one the card
        // paints the mood's own drawn art instead.
        val moods = (listOf(moodFor(now)) + MOOD_CARD_SET).distinct()
        val moodArt = moods.map { mood ->
            async {
                mood to withTimeoutOrNull(MOOD_ART_TIMEOUT_MS) {
                    YtMusicRepository.moodGenreArtwork(TopPickMood.CATEGORY_BROWSE_ID, mood.params).getOrNull()
                }
            }
        }.awaitAll().toMap()

        // The first card is always the new release when there is one, as on
        // Apple Music; the rest come in a different order every build.
        val lead = latestRelease(artists)
        val rest = buildList {
            personalStation(mixes, history, local)?.let(::add)
            moods.forEach { mood ->
                val base = if (mood == moodFor(now)) moodPick(now, local) else moodStation(mood)
                add(base.copy(images = listOfNotNull(moodArt[mood])))
            }
            artists.getOrNull(0)?.let { andSimilar(it, photos) }?.let(::add)
            artists.getOrNull(1)?.let { featured(it, photos) }?.let(::add)
            mixes[Mix.NEW_RELEASE]?.let { mix ->
                add(
                    TopPick(
                        kind = TopPickKind.NEW_RELEASE_MIX,
                        title = mix.title,
                        subtitle = mix.subtitle,
                        images = listOfNotNull(mix.thumbnailUrl),
                        watch = mix.watchTarget(),
                    ),
                )
            }
            artists.getOrNull(2)?.let { andSimilar(it, photos) }?.let(::add)
            discoveryStation(mixes, artists, history)?.let(::add)
        }.shuffled()
        prepareStations(listOfNotNull(lead) + rest)
    }

    /** At most the two personal/discovery metadata queues, in parallel, bounded per request. */
    suspend fun prepareStations(picks: List<TopPick>): List<TopPick> = coroutineScope {
        picks.map { pick -> async {
            if (!pick.isPreparedStation || pick.preparedSongs.isNotEmpty()) pick else {
                withTimeoutOrNull(12_000L) { prepareStationArtwork(pick) { queue(it) } }
                    ?: pick.copy(images = emptyList())
            }
        } }.awaitAll()
    }

    /**
     * [count] of [ranked] at random, the higher an artist sits the likelier —
     * the top of the chart turns up most builds, the rest often enough to
     * keep the row moving.
     */
    private fun <T> weightedSample(ranked: List<T>, count: Int): List<T> {
        val pool = ranked.withIndex().toMutableList()
        val picked = mutableListOf<T>()
        while (picked.size < count && pool.isNotEmpty()) {
            val weights = pool.map { (ranked.size - it.index).toDouble() }
            var roll = Random.nextDouble() * weights.sum()
            val index = weights.indexOfFirst { roll -= it; roll <= 0 }.coerceAtLeast(0)
            picked += pool.removeAt(index).value
        }
        return picked
    }

    /** The queue a card starts. Never called for [TopPickKind.LATEST_RELEASE], which opens a page. */
    suspend fun queue(pick: TopPick): Result<List<Song>> {
        if (pick.isPreparedStation && pick.preparedSongs.isNotEmpty()) {
            return Result.success(pick.preparedSongs)
        }
        if (pick.kind == TopPickKind.MOOD) return moodQueue(pick)
        val target = pick.watch ?: return Result.failure(IllegalStateException("no queue behind ${pick.kind}"))
        if (pick.kind == TopPickKind.PERSONAL_STATION || pick.kind == TopPickKind.DISCOVERY_STATION) {
            return freshStation(pick, target)
        }
        return YtMusicRepository.watchQueue(target)
    }

    /**
     * A Made for You station that sounds different on every tap.
     *
     * My Supermix and Discover Mix are rebuilt by YouTube about once a day
     * and come back in the same order every time they're asked for, so
     * played as they are, the station opened on the same songs every time.
     * Here the mix is the core — a random half of it — blended with the
     * radios of a few tracks drawn at random from the listener's history, and
     * the whole thing shuffled.
     *
     * A Discovery Station leaves out everything already in that history (and
     * the seed artist, when it's built off an artist's Mix rather than
     * Discover Mix), so what it adds stays new to the listener.
     */
    private suspend fun freshStation(pick: TopPick, target: WatchTarget): Result<List<Song>> = coroutineScope {
        val mixAsync = async { YtMusicRepository.watchQueue(target) }
        val history = YtMusicRepository.recentHistory().getOrNull().orEmpty()
        val seeds = history.take(SEED_POOL).shuffled().take(SEED_RADIOS)
        val radios = seeds.map { seed ->
            async {
                YtMusicRepository.watchQueue(WatchTarget(playlistId = "RDAMVM${seed.videoId}", videoId = seed.videoId))
                    .getOrNull().orEmpty()
            }
        }
        val mix = mixAsync.await().getOrElse { failure ->
            if (seeds.isEmpty()) return@coroutineScope Result.failure(failure)
            emptyList()
        }
        val around = radios.awaitAll().flatten()

        val discovery = pick.kind == TopPickKind.DISCOVERY_STATION
        val heard = if (discovery) history.mapTo(HashSet()) { it.videoId } + pick.exclude else emptySet()
        val seedArtist = pick.artistName?.takeIf { discovery }?.lowercase(Locale.ROOT)
        fun keep(song: Song): Boolean =
            song.videoId !in heard &&
                (seedArtist == null || !song.artist.lowercase(Locale.ROOT).contains(seedArtist))
        val core = mix.filter(::keep).shuffled().take(STATION_FROM_MIX)
        val coreIds = core.mapTo(HashSet()) { it.videoId }
        val extra = around.filter { keep(it) && it.videoId !in coreIds }
            .distinctBy { it.videoId }
            .shuffled()
            .take(STATION_SIZE - core.size)
        val station = (core + extra).shuffled()
        Log.d(TAG, "station ${pick.kind}: mix=${mix.size} core=${core.size} radios=${seeds.size} extra=${extra.size}")
        when {
            station.size >= MIN_FILTERED_QUEUE -> Result.success(station)
            mix.isNotEmpty() -> Result.success(mix.shuffled())
            else -> Result.failure(IllegalStateException("${pick.kind} came back empty"))
        }
    }

    /** History tracks a station's extra radios are seeded from: the recent end of it. */
    private const val SEED_POOL = 40
    private const val SEED_RADIOS = 3
    private const val STATION_FROM_MIX = 30
    private const val STATION_SIZE = 60

    // ---- Cards --------------------------------------------------------------

    private class ArtistCard(val page: ArtistPage, val browseId: String) {
        val name: String get() = page.name.orEmpty()

        /**
         * The page's "Fans might also like", by name, without the artist
         * themselves — YouTube can list a second channel of the same person
         * there — and without repeats. A few more than a card shows, so an
         * artist Apple has no photo of can be passed over.
         */
        val relatedNames: List<String> get() = related.keys.toList()

        /** [relatedNames] → each one's channel, which their Apple photo is checked against. */
        val related: Map<String, String> by lazy {
            val own = normalizedArtistName(name)
            page.sections
                .firstOrNull { shelf -> shelf.items.isNotEmpty() && shelf.items.all { it.browseId?.startsWith("UC") == true } }
                ?.items.orEmpty()
                .filter { it.browseId != browseId }
                .filter { normalizedArtistName(it.title).let { key -> key.isNotEmpty() && key != own } }
                .distinctBy { normalizedArtistName(it.title) }
                .take(RELATED_CANDIDATES)
                .associate { it.title to it.browseId!! }
        }
    }

    private const val RELATED_CANDIDATES = 4
    private const val PHOTO_LOOKUPS_AT_ONCE = 4

    /**
     * Apple Music photos for [artists] and the people their fans also play, by
     * name; an artist Apple has none of (or none provably theirs) is left out.
     * A card's own artist is checked against the page already in hand; a
     * related one's page is fetched only if their photo isn't known yet.
     */
    private suspend fun applePhotos(artists: List<ArtistCard>): Map<String, String> = coroutineScope {
        val catalogues = LinkedHashMap<String, suspend () -> Collection<String>>()
        artists.forEach { artist -> catalogues.putIfAbsent(artist.name) { AppleCatalogue.titlesOf(artist.page) } }
        artists.forEach { artist ->
            artist.related.forEach { (name, channel) ->
                catalogues.putIfAbsent(name) {
                    YtMusicRepository.artistLanding(channel).getOrNull()?.let(AppleCatalogue::titlesOf).orEmpty()
                }
            }
        }
        val limiter = Semaphore(PHOTO_LOOKUPS_AT_ONCE)
        catalogues.map { (name, catalogue) ->
            async { limiter.withPermit { AppleArtistPhotos.photoFor(name, catalogue)?.let { name to it } } }
        }.awaitAll().filterNotNull().toMap()
    }

    private fun latestRelease(artists: List<ArtistCard>): TopPick? {
        data class Candidate(val rank: Int, val artist: ArtistCard, val item: ShelfItem, val year: Int, val single: Boolean)
        val candidates = artists.flatMapIndexed { rank, artist ->
            // The release shelves are the ones of albums (MPRE… pages). Each is
            // newest first, so only its lead card is a candidate.
            artist.page.sections
                .mapNotNull { shelf -> shelf.items.firstOrNull()?.takeIf { it.browseId?.startsWith("MPRE") == true } }
                .map { item ->
                    Candidate(rank, artist, item, yearOf(item.subtitle), SINGLE.containsMatchIn(item.subtitle))
                }
        }
        // Newest year first. Within a year a single wins: a bare-year "album"
        // on a big artist's page is as often one of YouTube's auto-made
        // compilations as a real record, and a single never is.
        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.year }.thenBy { it.single }.thenByDescending { it.rank },
        ) ?: return null
        return TopPick(
            kind = TopPickKind.LATEST_RELEASE,
            title = best.item.title,
            subtitle = best.item.subtitle,
            artistName = best.artist.name,
            images = listOfNotNull(best.item.thumbnailUrl),
            browseId = best.item.browseId,
        )
    }

    private fun personalStation(mixes: Map<Mix, ShelfItem>, history: List<Song>, local: LocalTaste): TopPick? {
        val watch = mixes[Mix.SUPERMIX]?.watchTarget()
            // No Supermix (signed out, or a feed without one): a radio off the
            // track the listener plays most, which is the same idea built by hand.
            ?: (local.songs.firstOrNull() ?: history.firstOrNull())?.let { song ->
                WatchTarget(playlistId = "RDAMVM${song.videoId}", videoId = song.videoId)
            }
            ?: return null
        return TopPick(kind = TopPickKind.PERSONAL_STATION, watch = watch)
    }

    private fun moodPick(now: LocalDateTime, local: LocalTaste): TopPick =
        TopPick(
            kind = TopPickKind.MOOD,
            mood = moodFor(now),
            hints = moodHints(local),
        )

    /**
     * What steers a mood shelf toward the listener: their own profile's
     * scenes and genres when one has been learned, the month's Replay genres
     * and the phone's region when it hasn't.
     */
    private fun moodHints(local: LocalTaste): List<String> =
        taste?.hintWords()
            ?: (local.genres.map { it.lowercase(Locale.ROOT) } + regionHints()).distinct()

    private fun andSimilar(artist: ArtistCard, photos: Map<String, String>): TopPick? {
        val radio = artist.page.radio ?: return null
        // No Apple photo of the artist, no card: the big circle is the card.
        val face = photos[artist.name] ?: return null
        val related = artist.relatedNames
            .mapNotNull { photos[it] }
            // Two names can share one photo; one face per circle.
            .filter { it != face }
            .distinct()
            .take(2)
        return TopPick(
            kind = TopPickKind.ARTIST_AND_SIMILAR,
            title = artist.name,
            artistName = artist.name,
            artistBrowseId = artist.browseId,
            images = listOf(face) + related,
            watch = radio,
        )
    }

    private fun featured(artist: ArtistCard, photos: Map<String, String>): TopPick? {
        val shuffle = artist.page.shuffle ?: artist.page.radio ?: return null
        val face = photos[artist.name] ?: return null
        return TopPick(
            kind = TopPickKind.FEATURED_ARTIST,
            title = artist.name,
            subtitle = artist.page.monthlyListenerCount ?: artist.page.subscriberCountText.orEmpty(),
            artistName = artist.name,
            artistBrowseId = artist.browseId,
            images = listOf(face),
            watch = shuffle,
        )
    }

    private fun discoveryStation(
        mixes: Map<Mix, ShelfItem>,
        artists: List<ArtistCard>,
        history: List<Song>,
    ): TopPick? {
        mixes[Mix.DISCOVER]?.watchTarget()?.let { watch ->
            return TopPick(kind = TopPickKind.DISCOVERY_STATION, watch = watch)
        }
        // No Discover Mix: the top artist's Mix with everything already heard
        // filtered out at play time — see [queue].
        val seed = artists.firstOrNull() ?: return null
        val radio = seed.page.radio ?: return null
        return TopPick(
            kind = TopPickKind.DISCOVERY_STATION,
            artistName = seed.name,
            watch = radio,
            exclude = history.take(200).map { it.videoId },
        )
    }

    // ---- Artists ------------------------------------------------------------

    /**
     * The listener's artists, best first. Each history row counts once, with
     * a little extra the more recent it is; the app's own chart adds a smaller
     * share by position. Only artists with a channel id take part — a card
     * needs the page, and a bare name would need a search to find one.
     */
    private fun rankArtists(history: List<Song>, local: List<ArtistRef>): List<ArtistRef> {
        val scores = LinkedHashMap<String, Pair<String, Double>>()
        fun credit(id: String?, name: String, weight: Double) {
            if (id == null || !id.startsWith("UC") || name.isBlank()) return
            if (name.equals("Various Artists", ignoreCase = true)) return
            val (_, score) = scores[id] ?: (name to 0.0)
            scores[id] = name to score + weight
        }
        history.forEachIndexed { index, song ->
            val recency = 1.0 - index.toDouble() / history.size.coerceAtLeast(1)
            val lead = song.artists.firstOrNull { it.browseId != null }
            if (lead != null) credit(lead.browseId, lead.name, 1.0 + recency * 0.5)
            else credit(song.artistId, song.artist, 1.0 + recency * 0.5)
        }
        local.forEachIndexed { index, artist ->
            credit(artist.browseId, artist.name, (local.size - index).toDouble() * 0.5)
        }
        return scores.entries
            .sortedByDescending { it.value.second }
            .map { ArtistRef(name = it.value.first, browseId = it.key) }
    }

    // ---- Mixes --------------------------------------------------------------

    private enum class Mix { SUPERMIX, DISCOVER, NEW_RELEASE }

    /**
     * The account's mixes. All of them are `RDTMAK…` playlists, so the id says
     * "a mix" but not which; the title does, in English. In another interface
     * language only the Supermix is placed, as the lead mix, which is where
     * YouTube puts it.
     */
    private suspend fun findMixes(homeShelves: List<HomeShelf>): Map<Mix, ShelfItem> {
        val onHome = homeShelves.flatMap { it.items }.filter { it.isMix() }
        val mixes = onHome.takeIf { it.isNotEmpty() }
            ?: YtMusicRepository.mixedForYou().getOrNull().orEmpty().filter { it.isMix() }
        if (mixes.isEmpty()) return emptyMap()
        val found = mutableMapOf<Mix, ShelfItem>()
        mixes.forEach { item ->
            val title = item.title.lowercase(Locale.ROOT)
            val kind = when {
                "supermix" in title -> Mix.SUPERMIX
                "discover" in title -> Mix.DISCOVER
                "new release" in title -> Mix.NEW_RELEASE
                else -> null
            }
            if (kind != null) found.putIfAbsent(kind, item)
        }
        if (found.isEmpty()) found[Mix.SUPERMIX] = mixes.first()
        return found
    }

    private fun ShelfItem.isMix(): Boolean =
        browseId?.removePrefix("VL")?.startsWith("RDTMAK") == true

    private fun ShelfItem.watchTarget(): WatchTarget? =
        browseId?.removePrefix("VL")?.let { WatchTarget(playlistId = it) }

    // ---- Moods --------------------------------------------------------------

    /**
     * A mood at random, leaning on the hour: most of the time one that suits
     * it (Focus mid-morning, Love late in the evening), sometimes any of
     * them. Only Apple's seven Find Your Mood moods, as on Apple Music's own
     * Top Picks — the activities live in Find Your Mood's Show all.
     */
    fun moodFor(now: LocalDateTime): TopPickMood {
        val suited = when (now.hour) {
            in 5..8 -> listOf(TopPickMood.ENERGY, TopPickMood.FEEL_GOOD, TopPickMood.FOCUS)
            in 9..11 -> listOf(TopPickMood.FOCUS, TopPickMood.FEEL_GOOD, TopPickMood.ENERGY)
            in 12..16 -> listOf(TopPickMood.FEEL_GOOD, TopPickMood.FOCUS, TopPickMood.ENERGY, TopPickMood.RELAX)
            in 17..19 -> listOf(TopPickMood.ENERGY, TopPickMood.FEEL_GOOD, TopPickMood.RELAX)
            in 20..22 -> listOf(TopPickMood.LOVE, TopPickMood.RELAX, TopPickMood.HEARTBREAK, TopPickMood.FEEL_GOOD)
            else -> listOf(TopPickMood.RELAX, TopPickMood.FEELING_BLUE, TopPickMood.HEARTBREAK, TopPickMood.LOVE)
        }
        return if (Random.nextFloat() < 0.7f) suited.random()
        else TopPickMood.entries.filter { it.isCoreMood }.random()
    }

    /**
     * A mood's queue: the mood's own playlists, played as they are and
     * shuffled together. Not a playlist *radio* — a radio wanders off to
     * whatever sounds like the seed, and a Love station drifted into plain
     * feel-good pop within a few tracks.
     *
     * The category is shelves of playlists by scene ("Romantic Bollywood &
     * Indian", "Pop romance", …). A shelf naming the listener's own scene
     * wins; two of its playlists are drawn at random and mixed.
     */
    private suspend fun moodQueue(pick: TopPick): Result<List<Song>> = coroutineScope {
        val generation = com.music.bitchord.data.innertube.Innertube.listenerGeneration
        val mood = pick.mood ?: return@coroutineScope Result.failure(IllegalStateException("mood card without a mood"))
        // The listener's country and languages: shelves are asked for in their
        // country, and a shelf or playlist that names one of their languages
        // outranks one that does not.
        val region = MusicRegion.country
        val langs = MusicRegion.steeringLanguages()
        val shelves = YtMusicRepository.moodGenreShelves(TopPickMood.CATEGORY_BROWSE_ID, mood.params, region)
            .getOrElse { return@coroutineScope Result.failure(it) }
        val playlists = { shelf: HomeShelf -> shelf.items.filter { it.browseId?.startsWith("VL") == true } }
        val candidates = shelves.filter { playlists(it).isNotEmpty() }
        val score = { shelf: HomeShelf ->
            Taste.shelfScore(shelf.title, mood, pick.hints) + MusicRegion.shelfBonus(shelf.title, mood, langs)
        }
        val best = candidates.maxOfOrNull(score)
        val bestShelves = best?.let { b -> candidates.filter { score(it) == b } }.orEmpty()
        // A language shelf exists when the best one names a chosen language
        // (or, with no language to serve, whatever scored best will do).
        val languageShelf = if (langs.isEmpty()) bestShelves.firstOrNull()
        else bestShelves.firstOrNull { MusicRegion.languageMatches(it.title, langs) > 0 }
        val searched = if (languageShelf == null && langs.isNotEmpty()) languageSearchQueue(mood, langs) else emptyList()
        val fetched = if (searched.isNotEmpty()) searched else {
            val shelf = (languageShelf ?: bestShelves.randomOrNull())
                ?: return@coroutineScope Result.failure(IllegalStateException("no playlists for $mood"))
            // Playlists that name the listener's language first, the rest after.
            val ordered = playlists(shelf).take(8).shuffled()
                .sortedByDescending { MusicRegion.languageMatches(it.title, langs) }
            val chosen = ordered.take(MOOD_PLAYLISTS)
            chosen.map { item ->
                async {
                    val playlistId = item.browseId!!.removePrefix("VL")
                    // As a watch queue, so YouTube hands over each official video's
                    // song version directly; the playlist page only lists videos.
                    YtMusicRepository.playlistAsAudio(playlistId).getOrNull()?.takeIf { it.isNotEmpty() }
                        ?: YtMusicRepository.browseSongs(item.browseId!!).getOrNull()?.songs.orEmpty()
                }
            }.awaitAll().flatten()
                // A row YouTube couldn't pair stays the video, and plays as one;
                // the player menu's version switch turns it into the song.
                .distinctBy { it.videoId }
                .shuffled()
                .take(MOOD_QUEUE)
        }
        if (fetched.isEmpty()) return@coroutineScope Result.failure(IllegalStateException("$mood playlists were empty"))
        // The mood picked; now make it theirs. Blend in a few of their own
        // chart toppers the mood already carries, open on their artists and
        // likes, and sink what they keep skipping — all of it fail-closed
        // back to the queue as fetched.
        if (generation != com.music.bitchord.data.innertube.Innertube.listenerGeneration)
            return@coroutineScope Result.failure(IllegalStateException("Listener changed while mood loaded"))
        val snapshot = currentTaste()
        val effective = (snapshot ?: Taste.Snapshot()).copy(dislikedIds = moodDislikedIds(snapshot).toList())
        val personalized = Taste.personalizeMood(fetched, effective)
        if (personalized.isEmpty()) Result.failure(IllegalStateException("$mood has no permitted tracks"))
        else Result.success(personalized)
    }

    /**
     * Songs for a mood in the listener's languages when YouTube's mood shelves
     * name none of them: a search per language ("Malayalam relaxing songs"),
     * the languages' results mixed. Empty when too little comes back to be
     * worth playing, so the shelves stay the answer.
     */
    private suspend fun languageSearchQueue(
        mood: TopPickMood,
        langs: List<MusicRegion.Language>,
    ): List<Song> = coroutineScope {
        val songs = langs.take(3).map { lang ->
            async {
                YtMusicRepository.search(MusicRegion.searchQuery(lang, mood), com.music.bitchord.data.model.SearchFilter.SONGS)
                    .getOrNull().orEmpty()
                    .mapNotNull { row ->
                        when (row) {
                            is com.music.bitchord.data.model.SearchResult.Track -> row.song
                            is com.music.bitchord.data.model.SearchResult.TopTrack -> row.song
                            else -> null
                        }
                    }
                    .filterNot { it.isVideo }
                    .take(MOOD_QUEUE)
            }
        }.awaitAll().flatten().distinctBy { it.videoId }.shuffled().take(MOOD_QUEUE)
        if (songs.size >= MIN_SEARCHED_QUEUE) songs else emptyList()
    }

    private const val MIN_SEARCHED_QUEUE = 12
    private const val MOOD_PLAYLISTS = 3
    private const val MOOD_QUEUE = 60

    /** The Home mood cards offered after the hour's own mood. */
    private val MOOD_CARD_SET = listOf(
        TopPickMood.FOCUS,
        TopPickMood.CHILL,
        TopPickMood.PARTY,
        TopPickMood.SLEEP,
    )

    /** A mood card's cover lookup never holds the row back. */
    private const val MOOD_ART_TIMEOUT_MS = 4_000L

    /**
     * A mood station for a Find Your Mood tile — the same queue the Mood for
     * You card plays, steered toward the listener's scene by [hints].
     */
    fun moodStation(mood: TopPickMood, hints: List<String> = regionHints()): TopPick =
        TopPick(kind = TopPickKind.MOOD, mood = mood, hints = hints)

    fun regionHints(): List<String> = when (Locale.getDefault().country) {
        // The south-Indian scenes are here too: an Indian phone is as often a
        // Malayalam or Tamil listener as a Bollywood one. This is the fallback
        // for a listener with no profile yet, never a claim about their taste.
        "IN" -> listOf(
            "bollywood", "indian", "hindi", "punjabi", "desi",
            "malayalam", "tamil", "telugu", "kannada",
        )
        "PK" -> listOf("pakistani", "urdu", "punjabi")
        "KR" -> listOf("k-pop", "korean")
        "JP" -> listOf("j-pop", "japanese")
        "BR" -> listOf("brazil", "sertanejo", "funk")
        "MX", "ES", "AR", "CO", "CL", "PE" -> listOf("latin", "latino", "reggaeton")
        else -> emptyList()
    }

    private val YEAR = Regex("""\b(19|20)\d{2}\b""")
    private val SINGLE = Regex("""\b(Single|EP)\b""")

    private fun yearOf(subtitle: String): Int =
        YEAR.findAll(subtitle).lastOrNull()?.value?.toIntOrNull() ?: 0

    // ---- Storage ------------------------------------------------------------

    /** A day's row as stored on disk, so Home can paint it before any request. */
    @Serializable
    data class Stored(
        val listener: String?,
        /** The hour it was built in, e.g. `2026-10-21T15`. */
        val day: String,
        val picks: List<TopPick>,
        /** [AppleArtistPhotos]' answers, misses included, so the next build doesn't ask again. */
        val photos: Map<String, String> = emptyMap(),
    )

    /** A naked legacy snapshot cannot prove which listener it belongs to. */
    @Serializable
    data class StoredTaste(val listener: String?, val snapshot: Taste.Snapshot)

    fun encodeTaste(listener: String?, snapshot: Taste.Snapshot): String =
        json.encodeToString(StoredTaste.serializer(), StoredTaste(listener, snapshot))

    fun decodeTaste(text: String, listener: String?): Taste.Snapshot? = runCatching {
        json.decodeFromString(StoredTaste.serializer(), text)
            .takeIf { it.listener == listener }?.snapshot
    }.getOrNull()

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(stored: Stored): String = json.encodeToString(Stored.serializer(), stored)

    fun decode(text: String): Stored? = runCatching {
        val stored = json.decodeFromString(Stored.serializer(), text)
        // Transient queues cannot survive restart. Never display an old cover against a new shuffle.
        stored.copy(picks = stored.picks.map { if (it.isPreparedStation) it.copy(images = emptyList()) else it })
    }.getOrNull()
}
