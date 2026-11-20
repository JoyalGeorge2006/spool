package com.music.bitchord

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.theme.BitChordTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The production player drawer over REAL artwork, Blur effects on vs off.
 *
 * This is the integration proof the bare player shot cannot give: the sheet
 * under test is the production [com.music.bitchord.ui.player.LyricsProviderSheet],
 * reached through live production wiring: the landscape player's lyric strip
 * (CurrentLyricStrip onClick = openLyrics, NowPlayingScreen.kt:1832-1843)
 * opens the lyrics pane (pane = PlayerPane.Lyrics at :1736), whose status
 * caption "Lyrics by lrc.red" (PlayerLyrics.kt:3188-3190 builds it from
 * lyricsSource) has onStatusClick wired to showLyricsProviders = true
 * (NowPlayingScreen.kt:1917-1918), opening the sheet at :1480 with the
 * player's own haze source (:1666-1670), frost-gated at PlayerDrawer.kt:114
 * (PlayerPlatform.host.frostEnabled()). Nothing about the conditional is
 * copied into this test; every step goes through production composables.
 *
 * Why landscape: flatPlayer is hardcoded true (NowPlayingScreen.kt:838), so
 * in PORTRAIT the player is always the flat tabbed layout - the portrait
 * strip and caption live in the unreachable !flatPage block (:3419-3455) and
 * the flat portrait lyrics tab carries no provider caption. Run 44701227680
 * proved it: the scripted portrait click landed on an offscreen-composed
 * pager page (PlayerSwipePages uses beyondViewportPageCount = 2), hit a stub
 * seek, and the caption never existed. In landscape the pane AnimatedContent
 * composes only the selected pane (LandscapePlayer.kt:200-243), so the strip
 * is the only "First line of the song" node; the click below additionally
 * waits for exactly one matching node, so a silently offscreen or duplicated
 * match fails loudly instead of tapping the wrong thing.
 * (The flat-portrait provider-sheet reachability gap - the sheet is reachable
 * only from landscape or Settings - is base v1.9.17 behavior, reported
 * separately as a product finding; this test does not paper over it.)
 *
 * Assertions are limited to what the JVM renderer can honestly prove:
 * - the sheet opens through the production path in both switch positions
 *   (title + provider rows present in semantics);
 * - the drawer region visibly changes versus the bare player control, i.e.
 *   the sheet really renders over the artwork.
 * The ON-vs-OFF drawer-region pixel comparison is REPORTED as data, not
 * asserted: Skiko may render haze identically in both switch positions on the
 * JVM, and a pixel-identity result is a renderer limitation, not a wiring
 * failure. The branch itself is pinned by PlayerFrostGateTest.
 *
 * Coil loads artwork on its own dispatchers, off the compose test clock (run
 * 44701227680 captured the placeholder by racing it), so the control capture
 * polls on a real-time deadline until the fixture's saturated red cells are
 * actually on screen; if the art never loads the test fails loudly on the
 * explicit artLoaded check before any drawer step.
 *
 * Pixels come from Roborazzi's capture path plus a BitmapFactory read-back:
 * captureToImage is unusable under Robolectric here - its WindowCapture
 * forceRedraw is never satisfied within the timeout on this runner (run
 * 44695670032), while captureRoboImage is the path the whole v6 screenshot
 * set passes with. The capture lands in build/outputs/roborazzi, so the PNGs
 * reach the shots artifact in record mode.
 * All three captures come from ONE composition - createComposeRule
 * permits a single setContent per test - with the switch exercised by
 * toggling the same MutableStateFlow the production frost gate collects.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PlayerDrawerFrostShotTest {
    @get:Rule val compose = createComposeRule()

    private fun writeFixtureArt(): String {
        val size = 512
        val cell = 32
        val colors = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt())
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) for (x in 0 until size) {
            bmp.setPixel(x, y, colors[((x / cell) + (y / cell)) % 4])
        }
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "fixture_art_drawer.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file).toString()
    }

    private fun lastRoot(): SemanticsNodeInteraction =
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).let { roots -> roots[roots.fetchSemanticsNodes().size - 1] }

    private fun captureAndStore(name: String): Bitmap {
        val out = File("build/outputs/roborazzi/$name.png")
        out.parentFile?.mkdirs()
        // Force Record task type: the default comes from the roborazzi gradle
        // task system property, which is None under the plain unit-test task -
        // captureRoboImage is then a no-op and the read-back would fail. The
        // record path is the supported capture route on this runner either way.
        lastRoot().captureRoboImage(
            out.absolutePath,
            RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
        return BitmapFactory.decodeFile(out.absolutePath)
            ?: error("$name capture must decode")
    }

    /** The checker's red cell is saturated; nothing in the placeholder or theme is. */
    private fun artLoaded(b: Bitmap): Boolean {
        var red = 0
        for (y in 0 until b.height step 6) for (x in 0 until b.width step 6) {
            val p = b.getPixel(x, y)
            if (Color.red(p) > 180 && Color.green(p) < 100 && Color.blue(p) < 100) red++
        }
        return red > 200
    }

    /** See PlayerArtworkShotTest: poll the real pixels until Coil's load lands. */
    private fun captureWhenArtLoaded(name: String, timeoutMs: Long = 45_000): Bitmap {
        val deadline = System.currentTimeMillis() + timeoutMs
        var bmp = captureAndStore(name)
        while (!artLoaded(bmp) && System.currentTimeMillis() < deadline) {
            Thread.sleep(300)
            compose.waitForIdle()
            bmp = captureAndStore(name)
        }
        return bmp
    }

    private fun assertDrawerOpen() {
        compose.onNodeWithText("Choose lyrics provider").assertExists()
        compose.onNodeWithText("BiniLyrics").assertExists()
    }

    @Test fun productionDrawerOpensOverRealArtwork() {
        val art = writeFixtureArt()
        val v6 = V6ScreenshotTest()
        v6.installUiHost()
        val restore = v6.installVideoStubHost()
        val storedTheme = AppSettings.themeMode.value
        val oldBlur = AppSettings.blurEffects.value
        try {
            AppSettings.setThemeMode(ThemeMode.DARK)
            AppSettings.blurEffects.value = true
            // The landscape player's lyric clock intentionally never becomes
            // idle (LandscapePlayerFrameTest.kt:30-32): advance the test clock
            // explicitly instead of letting waitForIdle drain it forever.
            compose.mainClock.autoAdvance = false
            compose.setContent {
                BitChordTheme(darkTheme = true) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        v6.PlayerShot(landscape = true, artworkUrl = art)
                    }
                }
            }
            // Coil loads the file asynchronously off the test clock; settle a
            // few frames, then let the polling capture wait out the load.
            repeat(4) { compose.mainClock.advanceTimeBy(500); compose.waitForIdle() }
            val control = captureWhenArtLoaded("player_drawer_control_on_dark")
            Assert.assertTrue(
                "fixture art must be on screen before the drawer opens",
                artLoaded(control),
            )

            // Production entry point: tap the live lyric strip under the
            // landscape credits to open the lyrics pane, then the source
            // caption the pane wires to the provider sheet. Both fail loudly
            // if the wiring breaks. In landscape only the selected pane is
            // composed (LandscapePlayer.kt:200-243), so the strip is the one
            // node with this text; the wait pins that uniqueness instead of
            // trusting it.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("First line of the song").fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithText("First line of the song").performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            compose.onNodeWithText("Lyrics by lrc.red").performClick()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            assertDrawerOpen()
            val on = captureAndStore("player_drawer_frost_on_dark")

            // Flip the very flow the production frost gate collects; the
            // composition recomposes in place, no second setContent.
            AppSettings.blurEffects.value = false
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            repeat(4) { compose.mainClock.advanceTimeBy(500); compose.waitForIdle() }
            assertDrawerOpen()
            val off = captureAndStore("player_drawer_frost_off_dark")

            Assert.assertTrue(
                "captures must be the same size",
                control.width == on.width && control.height == on.height && on.width == off.width && on.height == off.height,
            )

            // The bottom-anchored drawer band, sampled through the frame
            // middle. Fractions, so minor layout shifts do not break this.
            val y0 = (on.height * 0.72).toInt()
            val y1 = (on.height * 0.92).toInt()
            val x0 = (on.width * 0.10).toInt()
            val x1 = (on.width * 0.90).toInt()

            // 1. The sheet must really render over the artwork: most of the
            //    drawer band differs from the same band of the bare player.
            var bandTotal = 0
            var bandDiff = 0
            for (y in y0 until y1 step 4) for (x in x0 until x1 step 4) {
                bandTotal++
                if (on.getPixel(x, y) != control.getPixel(x, y)) bandDiff++
            }
            Assert.assertTrue(
                "production drawer must visibly cover the player over real artwork ($bandDiff of $bandTotal sampled band pixels differ)",
                bandDiff * 2 > bandTotal,
            )

            // 2. ON vs OFF over the same band: reported as evidence, never
            //    asserted - Skiko may draw haze identically either way on JVM.
            var switchDiff = 0
            for (y in y0 until y1 step 4) for (x in x0 until x1 step 4) {
                if (on.getPixel(x, y) != off.getPixel(x, y)) switchDiff++
            }
            System.err.println(
                "PlayerDrawerFrostShotTest: drawer band switch-sensitivity = $switchDiff of $bandTotal sampled pixels differ (0 means Skiko rendered haze identically; visual frost quality is device-only evidence)",
            )
        } finally {
            compose.mainClock.autoAdvance = true
            AppSettings.blurEffects.value = oldBlur
            AppSettings.setThemeMode(storedTheme)
            com.music.bitchord.ui.player.PlayerPlatform.install(restore)
        }
    }
}
