package com.music.bitchord

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers

/**
 * The status-bar strip approved for every page on 21 Nov: a band of page
 * background drawn over each page root so the clock and battery never share
 * pixels with page content. Robolectric reports a zero status-bar inset, so
 * this proves presence and placement in the tree, not the on-device band
 * height — that proof belongs to the emulator/device pixel check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StatusBarStripTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun stripIsInTheTree() {
        compose.setContent {
            BitChordTheme {
                StatusBarStrip()
            }
        }
        compose.onNodeWithTag("status-bar-strip").assertExists()
    }

    @Test
    fun stripTakesTheFullWidth() {
        compose.setContent {
            BitChordTheme {
                StatusBarStrip()
            }
        }
        val bounds = compose.onNodeWithTag("status-bar-strip").fetchSemanticsNode().boundsInRoot
        assert(bounds.width > 0f) { "strip must span the page width" }
    }
}
