package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.screens.SettingsCategory
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsScrollScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @org.junit.Before
    fun installUiHost() {
        // The shared screens read settings through AppUi.host; a minimal stub
        // keeps the renders free of the app's real settings store.
        runCatching { com.music.bitchord.ui.AppUi.host }.onFailure {
            com.music.bitchord.ui.AppUi.install(object : com.music.bitchord.ui.AppUiHost {
                override val swipeToPlayNext = kotlinx.coroutines.flow.MutableStateFlow(false)
                override val pinnedPlaylists = kotlinx.coroutines.flow.MutableStateFlow(emptyList<String>())
                override val librarySort = kotlinx.coroutines.flow.MutableStateFlow(com.music.bitchord.data.settings.LibrarySort.DEFAULT)
                @Composable
                override fun downloadedIds(): Set<String> = emptySet()
            })
        }
    }

    private fun scrolledShot(name: String, dark: Boolean, section: SettingsCategory?, target: String) {
        val oldTheme = AppSettings.themeMode.value
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        try {
            compose.setContent { BitChordTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { SettingsShot(section) }
            } }
            compose.onNodeWithText(target, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            // Bring the entire final row above the viewport edge, not just its text.
            compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
            compose.waitForIdle()
            compose.onNodeWithText(target, useUnmergedTree = true).assertIsDisplayed()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
        } finally { AppSettings.setThemeMode(oldTheme) }
    }
    @Composable
    internal fun SettingsShot(section: com.music.bitchord.ui.screens.SettingsCategory? = null, padding: PaddingValues = PaddingValues(0.dp)) {
        com.music.bitchord.ui.screens.SettingsScreen(
            windowWidth = 360.dp,
            signedIn = false,
            account = null,
            onSignIn = {},
            onSignOut = {},
            onAccountScrobbling = {},
            onEqualizer = {},
            onOpenReplay = {},
            onLyricsSources = {},
            onTranslationLanguage = {},
            onSources = {},
            onListenTogether = {},
            onWearOs = {},
            onSpotifyCanvasAuth = {},
            onAppLanguage = {},
            onEditSignatureApi = {},
            onConfirmBitPerfect = {},
            onConfirmFixedVolume = {},
            contentPadding = padding,
            section = section,
        )
    }

    @Test fun settings_main_bottom_light() = scrolledShot("settings_main_bottom_light", false, null, "Language")
    @Test fun settings_main_bottom_dark() = scrolledShot("settings_main_bottom_dark", true, null, "Language")
    @Test fun settings_playback_bottom_light() = scrolledShot("settings_playback_bottom_light", false, SettingsCategory.PLAYBACK, "Equalizer")
    @Test fun settings_playback_bottom_dark() = scrolledShot("settings_playback_bottom_dark", true, SettingsCategory.PLAYBACK, "Equalizer")
}
