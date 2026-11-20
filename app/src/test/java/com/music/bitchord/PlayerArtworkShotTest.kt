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
 * The player over REAL artwork, Blur effects on vs off. The empty-artwork
 * captures (player_dark/light) have a uniform hero tile, which can prove
 * nothing about the switch's reach into the artwork path.
 *
 * What this pins down, per the production design:
 * - spool's flat player stands the main page on the theme background, not on
 *   artwork (PlayerChrome.kt:20-24); there is no full-player blurred backdrop
 *   in flat portrait to sample. The switch-invariant artwork surface on this
 *   page is the HERO TILE (AsyncImage, NowPlayingScreen.kt:2804), which
 *   consults no blur flag: the Blur effects switch gates only drawer/panel
 *   frost (PlayerDrawer.kt:114 via PlayerFrost), and FrostedPlayerFrame's
 *   frost machinery captures stills for those panels without touching page
 *   pixels (PlayerFrost.kt:61-109).
 * - So the hero band must be pixel-identical with Blur effects on and off;
 *   the switch-gated frost over the player is PlayerDrawerFrostShotTest's job.
 *
 * The artwork travels the production path: Song.thumbnailUrl ->
 * rememberRemoteArtworkUrl -> artworkAt (file URLs pass through untouched) ->
 * Coil file fetch (NowPlayingScreen.kt:771).
 *
 * Coil loads on its own dispatchers, which the compose test clock does not
 * drive: run 44701227680 showed the first capture racing the load (placeholder
 * band, "found 1 colors") while a capture moments later saw the art. The
 * captures below therefore poll: capture, inspect the fixture's saturated red
 * cells, and re-capture on a real-time deadline until the art is actually on
 * screen. The assertions are unchanged - if the art never loads, the band
 * check still fails loudly.
 *
 * Pixels come from Roborazzi's capture path plus a BitmapFactory read-back:
 * captureToImage is unusable under Robolectric here - its WindowCapture
 * forceRedraw is never satisfied within the timeout on this runner (run
 * 44695670032), while captureRoboImage is the path the whole v6 screenshot
 * set passes with. The capture lands in build/outputs/roborazzi, so the PNGs
 * reach the shots artifact in record mode.
 * The switch is exercised by toggling the same MutableStateFlow the
 * production frost gate collects (rememberFrostEnabled), inside ONE
 * composition - createComposeRule permits a single setContent per test.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PlayerArtworkShotTest {
    @get:Rule val compose = createComposeRule()

    private fun writeFixtureArt(): String {
        val size = 512
        val cell = 32
        val colors = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt())
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) for (x in 0 until size) {
            bmp.setPixel(x, y, colors[((x / cell) + (y / cell)) % 4])
        }
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "fixture_art.png")
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

    /**
     * Coil's file fetch and decode run on its own dispatchers, off the compose
     * test clock, so a fixed virtual-time wait can capture the placeholder
     * (run 44701227680). Capture, check, and re-capture until the art is
     * really on screen or the real-time deadline passes; the caller's band
     * assertion still decides pass/fail.
     */
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

    @Test fun artworkBackdropIsSwitchInvariant() {
        val art = writeFixtureArt()
        val v6 = V6ScreenshotTest()
        v6.installUiHost()
        val restore = v6.installVideoStubHost()
        val storedTheme = AppSettings.themeMode.value
        val oldBlur = AppSettings.blurEffects.value
        try {
            AppSettings.setThemeMode(ThemeMode.DARK)
            AppSettings.blurEffects.value = true
            compose.setContent {
                BitChordTheme(darkTheme = true) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        v6.PlayerShot(artworkUrl = art)
                    }
                }
            }
            // Coil loads the file asynchronously off the test clock; settle a
            // few frames, then let the polling capture wait out the load.
            repeat(4) { compose.mainClock.advanceTimeBy(500); compose.waitForIdle() }
            val on = captureWhenArtLoaded("player_artwork_blur_on_dark")

            // Flip the very flow the production frost gate collects; the
            // composition recomposes in place, no second setContent.
            AppSettings.blurEffects.value = false
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            repeat(4) { compose.mainClock.advanceTimeBy(500); compose.waitForIdle() }
            val off = captureWhenArtLoaded("player_artwork_blur_off_dark")

            Assert.assertTrue("captures must be the same size", on.width == off.width && on.height == off.height)

            // A horizontal band through the hero artwork (fractions of the
            // frame, so the exact layout may move a little without breaking this).
            val y = (on.height * 0.38).toInt()
            val x0 = (on.width * 0.25).toInt()
            val x1 = (on.width * 0.75).toInt()

            // 1. Real artwork, not the placeholder tile: a placeholder band is
            //    near-uniform (background + at most the note glyph edge); the
            //    checker fixture yields several saturated colors per band.
            val onColors = (x0 until x1).map { on.getPixel(it, y) }.toSet()
            Assert.assertTrue(
                "artwork band must show the loaded fixture art, not the placeholder (found ${onColors.size} colors)",
                onColors.size >= 3,
            )

            // 2. Switch invariance: the hero artwork must not change with Blur
            //    effects. Exact pixel equality across the band.
            val mismatches = (x0 until x1).count { on.getPixel(it, y) != off.getPixel(it, y) }
            Assert.assertTrue(
                "artwork backdrop must be identical with Blur effects on and off ($mismatches of ${x1 - x0} band pixels differ)",
                mismatches == 0,
            )
        } finally {
            AppSettings.blurEffects.value = oldBlur
            AppSettings.setThemeMode(storedTheme)
            com.music.bitchord.ui.player.PlayerPlatform.install(restore)
        }
    }
}
