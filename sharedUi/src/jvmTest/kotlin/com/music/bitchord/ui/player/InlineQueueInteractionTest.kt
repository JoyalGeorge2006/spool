package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.CompositionLocalProvider
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
        autoplayEnabled: Boolean = false,
        outerInset: androidx.compose.ui.unit.Dp = 16.dp,
        dragStates: MutableList<Boolean>? = null,
        flat: Boolean = false,
    ) {
        installQueueTestHost()
        setContent {
            MaterialTheme { CompositionLocalProvider(LocalPlayerFlat provides flat) {
                Box(Modifier.width(320.dp).height(360.dp)) {
                    InlineQueue(
                        queue = queue,
                        currentIndex = 0,
                        autoplayEnabled = autoplayEnabled,
                        controlsLocked = locked,
                        onJumpTo = {},
                        onRemove = { removed += it },
                        onMove = { _, _ -> },
                        onClear = {},
                        onDragActiveChange = { dragStates?.add(it) },
                        tabbed = true,
                        sideGutter = outerInset,
                    )
                }
            } }
        }
    }

    @Test
    fun `approved grouped rows preserve right swipe removal`() = runComposeUiTest {
        val removed = mutableListOf<Int>()
        mountQueue(songs(6), locked = false, removed = removed, outerInset = 14.dp, flat = true)
        onNodeWithText("Track 2").performTouchInput { swipeRight() }
        waitForIdle()
        assertEquals(listOf(2), removed)
    }

    @Test
    fun `grouped vertical swipes scroll both ways without removing a row`() = runComposeUiTest {
        val removed = mutableListOf<Int>()
        mountQueue(songs(21), locked = false, removed = removed, outerInset = 14.dp, flat = true)
        onNode(hasScrollAction()).performTouchInput { swipeUp() }
        waitForIdle()
        onNode(hasScrollAction()).performTouchInput { swipeDown() }
        waitForIdle()
        assertTrue(removed.isEmpty(), "vertical scrolling must not activate row removal")
        onNode(hasScrollAction()).performScrollToNode(hasText("Track 20"))
        onNodeWithText("Track 20").assertIsDisplayed()
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
    fun `approved video inset keeps narrow queue row reachable and swipe index unchanged`() = runComposeUiTest {
        val removed = mutableListOf<Int>()
        mountQueue(songs(21), locked = false, removed = removed, outerInset = 0.dp)
        onNode(hasScrollAction()).performScrollToNode(hasText("Track 20"))
        onNodeWithText("Track 20").assertIsDisplayed().performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(listOf(20), removed)
    }

    @Test
    fun `zero inset held row stays horizontally aligned during a real pointer drag`() = runComposeUiTest {
        val removed = mutableListOf<Int>()
        val dragStates = mutableListOf<Boolean>()
        mountQueue(songs(6), locked = false, removed = removed, outerInset = 0.dp, dragStates = dragStates)
        val before = onNodeWithText("Track 1").fetchSemanticsNode().boundsInRoot
        val mergedBounds = onAllNodesWithContentDescription("Drag to reorder")[0].fetchSemanticsNode().boundsInRoot
        val handle = onAllNodesWithContentDescription("Drag to reorder", useUnmergedTree = true)[0].assertIsDisplayed()
        val handleBounds = handle.fetchSemanticsNode().boundsInRoot
        println("DRAG_PROBE merged=$mergedBounds unmerged=$handleBounds rowText=$before")
        assertTrue(handleBounds.width < mergedBounds.width,
            "probe must target the glyph's unmerged handle rather than the clickable row")
        val rootBounds = onRoot().fetchSemanticsNode().boundsInRoot
        val start = handleBounds.center - rootBounds.topLeft
        onRoot().performTouchInput {
            down(start)
            advanceEventTime(32)
            moveTo(start + androidx.compose.ui.geometry.Offset(0f, 12f))
            advanceEventTime(32)
            moveTo(start + androidx.compose.ui.geometry.Offset(0f, 30f))
            advanceEventTime(32)
            moveTo(start + androidx.compose.ui.geometry.Offset(0f, 36f))
        }
        waitForIdle()
        assertTrue(dragStates.contains(true), "real handle drag must activate the held overlay; states=$dragStates")
        // The hidden stand-in stays at the list slot. The held copy moved
        // with the actual handle gesture, so identify it by vertical geometry.
        val copies = onAllNodesWithText("Track 1").fetchSemanticsNodes()
        assertEquals(2, copies.size, "activated drag keeps stand-in plus held overlay")
        val moved = copies.filter { kotlin.math.abs(it.boundsInRoot.top - before.top) > 1f }
        assertEquals(1, moved.size, "one visible held overlay moves, one list stand-in stays")
        val held = moved.single().boundsInRoot
        assertTrue(kotlin.math.abs(held.left - before.left) <= 1f,
            "overlay/list text start diverged: ${held.left} vs ${before.left}")
        onRoot().performTouchInput { up() }
        waitForIdle()
        assertEquals(listOf(true, false), dragStates)
        assertEquals(emptyList<Int>(), removed)
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

    @Test
    fun `the first autoplay row swipes away with its own timeline index`() = runComposeUiTest {
        val queue = songs(3) + Song(
            videoId = "va0",
            title = "Autoplay pick",
            artist = "Artist",
            thumbnailUrl = null,
            queueTier = QueueTier.AUTOPLAY,
            queueEntryId = "ea0",
        ) + Song(
            videoId = "va1",
            title = "Second pick",
            artist = "Artist",
            thumbnailUrl = null,
            queueTier = QueueTier.AUTOPLAY,
            queueEntryId = "ea1",
        )
        val removed = mutableListOf<Int>()
        mountQueue(queue, locked = false, removed = removed, autoplayEnabled = true)
        // The autoplay section follows context rows and its heading. Semantics
        // can retain a row below this bounded pane; target a displayed row,
        // just as a person must scroll it into view before swiping.
        println("AUTOPLAY_BEFORE_SCROLL row=${onNodeWithText("Autoplay pick").fetchSemanticsNode().boundsInRoot} root=${onRoot().fetchSemanticsNode().boundsInRoot}")
        onNode(hasScrollAction()).performScrollToNode(hasText("Autoplay pick"))
        onNodeWithText("Autoplay pick").assertIsDisplayed().performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(listOf(3), removed)
    }

    @Test
    fun `a partial swipe reveals the remove cue without removing`() = runComposeUiTest {
        val removed = mutableListOf<Int>()
        mountQueue(songs(6), locked = false, removed = removed)
        onNodeWithText("Track 2").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-width * 0.25f, 0f))
        }
        onNodeWithContentDescription("Remove").assertIsDisplayed()
        onNodeWithText("Track 2").performTouchInput { up() }
        waitForIdle()
        assertEquals(emptyList<Int>(), removed)
    }
}
