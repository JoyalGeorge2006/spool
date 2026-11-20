package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.music.bitchord.ui.components.LyricsSourcesDialog
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The lyrics-sources list is intentionally capped and scrollable
 * (LyricsSourcesDialog.kt ReorderableSourceList: heightIn(max = SOURCES_MAX_HEIGHT)
 * + verticalScroll), so a static capture shows the tail row clipped at the
 * viewport boundary. This proves that boundary is a scroll viewport, not an
 * unreachable row or an overlap: swiping inside the list must bring the last
 * source fully into the viewport, while Reset and Done never leave the card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = com.github.takahirom.roborazzi.RobolectricDeviceQualifiers.Pixel7)
class LyricsSourcesDialogScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lastSourceRowScrollsFullyIntoView() {
        V6ScreenshotTest().installUiHost()
        compose.setContent {
            BitChordTheme(darkTheme = true) {
                val haze = remember { HazeState() }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    LyricsSourcesDialog(haze, {})
                }
            }
        }
        compose.waitForIdle()

        val scrollers = compose.onAllNodes(hasScrollAction())
        val scroller = scrollers[scrollers.fetchSemanticsNodes().size - 1]
        scroller.assertExists()

        repeat(8) {
            scroller.performTouchInput { swipeUp(center.y, center.y - 400f) }
            compose.waitForIdle()
        }

        val viewport = scroller.fetchSemanticsNode().boundsInRoot
        // Genius is the FINAL row of LyricsSource.offered (declaration order,
        // hidden entries filtered): lrc.red ... Musixmatch, Genius.
        val lastRow = compose.onNodeWithText("Genius")
        lastRow.assertIsDisplayed()
        val row = lastRow.fetchSemanticsNode().boundsInRoot
        Assert.assertTrue(
            "last source row must be fully inside the scrolled viewport (row=$row viewport=$viewport)",
            row.top >= viewport.top && row.bottom <= viewport.bottom,
        )

        // The actions below the capped list stay put the whole time.
        compose.onNodeWithText("Reset to default").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }
}
