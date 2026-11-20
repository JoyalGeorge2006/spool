package com.music.bitchord.data

import com.music.bitchord.data.model.*
import kotlinx.coroutines.*
import kotlin.test.*

class StationArtworkTest {
    private fun song(id: String, art: String?) = Song(id, "Song $id", "Artist", art)
    private fun pick(kind: TopPickKind = TopPickKind.PERSONAL_STATION) = TopPick(kind, watch = WatchTarget("exact-target", "seed"))
    @Test fun previewUsesOnlyThisSnapshotAndTapDoesNotRefetch() = runBlocking {
        val queue = listOf(song("first", null), song("second", "https://art/640.jpg"), song("third", "https://art/other.jpg"))
        var calls = 0
        val initial = pick(TopPickKind.DISCOVERY_STATION).copy(exclude = listOf("heard"))
        val prepared = prepareStationArtwork(initial) { calls++; Result.success(queue) }
        assertEquals(listOf("https://art/640.jpg", "https://art/other.jpg"), prepared.images)
        assertEquals(queue, TopPicks.queue(prepared).getOrThrow())
        assertEquals(initial.watch, prepared.watch)
        assertEquals(initial.exclude, prepared.exclude)
        val again = prepareStationArtwork(prepared) { error("must not refetch") }
        assertSame(prepared, again)
        assertEquals(1, calls)
    }
    @Test fun failedPreparationClearsUnrelatedArtAndKeepsTarget() = runBlocking {
        val original = pick().copy(images = listOf("https://stale/art"))
        val result = prepareStationArtwork(original) { Result.failure(Exception("offline")) }
        assertTrue(result.images.isEmpty()); assertTrue(result.preparedSongs.isEmpty())
        assertEquals(original.watch, result.watch)
    }
    @Test fun noPhotoQueueStillRemainsPlayable() = runBlocking {
        val q = listOf(song("no-art", null))
        val p = prepareStationArtwork(pick()) { Result.success(q) }
        assertTrue(p.images.isEmpty()); assertEquals(q, TopPicks.queue(p).getOrThrow())
    }
    @Test fun refreshReplacesQueueAndCoverTogether() = runBlocking {
        val a = prepareStationArtwork(pick()) { Result.success(listOf(song("a", "https://art/a"))) }
        val b = prepareStationArtwork(pick()) { Result.success(listOf(song("b", "https://art/b"))) }
        assertEquals("a", a.preparedSongs.first().videoId); assertEquals("b", b.preparedSongs.first().videoId)
        assertEquals(listOf("https://art/b"), b.images)
    }
    @Test fun duplicateAndInvalidCoversAreBounded() = runBlocking {
        val q = listOf(song("0", "bad")) + (1..6).map { song("$it", "https://art/${it/2}") }
        val p = prepareStationArtwork(pick()) { Result.success(q) }
        assertEquals(3, p.images.size); assertEquals(p.images.distinct(), p.images)
        assertEquals(q, p.preparedSongs)
    }
    @Test fun cancellationIsNotConvertedIntoFallback() = runBlocking<Unit> {
        assertFailsWith<CancellationException> { prepareStationArtwork(pick()) { throw CancellationException("cancel") } }
    }
    @Test fun nonStationPhotosAndBehaviorAreUntouched() = runBlocking {
        val p = pick(TopPickKind.FEATURED_ARTIST).copy(images = listOf("https://artist"))
        assertSame(p, prepareStationArtwork(p) { error("no extra requests") })
    }
    @Test fun persistedPreviewCannotLieAboutANewQueue() = runBlocking {
        val p = prepareStationArtwork(pick()) { Result.success(listOf(song("a", "https://art/a"))) }
        val text = TopPicks.encode(TopPicks.Stored(null, "slot", listOf(p), emptyMap()))
        assertFalse(text.contains("preparedSongs"))
        val restored = TopPicks.decode(text)!!.picks.single()
        assertTrue(restored.images.isEmpty()); assertTrue(restored.preparedSongs.isEmpty())
        assertEquals(p.watch, restored.watch)
    }
}
