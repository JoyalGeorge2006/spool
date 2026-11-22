package com.music.bitchord

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.InlineFrostSheet
import com.music.bitchord.ui.components.frostedCard
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proof for the new SpoolFrost helper, not legacy glass: the same card and
 * busy backdrop rendered with the Blur effects switch ON versus OFF. ON must
 * draw the frosted treatment (semi-transparent tint over the page's haze
 * source); OFF must draw the solid fallback. The CI artifacts let each pair
 * be pixel-compared: identical PNGs would mean the switch or the helper does
 * nothing. These captures run on the JVM, so they prove the gate and the two
 * render paths - real blur quality still needs a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SpoolFrostScreenshotTest {

    private fun frostShot(name: String, dark: Boolean, blurOn: Boolean) {
        val storedTheme = AppSettings.themeMode.value
        val storedBlur = AppSettings.blurEffects.value
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        AppSettings.blurEffects.value = blurOn
        try {
            captureRoboImage("build/outputs/roborazzi/$name.png") {
                BitChordTheme(darkTheme = dark) {
                    FrostScene(dark)
                }
            }
        } finally {
            AppSettings.setThemeMode(storedTheme)
            AppSettings.blurEffects.value = storedBlur
        }
    }

    @Composable
    private fun FrostScene(dark: Boolean) {
        val haze = remember { HazeState() }
        Box(Modifier.fillMaxSize()) {
            // Busy stand-in art: a high-frequency checker so any transparency
            // or blur under the card is visible, smooth blocks would hide it.
            Canvas(Modifier.fillMaxSize().hazeSource(haze)) {
                val cell = 24.dp.toPx()
                val a = if (dark) Color(0xFF7A3FF2) else Color(0xFF9D46FF)
                val b = if (dark) Color(0xFF00B8A9) else Color(0xFF00C2A8)
                val c = if (dark) Color(0xFFFFC641) else Color(0xFFFFB300)
                var row = 0
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    var col = row
                    while (x < size.width) {
                        drawRect(
                            color = when (col % 3) { 0 -> a; 1 -> b; else -> c },
                            topLeft = Offset(x, y),
                            size = Size(cell, cell),
                        )
                        x += cell
                        col++
                    }
                    y += cell
                    row++
                }
            }
            Column(
                Modifier
                    .align(Alignment.Center)
                    .frostedCard(haze, MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(28.dp))
                    .padding(horizontal = 28.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "Frosted card",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Blur " + if (AppSettings.blurEffects.value) "on" else "off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    /**
     * The same proof for the inline sheet wrapper: the sheet over the busy
     * backdrop, Blur effects ON versus OFF. ON must frost over the page's
     * haze; OFF must draw the solid container fill.
     */
    private fun sheetShot(name: String, dark: Boolean, blurOn: Boolean) {
        val storedTheme = AppSettings.themeMode.value
        val storedBlur = AppSettings.blurEffects.value
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        AppSettings.blurEffects.value = blurOn
        try {
            captureRoboImage("build/outputs/roborazzi/$name.png") {
                BitChordTheme(darkTheme = dark) {
                    SheetScene()
                }
            }
        } finally {
            AppSettings.setThemeMode(storedTheme)
            AppSettings.blurEffects.value = storedBlur
        }
    }

    @Composable
    private fun SheetScene() {
        val haze = remember { HazeState() }
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().hazeSource(haze)) {
                val cell = 24.dp.toPx()
                var y = 0f
                var row = 0
                while (y < size.height) {
                    var x = 0f
                    var col = row
                    while (x < size.width) {
                        drawRect(
                            color = when (col % 3) { 0 -> Color(0xFF7A3FF2); 1 -> Color(0xFF00B8A9); else -> Color(0xFFFFC641) },
                            topLeft = Offset(x, y),
                            size = Size(cell, cell),
                        )
                        x += cell
                        col++
                    }
                    y += cell
                    row++
                }
            }
            InlineFrostSheet(onDismiss = {}, hazeState = haze) {
                Text(
                    text = "Inline sheet",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                )
            }
        }
    }

    @Test
    fun inline_sheet_on_dark() = sheetShot("inline_sheet_on_dark", dark = true, blurOn = true)

    @Test
    fun inline_sheet_off_dark() = sheetShot("inline_sheet_off_dark", dark = true, blurOn = false)

    // The Material You light appearance gets the same on/off pair as dark:
    // the frost tint differs per scheme, so one scheme's capture proves
    // nothing about the other.
    @Test
    fun inline_sheet_on_light() = sheetShot("inline_sheet_on_light", dark = false, blurOn = true)

    @Test
    fun inline_sheet_off_light() = sheetShot("inline_sheet_off_light", dark = false, blurOn = false)

    @Test
    fun frost_on_dark() = frostShot("frost_on_dark", dark = true, blurOn = true)

    @Test
    fun frost_off_dark() = frostShot("frost_off_dark", dark = true, blurOn = false)

    @Test
    fun frost_on_light() = frostShot("frost_on_light", dark = false, blurOn = true)

    @Test
    fun frost_off_light() = frostShot("frost_off_light", dark = false, blurOn = false)
}
