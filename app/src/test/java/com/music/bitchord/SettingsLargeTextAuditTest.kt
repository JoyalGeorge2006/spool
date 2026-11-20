package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Small-window 2x-font component renders. No MainActivity/IME/device claim. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class SettingsLargeTextAuditTest {
    @get:Rule val compose = createComposeRule()
    private fun probe(category: com.music.bitchord.ui.screens.SettingsCategory?, dark: Boolean) {
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val old = AppSettings.themeMode.value
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        val name = "settings_large_${category?.name ?: "MAIN"}_${dark}"
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    BitChordTheme(darkTheme = dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { fixture.SettingsShot(category) }
                    }
                }
            }
            compose.waitForIdle()
            if (category == com.music.bitchord.ui.screens.SettingsCategory.DOWNLOADS) {
                println("DOWNLOAD_LAYOUT " + compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 12))
                val badges = compose.onAllNodesWithText("BLOCKING", useUnmergedTree = true).fetchSemanticsNodes()
                check(badges.isNotEmpty()) { "Metered fixture must render BLOCKING badge" }
                badges.forEach {
                    check(it.boundsInRoot.width > 40f && it.boundsInRoot.height < 60f) { "Blocking badge collapsed: ${it.boundsInRoot}" }
                }
                val wifi = compose.onNodeWithText("Download over Wi-Fi only", useUnmergedTree = true).fetchSemanticsNode()
                val row = requireNotNull(wifi.parent)
                println("DOWNLOAD_WIFI_PARENT ${row.boundsInRoot}")
            }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_top.png")
            val lists = compose.onAllNodes(hasScrollAction())
            if (lists.fetchSemanticsNodes().isNotEmpty()) {
                val list = lists.onFirst()
                var step = 0
                while (true) {
                    val node = list.fetchSemanticsNode()
                    val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
                    val before = range.value()
                    val max = range.maxValue()
                    println("SETTINGS_POSITION $name step=$step value=$before max=$max")
                    if (before >= max - 1f) break
                    check(step < 40) { "Settings scroll did not reach end: $name $before/$max" }
                    val distance = node.boundsInRoot.height * 0.65f
                    list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
                    compose.waitForIdle()
                    val after = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                    check(after > before) { "Settings scroll made no progress: $name $before -> $after" }
                    compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_position_${step.toString().padStart(2, '0')}.png")
                    step++
                }
            }
        } finally { AppSettings.setThemeMode(old) }
    }
    @Test fun main_false() = probe(null, false)
    @Test fun main_true() = probe(null, true)
    @Test fun downloads_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.DOWNLOADS, false)
    @Test fun downloads_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.DOWNLOADS, true)
    @Test fun playback_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.PLAYBACK, false)
    @Test fun playback_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.PLAYBACK, true)
    @Test fun content_preferences_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.CONTENT_PREFERENCES, false)
    @Test fun content_preferences_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.CONTENT_PREFERENCES, true)
    @Test fun appearance_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.APPEARANCE, false)
    @Test fun appearance_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.APPEARANCE, true)
    @Test fun performance_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.PERFORMANCE, false)
    @Test fun performance_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.PERFORMANCE, true)
    @Test fun local_music_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.LOCAL_MUSIC, false)
    @Test fun local_music_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.LOCAL_MUSIC, true)
    @Test fun storage_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.STORAGE, false)
    @Test fun storage_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.STORAGE, true)
    @Test fun your_data_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.YOUR_DATA, false)
    @Test fun your_data_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.YOUR_DATA, true)
    @Test fun miscellaneous_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.MISCELLANEOUS, false)
    @Test fun miscellaneous_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.MISCELLANEOUS, true)
    @Test fun language_false() = probe(com.music.bitchord.ui.screens.SettingsCategory.LANGUAGE, false)
    @Test fun language_true() = probe(com.music.bitchord.ui.screens.SettingsCategory.LANGUAGE, true)
}
