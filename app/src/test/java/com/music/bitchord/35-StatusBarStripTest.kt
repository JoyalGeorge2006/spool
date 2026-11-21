package com.music.bitchord

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The status-bar strip approved for every page on 21 Nov: a band of page
 * background drawn over each page root so the clock and battery never share
 * pixels with page content, scrolled or not.
 *
 * Robolectric reports a zero status-bar inset on its own, so these tests
 * inject a nonzero status-bar inset (100px) into the view hierarchy and then
 * check what actually lays out and renders: the strip takes exactly the
 * injected height, sits at the very top, and scrolled content stays under it
 * (the roborazzi capture shows the band over a scrolled page). The remaining
 * device-only gap (the real inset value, real status icons, the player
 * overlay on hardware) is for the emulator/device pixel check, not claimed
 * here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StatusBarStripTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun injectStatusBarInset(px: Int) {
        val content: View = compose.activity.findViewById(android.R.id.content)
        ViewCompat.dispatchApplyWindowInsets(
            content,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px, 0, 0))
                .build(),
        )
        compose.waitForIdle()
    }

    @Test
    fun stripIsInTheTree() {
        compose.setContent { BitChordTheme { StatusBarStrip() } }
        compose.onNodeWithTag("status-bar-strip").assertExists()
    }

    @Test
    fun stripTakesExactlyTheInjectedInsetHeight() {
        compose.setContent { BitChordTheme { StatusBarStrip() } }
        injectStatusBarInset(100)
        val bounds = compose.onNodeWithTag("status-bar-strip").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, bounds.top, 0.5f)
        assertEquals(100f, bounds.height, 0.5f)
    }

    @Test
    fun stripSpansTheFullWidth() {
        compose.setContent { BitChordTheme { StatusBarStrip() } }
        injectStatusBarInset(100)
        val strip = compose.onNodeWithTag("status-bar-strip").fetchSemanticsNode().boundsInRoot
        val rootWidth = compose.activity.resources.displayMetrics.widthPixels.toFloat()
        assertEquals(rootWidth, strip.width, 1f)
    }

    @Test
    fun scrolledContentStaysUnderTheStrip() {
        compose.setContent {
            BitChordTheme {
                Box(Modifier.fillMaxSize().testTag("strip-test-root")) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(50) { Text("row $it") }
                    }
                    StatusBarStrip()
                }
            }
        }
        injectStatusBarInset(100)
        val bounds = compose.onNodeWithTag("status-bar-strip").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, bounds.top, 0.5f)
        assertEquals(100f, bounds.height, 0.5f)
        compose.onNodeWithTag("strip-test-root").captureRoboImage()
    }
}
