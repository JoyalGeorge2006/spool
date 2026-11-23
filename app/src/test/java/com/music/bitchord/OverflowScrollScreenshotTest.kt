package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.SongActionsMenu
import com.music.bitchord.ui.components.SpoolPlayerOverflow
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real menu compositor with callbacks as fixtures. No clipboard, cast or network effects. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class OverflowScrollScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(dark: Boolean, page: String) {
        val oldTheme = AppSettings.themeMode.value
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        var copies = 0
        try {
            compose.setContent {
                BitChordTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SpoolPlayerOverflow(onDismiss = {}) {
                            SongActionsMenu(
                                song = Song("fixture-overflow", "Overflow fixture", "Fixture artist", null,
                                    albumId = "fixture-album", albumName = "Fixture album", artistId = "fixture-artist"),
                                signedIn = true, likeStatus = LikeStatus.INDIFFERENT,
                                onPlayNext = {}, onAddToQueue = {}, onStartRadio = {}, onDownload = {},
                                onToggleLike = {}, onToggleDislike = {}, onAddToPlaylist = {},
                                onUpgradeQuality = { error("Removed action must not run") },
                                onOpenAlbum = {}, onOpenArtist = {},
                                onShare = {}, onCopyLog = { copies++ }, onLyricsOffset = {},
                                showSleepTimer = true,
                                onCast = {}, onAudioOutput = {}, onAutomixOverview = {}, onRemoveFromCache = {},
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithText("Upgrade quality", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("Like", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Dislike", useUnmergedTree = true).assertIsDisplayed()
            compose.onAllNodes(isRoot()).onLast().captureRoboImage(
                "build/outputs/roborazzi/menu_top_${page}_${if (dark) "dark" else "light"}.png")
            val target = when (page) {
                "sleep" -> "Sleep timer"
                "info" -> "Song info"
                else -> "Copy log"
            }
            compose.onNodeWithText(target, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.waitForIdle()
            if (page == "bottom") {
                // Text visibility alone leaves the row icon at the viewport edge.
                // Continue the physical scroll before judging final-row clearance.
                compose.onAllNodes(hasScrollAction()).onLast().performTouchInput { swipeUp() }
                compose.waitForIdle()
                compose.onNodeWithText("Copy log", useUnmergedTree = true).assertIsDisplayed()
            }
            if (page != "bottom") {
                compose.onNodeWithText(target, useUnmergedTree = true).performTouchInput { click() }
                compose.waitForIdle()
            }
            compose.onAllNodes(isRoot()).onLast().captureRoboImage(
                "build/outputs/roborazzi/overflow_${page}_${if (dark) "dark" else "light"}.png")
            if (page == "bottom") {
                compose.onNodeWithText("Copy log", useUnmergedTree = true).performTouchInput { click() }
                compose.runOnIdle { assertEquals("last action remains reachable", 1, copies) }
            }
        } finally { AppSettings.setThemeMode(oldTheme) }
    }
    @Test fun overflow_bottom_light() = shot(false, "bottom")
    @Test fun overflow_bottom_dark() = shot(true, "bottom")
    @Test fun overflow_sleep_light() = shot(false, "sleep")
    @Test fun overflow_sleep_dark() = shot(true, "sleep")
    @Test fun overflow_info_light() = shot(false, "info")
    @Test fun overflow_info_dark() = shot(true, "info")
}
