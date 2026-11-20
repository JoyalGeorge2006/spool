package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
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

/** Actual secondary Settings destinations, bounded scroll; not installed navigation/IME proof. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class SettingsTertiaryViewportTest {
    @get:Rule val compose = createComposeRule()
    private fun probe(page: String, dark: Boolean) {
        V6ScreenshotTest().installUiHost()
        val old = AppSettings.themeMode.value
        val oldEq = AppSettings.equalizerEnabled.value
        val oldMode = AppSettings.equalizerMode.value
        if (page.startsWith("EQUALIZER")) {
            AppSettings.setEqualizerEnabled(true)
            AppSettings.setEqualizerMode(if (page == "EQUALIZER_MANUAL") com.music.bitchord.data.settings.EqualizerMode.MANUAL else com.music.bitchord.data.settings.EqualizerMode.DYNAMIC)
        }
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        val name = "tertiary_${page}_${dark}"
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    BitChordTheme(darkTheme = dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            val haze = remember { dev.chrisbanes.haze.HazeState() }
                            when (page) {
                                "EQUALIZER", "EQUALIZER_MANUAL" -> com.music.bitchord.ui.screens.EqualizerScreen(contentPadding = PaddingValues(0.dp), onOpenPresets = {})
                                "PRESETS" -> com.music.bitchord.ui.screens.EqualizerPresetPage(onChosen = {}, contentPadding = PaddingValues(0.dp))
                                "LYRICS_SOURCES" -> com.music.bitchord.ui.components.LyricsSourcesDialog(hazeState = haze, onDismiss = {})
                                "APP_LANGUAGE" -> com.music.bitchord.ui.components.AppLanguageDialog(hazeState = haze, onDismiss = {})
                                "TRANSLATION_LANGUAGE" -> com.music.bitchord.ui.components.TranslationLanguageDialog(hazeState = haze, onDismiss = {})
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            if (page == "LYRICS_SOURCES" || page == "TRANSLATION_LANGUAGE") {
                val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                repeat(texts.fetchSemanticsNodes().size) { index ->
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    layouts.forEach { result ->
                        repeat(result.lineCount) { line -> check(!result.isLineEllipsized(line)) { "Ellipsized $page: ${result.layoutInput.text}" } }
                    }
                }
            }
            if (page == "LYRICS_SOURCES") {
                val handles = compose.onAllNodesWithContentDescription("Drag to reorder", useUnmergedTree = true).fetchSemanticsNodes()
                check(handles.size > 2)
                val pitches = handles.zipWithNext { a, b -> b.layoutInfo.coordinates.positionInRoot().y - a.layoutInfo.coordinates.positionInRoot().y }
                check(pitches.max() - pitches.min() < 2f) { "Variable lyrics drag pitch: $pitches" }
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
                    println("SECONDARY_POSITION $name step=$step value=$before max=$max")
                    if (before >= max - 1f || step >= (if (page == "LYRICS_SOURCES" || page == "EQUALIZER_MANUAL") 40 else 4)) break
                    println("TERTIARY_PARTIAL_SCROLL $name: at most four increments, not full language-list coverage")
                    list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, node.boundsInRoot.height * .65f) }
                    compose.waitForIdle()
                    val after = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                    check(after > before) { "Secondary scroll made no progress: $name $before -> $after" }
                    compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_position_${step.toString().padStart(2, '0')}.png")
                    step++
                }
            }
        } finally { AppSettings.setThemeMode(old); AppSettings.setEqualizerEnabled(oldEq); AppSettings.setEqualizerMode(oldMode) }
    }
    @Test fun equalizer_manual_false() = probe("EQUALIZER_MANUAL", false)
    @Test fun equalizer_manual_true() = probe("EQUALIZER_MANUAL", true)
    @Test fun equalizer_false() = probe("EQUALIZER", false)
    @Test fun equalizer_true() = probe("EQUALIZER", true)
    @Test fun presets_false() = probe("PRESETS", false)
    @Test fun presets_true() = probe("PRESETS", true)
    @Test fun lyrics_sources_false() = probe("LYRICS_SOURCES", false)
    @Test fun lyrics_sources_true() = probe("LYRICS_SOURCES", true)
    @Test fun app_language_false() = probe("APP_LANGUAGE", false)
    @Test fun app_language_true() = probe("APP_LANGUAGE", true)
    @Test fun translation_language_false() = probe("TRANSLATION_LANGUAGE", false)
    @Test fun translation_language_true() = probe("TRANSLATION_LANGUAGE", true)
}
