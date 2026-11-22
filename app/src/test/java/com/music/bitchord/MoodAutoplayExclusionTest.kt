package com.music.bitchord

import com.music.bitchord.data.LikeState
import com.music.bitchord.data.Taste
import com.music.bitchord.data.TopPicks
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.*
import com.music.bitchord.playback.QueueBuilder
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MoodAutoplayExclusionTest {
    private val seed = Song("seed", "Seed", "Artist", null,
        playbackSourceType = PlaybackSourceType.HOME, playbackSourceId = TopPickMood.ENERGY.name)
    private val profile = Taste.Snapshot(artists = listOf("fixture artist"), dislikedIds = listOf("blocked"))
    private lateinit var oldRatings: Map<String, LikeStatus>
    private var oldTaste: Taste.Snapshot? = null
    private var oldGeneration: Long = -1

    @Before fun setup() {
        oldRatings = LikeState.overrides.value.toMap()
        oldTaste = TopPicks.currentTaste()
        oldGeneration = Innertube.listenerGeneration
        LikeState.clear()
        TopPicks.updateTaste(profile)
    }
    @After fun restore() {
        LikeState.clear()
        if (oldGeneration == Innertube.listenerGeneration) {
            oldRatings.forEach { (id, status) -> LikeState.set(id, status) }
            TopPicks.updateTaste(oldTaste, oldGeneration)
        } else TopPicks.updateTaste(null)
    }
    @Test fun allTwentyEightMoodProvenancesExcludeExactDislikedTrack() {
        for (mood in TopPickMood.entries) assertEquals(setOf("blocked"), TopPicks.moodAutoplayDislikedIds(seed.copy(playbackSourceId = mood.name), false))
    }
    @Test fun otherHomeAndExplicitRadioAndPartyAreUnchanged() {
        assertTrue(TopPicks.moodAutoplayDislikedIds(seed.copy(playbackSourceId = "supermix"), false).isEmpty())
        assertTrue(TopPicks.moodAutoplayDislikedIds(seed.copy(playbackSourceType = PlaybackSourceType.SHARED_LINK), false).isEmpty())
        assertTrue(TopPicks.moodAutoplayDislikedIds(seed, true).isEmpty())
        // Android Auto's explicit initial station keeps its source metadata;
        // only a subsequent automatic tail inherits the mood exclusion policy.
        assertEquals(setOf("blocked"), TopPicks.moodAutoplayDislikedIds(seed.copy(radioName = seed.title), false))
    }
    @Test fun liveRatingsOverrideCachedSnapshotAndAreReadAgain() {
        val captured = TopPicks.moodAutoplayDislikedIds(seed, false)
        LikeState.set("blocked", LikeStatus.LIKE)
        LikeState.set("new-blocked", LikeStatus.DISLIKE)
        assertEquals(setOf("blocked"), captured)
        assertEquals(setOf("new-blocked"), TopPicks.moodAutoplayDislikedIds(seed, false))
        LikeState.set("blocked", LikeStatus.INDIFFERENT)
        assertEquals(setOf("new-blocked"), TopPicks.moodAutoplayDislikedIds(seed, false))
    }
    @Test fun nullTasteUsesOnlyCurrentSessionOverridesAndResetClearsPriorProfile() {
        TopPicks.updateTaste(null)
        assertTrue(TopPicks.moodAutoplayDislikedIds(seed, false).isEmpty())
        LikeState.set("current-only", LikeStatus.DISLIKE)
        assertEquals(setOf("current-only"), TopPicks.moodAutoplayDislikedIds(seed, false))
        LikeState.clear(); TopPicks.updateTaste(null)
        assertTrue(TopPicks.moodAutoplayDislikedIds(seed, false).isEmpty())
    }
    @Test fun storedProfileRequiresMatchingListenerAndRejectsLegacyUnboundSnapshot() {
        val encoded = TopPicks.encodeTaste("listener-A", profile)
        assertEquals(profile, TopPicks.decodeTaste(encoded, "listener-A"))
        assertNull(TopPicks.decodeTaste(encoded, "listener-B"))
        assertNull(TopPicks.decodeTaste(Taste.encode(profile), "listener-A"))
        assertEquals(profile, TopPicks.decodeTaste(TopPicks.encodeTaste(null, profile), null))
        assertNull(TopPicks.decodeTaste(TopPicks.encodeTaste(null, profile), "listener-A"))
    }
    @Test fun listenerGenerationChangeCannotReuseOldProfileOrPublishOldBuild() {
        val generation = Innertube.listenerGeneration
        val channelField = Innertube::class.java.getDeclaredField("channelOverride").apply { isAccessible = true }
        val oldChannel = channelField.get(Innertube) as Innertube.ChannelSelection?
        try {
            Innertube.selectChannel("mood-unit-fixture", null)
            assertNotEquals(generation, Innertube.listenerGeneration)
            assertTrue(TopPicks.moodAutoplayDislikedIds(seed, false).isEmpty())
            TopPicks.updateTaste(profile, generation)
            assertTrue(TopPicks.moodAutoplayDislikedIds(seed, false).isEmpty())
            TopPicks.updateTaste(Taste.Snapshot(artists = listOf("current fixture artist"), dislikedIds = listOf("current")))
            assertEquals(setOf("current"), TopPicks.moodAutoplayDislikedIds(seed, false))
        } finally { Innertube.selectChannel(oldChannel?.pageId, oldChannel?.dataSyncId, oldChannel?.authUser) }
    }
    @Test fun filteringBeforeBatchCapNeverRestoresBlockedSongsInSmallOrEmptyBatch() {
        val blocked = Song("blocked", "Blocked", "Blocked artist", null)
        val allowed = Song("allowed", "Allowed", "Allowed artist", null)
        val ids = TopPicks.moodAutoplayDislikedIds(seed, false)
        assertEquals(listOf(allowed), QueueBuilder.extend(listOf(seed), listOf(blocked, allowed).filterNot { it.videoId in ids }, 1))
        assertTrue(QueueBuilder.extend(listOf(seed), listOf(blocked).filterNot { it.videoId in ids }, 10).isEmpty())
    }

    @Test fun sparseNegativeSnapshotSurvivesStoreRestoreWithoutInventingPositiveSteering() {
        val sparse = Taste.Snapshot(dislikedIds = listOf("sparse-blocked"), skippedIds = mapOf("skip" to 3))
        TopPicks.updateTaste(TopPicks.decodeTaste(TopPicks.encodeTaste("same-listener", sparse), "same-listener"))
        assertEquals(sparse, TopPicks.currentTaste())
        assertNull(TopPicks.tasteHints())
        assertEquals(setOf("sparse-blocked"), TopPicks.moodAutoplayDislikedIds(seed, false))
    }

    @Test fun profileRebuildKeepsKnownDislikesUntilLiveUndo() = kotlinx.coroutines.runBlocking<Unit> {
        TopPicks.updateTaste(Taste.Snapshot(dislikedIds = listOf("remembered")))
        assertEquals(listOf("remembered"), com.music.bitchord.data.stats.TasteProfile.current().dislikedIds)
        for (undo in listOf(LikeStatus.LIKE, LikeStatus.INDIFFERENT)) {
            LikeState.set("remembered", undo)
            assertTrue(com.music.bitchord.data.stats.TasteProfile.current().dislikedIds.isEmpty())
        }
    }

    @Test fun delayedProfileReadUsesLatestUndoAndNewDislikeAtReturnAndPersistence() = kotlinx.coroutines.runBlocking<Unit> {
        for (undo in listOf(LikeStatus.LIKE, LikeStatus.INDIFFERENT)) {
            LikeState.clear()
            TopPicks.updateTaste(Taste.Snapshot(dislikedIds = listOf("remembered")))
            val generation = Innertube.listenerGeneration
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            val result = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                com.music.bitchord.data.stats.TasteProfile.current(
                    readSummary = { null },
                    readSkips = { entered.complete(Unit); release.await(); emptyMap() },
                )
            }
            entered.await()
            LikeState.set("remembered", undo)
            LikeState.set("during-read", LikeStatus.DISLIKE)
            release.complete(Unit)
            val built = result.await()
            assertEquals(listOf("during-read"), built.dislikedIds)
            TopPicks.updateTaste(built, generation)
            // A second change while MainViewModel is dispatched to IO must be
            // reconciled immediately before encoding, not frozen at return.
            LikeState.set("during-read", undo)
            LikeState.set("before-write", LikeStatus.DISLIKE)
            val latest = TopPicks.reconcileTaste(built, generation)!!
            TopPicks.updateTaste(latest, generation)
            val decoded = TopPicks.decodeTaste(TopPicks.encodeTaste("fixture", latest), "fixture")!!
            assertEquals(listOf("before-write"), decoded.dislikedIds)
            assertEquals(setOf("before-write"), TopPicks.moodAutoplayDislikedIds(seed, false))
        }
    }

    @Test fun delayedProfileReadCannotReturnOrPersistAcrossListenerGeneration() = kotlinx.coroutines.runBlocking<Unit> {
        val channelField = Innertube::class.java.getDeclaredField("channelOverride").apply { isAccessible = true }
        val oldChannel = channelField.get(Innertube) as Innertube.ChannelSelection?
        val generation = Innertube.listenerGeneration
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val result = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            com.music.bitchord.data.stats.TasteProfile.current(
                readSummary = { null },
                readSkips = { entered.complete(Unit); release.await(); emptyMap() },
            )
        }
        try {
            entered.await()
            Innertube.selectChannel("delayed-profile-fixture", null)
            release.complete(Unit)
            try { result.await(); fail("Stale profile build returned") }
            catch (_: kotlinx.coroutines.CancellationException) { }
            assertNull(TopPicks.reconcileTaste(profile, generation))
            assertNull(TopPicks.currentTaste())
        } finally {
            release.complete(Unit)
            Innertube.selectChannel(oldChannel?.pageId, oldChannel?.dataSyncId, oldChannel?.authUser)
        }
    }

    @Test fun cancelledProfileSourceDoesNotContinueToSkipsOrPublication() = kotlinx.coroutines.runBlocking<Unit> {
        var skipsRead = false
        try {
            com.music.bitchord.data.stats.TasteProfile.current(
                readSummary = { throw kotlinx.coroutines.CancellationException("cancelled source") },
                readSkips = { skipsRead = true; emptyMap() },
            )
            fail("Cancellation was swallowed")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertFalse(skipsRead)
        assertEquals(profile, TopPicks.currentTaste())
    }

    @Test fun actualDiskPublicationKeepsLatestUndoAndPreservesFileOnStaleGeneration() = kotlinx.coroutines.runBlocking<Unit> {
        val directory = java.nio.file.Files.createTempDirectory("taste-publication-test").toFile()
        val file = java.io.File(directory, "taste.json")
        try {
            val generation = Innertube.listenerGeneration
            LikeState.set("blocked", LikeStatus.INDIFFERENT)
            LikeState.set("new-blocked", LikeStatus.DISLIKE)
            assertTrue(TopPicks.persistTaste(file, "fixture", profile, generation))
            val saved = file.readText()
            assertEquals(listOf("new-blocked"), TopPicks.decodeTaste(saved, "fixture")!!.dislikedIds)
            assertFalse(TopPicks.persistTaste(file, "fixture", Taste.Snapshot(), generation - 1))
            assertEquals(saved, file.readText())
            assertEquals(listOf("taste.json"), directory.listFiles()!!.map { it.name })
            val cancelled = async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                TopPicks.persistTaste(file, "fixture", Taste.Snapshot(), generation)
            }
            cancelled.cancel()
            try { cancelled.await(); fail("Cancelled save returned") }
            catch (_: kotlinx.coroutines.CancellationException) { }
            assertEquals(saved, file.readText())
        } finally { directory.deleteRecursively() }
    }
}
