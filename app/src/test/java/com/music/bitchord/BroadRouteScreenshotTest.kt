package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.*
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.screens.*
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Source-located audit gaps. Standalone fixtures, not navigation, sign-in or service acceptance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BroadRouteScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val songs = List(5) { Song("fixture:$it", "Track ${it+1}", "Fixture artist", null,
        durationText = "3:24", queueEntryId = "entry:$it") }
    private fun shot(dark: Boolean, page: String) {
        V6ScreenshotTest().installUiHost()
        val oldTheme = AppSettings.themeMode.value
        val oldEqMode = AppSettings.equalizerMode.value
        val oldEqEnabled = AppSettings.equalizerEnabled.value
        if (page.startsWith("equalizer")) {
            AppSettings.equalizerMode.value = if (page.endsWith("_manual")) com.music.bitchord.data.settings.EqualizerMode.MANUAL else com.music.bitchord.data.settings.EqualizerMode.DYNAMIC
            AppSettings.equalizerEnabled.value = true
        }
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        try {
            compose.setContent { BitChordTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (page.removeSuffix("_bottom").removeSuffix("_manual")) {
                        "equalizer" -> EqualizerScreen(PaddingValues())
                        "presets" -> EqualizerPresetPage({}, PaddingValues())
                        "history" -> HistoryScreen(UiState.Success(songs), rememberLazyListState(), { _, _ -> }, {}, {}, {}, PaddingValues())
                        "history_error" -> HistoryScreen(UiState.Error("Couldn't load listening history. Try again."), rememberLazyListState(), { _, _ -> }, {}, {}, {}, PaddingValues())
                        "discord_signed_out" -> DiscordScreen(null, 0L, 0L, {}, {}, PaddingValues())
                        "spotify_signed_out" -> SpotifyLibraryScreen({}, {}, {}, PaddingValues())
                        "downloads_empty" -> com.music.bitchord.ui.components.DownloadManagerPage({}, PaddingValues())
                        "moods" -> MoodGridPage(rememberLazyGridState(), { _, _ -> }, PaddingValues())
                        "library_grid" -> LibraryGridPage(HomeShelf("Playlists", emptyList()), rememberLazyGridState(), {}, {}, PaddingValues(), onNewPlaylist = {})
                        "language_middle" -> com.music.bitchord.ui.components.AppLanguageDialog(androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }, {})
                    }
                }
            } }
            compose.waitForIdle()
            if (page.endsWith("_bottom")) {
                val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                val target = context.getString(if (page.startsWith("discord")) R.string.discord_basic_presence_notice else R.string.system_equalizer_footer)
                compose.onNodeWithText(target, useUnmergedTree = true).performScrollTo()
                compose.onAllNodes(hasScrollAction()).onLast().performTouchInput { swipeUp() }
                compose.waitForIdle()
                compose.onNodeWithText(target, useUnmergedTree = true).assertIsDisplayed()
            }
            if (page == "language_middle") {
                val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                val middle = context.getString(R.string.chinese_simplified)
                compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText(middle))
                compose.waitForIdle()
                compose.onNodeWithText(middle).assertIsDisplayed()
            }
            compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/broad_${page}_${if (dark) "dark" else "light"}.png")
        } finally {
            AppSettings.equalizerMode.value = oldEqMode
            AppSettings.equalizerEnabled.value = oldEqEnabled
            AppSettings.setThemeMode(oldTheme)
        }
    }
    @Test fun equalizer_bottom_light() = shot(false, "equalizer_bottom")
    @Test fun equalizer_bottom_dark() = shot(true, "equalizer_bottom")
    @Test fun equalizer_manual_light() = shot(false, "equalizer_manual")
    @Test fun equalizer_manual_dark() = shot(true, "equalizer_manual")
    @Test fun discord_bottom_light() = shot(false, "discord_signed_out_bottom")
    @Test fun discord_bottom_dark() = shot(true, "discord_signed_out_bottom")
    @Test fun equalizer_light() = shot(false, "equalizer")
    @Test fun equalizer_dark() = shot(true, "equalizer")
    @Test fun presets_light() = shot(false, "presets")
    @Test fun presets_dark() = shot(true, "presets")
    @Test fun history_light() = shot(false, "history")
    @Test fun history_dark() = shot(true, "history")
    @Test fun history_error_light() = shot(false, "history_error")
    @Test fun history_error_dark() = shot(true, "history_error")
    @Test fun discord_signed_out_light() = shot(false, "discord_signed_out")
    @Test fun discord_signed_out_dark() = shot(true, "discord_signed_out")
    @Test fun spotify_signed_out_light() = shot(false, "spotify_signed_out")
    @Test fun spotify_signed_out_dark() = shot(true, "spotify_signed_out")
    @Test fun downloads_empty_light() = shot(false, "downloads_empty")
    @Test fun downloads_empty_dark() = shot(true, "downloads_empty")
    @Test fun moods_light() = shot(false, "moods")
    @Test fun moods_dark() = shot(true, "moods")
    @Test fun library_grid_light() = shot(false, "library_grid")
    @Test fun library_grid_dark() = shot(true, "library_grid")
    @Test fun language_middle_light() = shot(false, "language_middle")
    @Test fun language_middle_dark() = shot(true, "language_middle")
}
