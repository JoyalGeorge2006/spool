package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueUndoPositionTest {
    private val user = QueueTier.USER_QUEUE
    private val context = QueueTier.CONTEXT
    private val auto = QueueTier.AUTOPLAY
    private fun entry(id: String, tier: QueueTier = context) = QueueUndoPosition.Entry(id, tier)

    @Test fun followsLiveNextNeighborAfterReorder() {
        assertEquals(2, QueueUndoPosition.choose(listOf(entry("now"), entry("x"), entry("after")), 0, context, "gone", "after", "now"))
    }
    @Test fun followsLivePreviousNeighbor() {
        assertEquals(2, QueueUndoPosition.choose(listOf(entry("now"), entry("before")), 0, context, "before", null, "now"))
    }
    @Test fun neverInsertsBeforePlayhead() {
        assertNull(QueueUndoPosition.choose(listOf(entry("after"), entry("new-now")), 1, context, null, "after", "old-now"))
    }
    @Test fun replacementQueueWithoutAnchorsIsRefused() {
        assertNull(QueueUndoPosition.choose(listOf(entry("different")), 0, context, "before", "after", "old-now"))
    }
    @Test fun emptyQueueAllowsRestoreWithoutSeeking() {
        assertEquals(0, QueueUndoPosition.choose(emptyList(), -1, context, null, null, null))
    }
    @Test fun fallbackPreservesSectionOrder() {
        assertEquals(1, QueueUndoPosition.choose(listOf(entry("now"), entry("c"), entry("a", auto)), 0, user, null, null, "now"))
    }
    @Test fun sameIdInOtherSectionIsNotAnAnchor() {
        assertNull(QueueUndoPosition.choose(listOf(entry("now"), entry("after", auto)), 0, context, null, "after", "old-now"))
    }
    @Test fun duplicateAnchorIdsAreNotTrusted() {
        assertNull(QueueUndoPosition.choose(listOf(entry("now"), entry("after"), entry("after")), 0, context, null, "after", "old-now"))
    }
    @Test fun removingPlayingTrackRestoresAfterNewPlayheadWithoutRestart() {
        assertEquals(1, QueueUndoPosition.choose(listOf(entry("after"), entry("next")), 0, context, null, "after", "after", wasCurrent = true))
    }

    @Test fun currentAtTierBoundaryRestoresAfterValidatedNewPlayhead() {
        assertEquals(1, QueueUndoPosition.choose(listOf(entry("auto", auto), entry("next", auto)), 0, context, null, null, "auto", wasCurrent = true))
    }
    @Test fun removedLastCurrentWithHistoryDoesNotRestartIt() {
        assertEquals(1, QueueUndoPosition.choose(listOf(entry("history")), 0, context, "history", null, "history", wasCurrent = true))
    }
    @Test fun historyUndoRemainsBehindPlayhead() {
        assertEquals(0, QueueUndoPosition.choose(listOf(entry("now"), entry("next")), 0, context, null, "now", "now", wasHistory = true))
    }
    @Test fun currentUndoAfterAnotherTrackChangeIsRefused() {
        assertNull(QueueUndoPosition.choose(listOf(entry("later")), 0, context, null, null, "expected", wasCurrent = true))
    }
    @Test fun soleCurrentRestoreDoesNotSeekOrPlay() {
        assertEquals(0, QueueUndoPosition.choose(emptyList(), -1, context, null, null, null, wasCurrent = true))
    }

}
