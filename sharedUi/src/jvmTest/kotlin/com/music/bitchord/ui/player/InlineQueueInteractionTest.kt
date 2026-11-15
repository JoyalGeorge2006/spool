package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real [InlineQueue] in a bounded box: the pane the page layout gives it.
 *
 *  - 20 rows: scrolling to the last one brings it fully inside the pane.
 *  - Swipe past 40% of a row's width calls onRemove(timelineIndex) once.
 *  - Locked (a party that holds the controls): the same swipe removes nothing.
 *
 * Needs a [PlayerHost] installed, because the rows read the platform's artwork
 * URL and the queue reads the settings. [installQueueTestHost] is that and
 * nothing more; see its comment. Not covered: drag-reorder (needs a long press
 * on the handle and the list's auto-scroll, and onMove ordering), Undo (the
 * host's), and the gesture-bar inset (a window property: device only).
 */
@OptIn(ExperimentalTestApi::class)
class InlineQueueInteractionTest {
    private fun songs(count: Int) = List(count) { i ->
        Song(
            videoId = "v$i",
            title = if (i == 0) "Now" else "Track $i",
            artist = "Artist",
            thumbnailUrl = null,
            queueTier = QueueTier.CONTEXT,
            queueEntryId = "e$i",
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.mountQueue(
        queue: List<Song>,
        locked: Boolean,
        removed: MutableList<Int>,
    ) {
        installQueueTestHost()
        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    InlineQueue(
                        queue = queue,
                        currentIndex = 0,
                        autoplayEnabled = false,
                        controlsLocked = locked,
                        onJumpTo = {},
                        onRemove = { removed += it },
                        onMove = { _, _ -> },
                        onClear = {},
                        tabbed = true,
                    )
                }
            }
        }
    }

    @Test
    fun `the last of 20 rows scrolls fully into the pane`() = runComposeUiTest {
        val removed = mutableListOf<Int>()
        mountQueue(songs(21), locked = false, removed = removed)
        onNode(hasScrollAction()).performScrollToNode(hasText("Track 20"))
        onNodeWithText("Track 20").assertIsDisplayed()
        val root = onRoot().fetchSemanticsNode().boundsInRoot
        val row = onNodeWithText("Track 20").fetchSemanticsNode().boundsInRoot
        assertTrue(row.bottom <= root.bottom, "last row ends at ${row.bottom}, pane ends at ${root.bottom}")
        assertTrue(row.top >= root.top)
    }

    @Test
    fun `a long swipe removes the row once with its timeline index`() = runComposeUiTest {
        val removed = mutableListOf<Int>()
        mountQueue(songs(6), locked = false, removed = removed)
        onNodeWithText("Track 2").performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(listOf(2), removed)
    }

    @Test
    fun `a short swipe removes nothing`() = runComposeUiTest {
        val removed = mutableListOf<Int>()
        mountQueue(songs(6), locked = false, removed = removed)
        onNodeWithText("Track 2").performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - width * 0.1f)
        }
        waitForIdle()
        assertEquals(emptyList<Int>(), removed)
    }

    @Test
    fun `locked queue ignores the swipe`() = runComposeUiTest {
        val removed = mutableListOf<Int>()
        mountQueue(songs(6), locked = true, removed = removed)
        onNodeWithText("Track 2").performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(emptyList<Int>(), removed)
    }
}
