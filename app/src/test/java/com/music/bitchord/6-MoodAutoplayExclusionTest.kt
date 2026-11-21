package com.music.bitchord

import com.music.bitchord.data.LikeState
import com.music.bitchord.data.Taste
import com.music.bitchord.data.TopPicks
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.*
import com.music.bitchord.playback.QueueBuilder
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MoodAutoplayExclusionTest {
    private val seed = Song("seed", "Seed", "Artist", null,
        playbackSourceType = PlaybackSourceType.HOME, playbackSourceId = TopPickMood.ENERGY.name)
    private val profile = Taste.Snapshot(dislikedIds = listOf("blocked"))
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
            TopPicks.updateTaste(Taste.Snapshot(dislikedIds = listOf("current")))
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
}
