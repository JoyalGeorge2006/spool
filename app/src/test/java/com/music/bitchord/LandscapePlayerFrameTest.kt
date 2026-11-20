package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Bounded playing frames and separately named paused layout. No production clock bypass. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w780dp-h360dp-land-mdpi")
class LandscapePlayerFrameTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(dark: Boolean, playing: Boolean) {
        // A playing lyric clock intentionally never becomes idle. Advance the test
        // clock explicitly instead of asking Robolectric's static helper to drain it.
        compose.mainClock.autoAdvance = false
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val host = fixture.installVideoStubHost()
        val oldTheme = AppSettings.themeMode.value
        val oldSyncedLyrics = AppSettings.syncedLyrics.value
        AppSettings.setSyncedLyrics(true)
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        val name = "landscape_${if (dark) "dark" else "light"}_${if (playing) "playing_frames" else "paused_layout"}"
        try {
            compose.setContent { BitChordTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("landscape-root")) {
                    fixture.PlayerShot(landscape = true, playing = playing)
                }
            } }
            var elapsed = 0L
            for (at in listOf(16L, 160L, 400L, 1000L)) {
                compose.mainClock.advanceTimeBy(at - elapsed)
                elapsed = at
                compose.waitForIdle()
                if (at >= 400L) {
                    compose.onNodeWithText("Midnight Rain").assertIsDisplayed()
                    compose.onNodeWithText("First line of the song").assertIsDisplayed()
                    val bounds = compose.onNodeWithTag("landscape-root").fetchSemanticsNode().boundsInRoot
                    assertTrue("Fixture must be a real wide landscape root", bounds.width > bounds.height * 1.8f)
                }
                compose.onNodeWithTag("landscape-root").captureRoboImage("build/outputs/roborazzi/${name}_$at.png")
            }
            compose.onNodeWithText("Audio output").assertDoesNotExist()
            compose.onNodeWithText("Up next").assertDoesNotExist()
            compose.onNodeWithText("Queue").assertIsDisplayed()
            compose.onNodeWithText("Lyrics").assertIsDisplayed()
            compose.onNodeWithText("Related").assertIsDisplayed()
            for (tab in listOf("Lyrics", "Queue", "Related")) {
                compose.onNodeWithText(tab).performClick()
                repeat(8) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() }
                compose.onNodeWithTag("player-section-header").assertIsDisplayed()
                compose.onNodeWithText(tab).assertIsSelected()
                when (tab) {
                    "Lyrics" -> compose.onNodeWithText("First line of the song").assertIsDisplayed()
                    "Queue" -> compose.onNodeWithText("Track 1").assertIsDisplayed()
                    "Related" -> {
                        compose.onNodeWithText("Track 5").assertIsDisplayed()
                        compose.onNodeWithText("Track 1").assertDoesNotExist()
                    }
                }
                compose.onNodeWithTag("landscape-root").captureRoboImage("build/outputs/roborazzi/${name}_section_$tab.png")
            }
            compose.onNodeWithContentDescription("Back to player").performClick()
            repeat(8) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() }
            compose.onNodeWithTag("player-section-header").assertDoesNotExist()
            compose.onNodeWithText("Midnight Rain").assertIsDisplayed()
            compose.onNodeWithTag("landscape-root").captureRoboImage("build/outputs/roborazzi/${name}_return_main.png")
        } finally {
            AppSettings.setSyncedLyrics(oldSyncedLyrics)
            AppSettings.setThemeMode(oldTheme)
            PlayerPlatform.install(host)
        }
    }
    @Test fun playingLight() = capture(false, true)
    @Test fun playingDark() = capture(true, true)
    @Test fun pausedLayoutLight() = capture(false, false)
    @Test fun pausedLayoutDark() = capture(true, false)
}
