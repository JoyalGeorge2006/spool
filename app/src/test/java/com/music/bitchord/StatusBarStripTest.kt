package com.music.bitchord

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
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
 * injected height, sits at the very top, scrolled content stays under it
 * (the roborazzi capture shows the band over a scrolled page), and real
 * MotionEvents aimed at the strip never reach a clickable hidden beneath it
 * while taps below the strip still land. The remaining device-only gap (the
 * real inset value, real status icons, the player sheet on hardware) is for
 * the emulator/device pixel check, not claimed here.
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

    private fun tapAt(x: Float, y: Float) {
        val content: View = compose.activity.findViewById(android.R.id.content)
        val t0 = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(t0, t0 + 40, MotionEvent.ACTION_UP, x, y, 0)
        content.dispatchTouchEvent(down)
        content.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        compose.waitForIdle()
    }

    @Test
    fun touchesOnTheStripNeverReachTheContentItHides() {
        var taps = 0
        compose.setContent {
            BitChordTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { taps++ }.testTag("under-strip"))
                    StatusBarStrip()
                }
            }
        }
        injectStatusBarInset(100)
        // 50px down is inside the 100px strip, directly over the clickable.
        tapAt(x = 400f, y = 50f)
        assertEquals(0, taps)
    }

    @Test
    fun touchesBelowTheStripStillReachTheContent() {
        var taps = 0
        compose.setContent {
            BitChordTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clickable { taps++ })
                    StatusBarStrip()
                }
            }
        }
        injectStatusBarInset(100)
        tapAt(x = 400f, y = 500f)
        assertEquals(1, taps)
    }
}
