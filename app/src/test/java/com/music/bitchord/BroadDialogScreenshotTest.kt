package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.*
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Isolated dialog fixtures. No auth, downloads, provider calls or account changes requested. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BroadDialogScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun shot(dark: Boolean, page: String, material: Boolean = false) {
        V6ScreenshotTest().installUiHost()
        val oldTheme = AppSettings.themeMode.value
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        try {
            compose.setContent { BitChordTheme(darkTheme = dark, palette = if(material) com.music.bitchord.data.settings.PaletteMode.MATERIAL_YOU else com.music.bitchord.data.settings.PaletteMode.ASTRYX) {
                val haze = remember { HazeState() }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (page) {
                        "translation" -> TranslationLanguageDialog(haze, {})
                        "lyrics_sources" -> LyricsSourcesDialog(haze, {})
                        "account_empty", "account_loading", "account_choices" -> AccountChannelDialog(
                            channels = if (page == "account_choices") listOf(
                                AccountChannel("Fixture listener", "listener@example.invalid", null, "fixture:primary", null, true),
                                AccountChannel("An unusually long fixture channel name for layout checking", "Brand channel", null, "fixture:brand", null, false)
                            ) else emptyList(),
                            loading = page == "account_loading", selectedKey = "fixture:primary",
                            hazeState = haze, onSelect = {}, onChooseInYouTube = {}, onDismiss = {})
                        "update" -> UpdateAvailableDialog("1.9.3", "Fixture release notes. No download or install is performed.", haze, {}, {}, {}, {}, {})
                        "playlist_empty", "playlist_create" -> PlaylistPickerSheet(emptyList(), false, {}, { _, _ -> }, {}, startCreating = page == "playlist_create", offline = true)
                        "replay_empty", "replay_loading" -> com.music.bitchord.ui.replay.ReplayScreen(
                            state = com.music.bitchord.ui.replay.ReplayState(com.music.bitchord.data.stats.ReplayPeriod.THIS_YEAR, null, page == "replay_loading", null),
                            holder = "Fixture listener", onPeriodChange = {}, onOpenStory = {}, onPlaySong = {},
                            onOpenArtist = { _, _ -> }, onOpenAlbum = { _, _, _, _ -> }, onShare = {}, contentPadding = PaddingValues())
                    }
                }
            } }
            compose.waitForIdle()
            compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/dialog_${page}_${material}_${if (dark) "dark" else "light"}.png")
        } finally { AppSettings.setThemeMode(oldTheme) }
    }
    @Test fun translation_light() = shot(false, "translation")
    @Test fun translation_dark() = shot(true, "translation")
    @Test fun lyrics_sources_light() = shot(false, "lyrics_sources")
    @Test fun lyrics_sources_dark() = shot(true, "lyrics_sources")
    @Test fun account_empty_light() = shot(false, "account_empty")
    @Test fun account_empty_dark() = shot(true, "account_empty")
    @Test fun account_loading_light() = shot(false, "account_loading")
    @Test fun account_loading_dark() = shot(true, "account_loading")
    @Test fun account_choices_light() = shot(false, "account_choices")
    @Test fun account_choices_dark() = shot(true, "account_choices")
    @Test fun update_light() = shot(false, "update")
    @Test fun update_dark() = shot(true, "update")
    @Test fun playlist_empty_light() = shot(false, "playlist_empty")
    @Test fun playlist_empty_dark() = shot(true, "playlist_empty")
    @Test fun playlist_create_light() = shot(false, "playlist_create")
    @Test fun playlist_create_dark() = shot(true, "playlist_create")
    @Test fun replay_empty_light() = shot(false, "replay_empty")
    @Test fun replay_empty_dark() = shot(true, "replay_empty")
    @Test fun replay_loading_light() = shot(false, "replay_loading")
    @Test fun replay_loading_dark() = shot(true, "replay_loading")
    @Test fun material_translation_true()=shot(true,"translation",true)
    @Test fun material_translation_false()=shot(false,"translation",true)
    @Test fun material_lyrics_sources_true()=shot(true,"lyrics_sources",true)
    @Test fun material_lyrics_sources_false()=shot(false,"lyrics_sources",true)
    @Test fun material_account_empty_true()=shot(true,"account_empty",true)
    @Test fun material_account_empty_false()=shot(false,"account_empty",true)
    @Test fun material_account_loading_true()=shot(true,"account_loading",true)
    @Test fun material_account_loading_false()=shot(false,"account_loading",true)
    @Test fun material_account_choices_true()=shot(true,"account_choices",true)
    @Test fun material_account_choices_false()=shot(false,"account_choices",true)
    @Test fun material_update_true()=shot(true,"update",true)
    @Test fun material_update_false()=shot(false,"update",true)
    @Test fun material_playlist_empty_true()=shot(true,"playlist_empty",true)
    @Test fun material_playlist_empty_false()=shot(false,"playlist_empty",true)
    @Test fun material_playlist_create_true()=shot(true,"playlist_create",true)
    @Test fun material_playlist_create_false()=shot(false,"playlist_create",true)
    @Test fun material_replay_empty_true()=shot(true,"replay_empty",true)
    @Test fun material_replay_empty_false()=shot(false,"replay_empty",true)
    @Test fun material_replay_loading_true()=shot(true,"replay_loading",true)
    @Test fun material_replay_loading_false()=shot(false,"replay_loading",true)
}
