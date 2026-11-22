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

/** Actual secondary Settings destinations, bounded scroll; not installed navigation/IME proof. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class SettingsSecondaryViewportTest {
    @get:Rule val compose = createComposeRule()
    private fun probe(page: String, dark: Boolean) {
        V6ScreenshotTest().installUiHost()
        val old = AppSettings.themeMode.value
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        val name = "secondary_${page}_${dark}"
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    BitChordTheme(darkTheme = dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            when (page) {
                                "ACCOUNT_SIGNED_OUT" -> com.music.bitchord.ui.screens.AccountAndScrobblingScreen(signedIn = false, account = null, channelName = null, onSignIn = {}, onSwitchChannel = {}, onSignOut = {}, onOpenListenBrainzLogin = {}, onOpenLastfmLogin = {}, onOpenDiscord = {}, onOpenSpotify = {}, contentPadding = PaddingValues(0.dp))
                                "SOURCES" -> com.music.bitchord.ui.screens.SourcesScreen(contentPadding = PaddingValues(0.dp), onEditSource = {}, onChooseSourceQuality = {}, onExplainOutdatedAddon = {}, onEditWebDav = {}, onEditSmb = {}, onConfirmJioSaavn = {})
                                "LISTEN_SIGNED_OUT" -> com.music.bitchord.ui.screens.ListenTogetherScreen(signedIn = false, onSignIn = {}, contentPadding = PaddingValues(0.dp), onEditServer = {})
                                else -> com.music.bitchord.ui.screens.SettingsChoicePage(choice = com.music.bitchord.ui.screens.SettingsChoiceKind.valueOf(page), onChosen = {}, onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp))
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            if (page == "SOURCES" || page == "ACCOUNT_SIGNED_OUT") {
                val nodes = compose.onAllNodes(hasText(if (page == "SOURCES") com.music.bitchord.data.sources.SourceRegistry.configs.value.first { it.kind == com.music.bitchord.data.sources.SourceKind.YOUTUBE }.displayName else "Not signed in"), useUnmergedTree = true).fetchSemanticsNodes()
                check(nodes.isNotEmpty()) { "Missing responsive title in $page" }
                nodes.forEach { node ->
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    check(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
                    check(layouts.none { layout -> (0 until layout.lineCount).any(layout::isLineEllipsized) }) { "Title ellipsized in $page" }
                    check(node.boundsInRoot.width > 120f) { "Title width squeezed in $page: ${node.boundsInRoot}" }
                }
                println("SECONDARY_LAYOUT $page " + compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 12))
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
                    if (before >= max - 1f) break
                    check(step < 40) { "Secondary scroll did not reach end: $name $before/$max" }
                    list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, node.boundsInRoot.height * .65f) }
                    compose.waitForIdle()
                    val after = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                    check(after > before) { "Secondary scroll made no progress: $name $before -> $after" }
                    compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_position_${step.toString().padStart(2, '0')}.png")
                    step++
                }
            }
        } finally { AppSettings.setThemeMode(old) }
    }
    @Test fun audio_quality_wifi_false() = probe("AUDIO_QUALITY_WIFI", false)
    @Test fun audio_quality_wifi_true() = probe("AUDIO_QUALITY_WIFI", true)
    @Test fun audio_quality_cellular_false() = probe("AUDIO_QUALITY_CELLULAR", false)
    @Test fun audio_quality_cellular_true() = probe("AUDIO_QUALITY_CELLULAR", true)
    @Test fun download_quality_false() = probe("DOWNLOAD_QUALITY", false)
    @Test fun download_quality_true() = probe("DOWNLOAD_QUALITY", true)
    @Test fun automix_performance_false() = probe("AUTOMIX_PERFORMANCE", false)
    @Test fun automix_performance_true() = probe("AUTOMIX_PERFORMANCE", true)
    @Test fun bit_perfect_false() = probe("BIT_PERFECT", false)
    @Test fun bit_perfect_true() = probe("BIT_PERFECT", true)
    @Test fun font_false() = probe("FONT", false)
    @Test fun font_true() = probe("FONT", true)
    @Test fun account_signed_out_false() = probe("ACCOUNT_SIGNED_OUT", false)
    @Test fun account_signed_out_true() = probe("ACCOUNT_SIGNED_OUT", true)
    @Test fun sources_false() = probe("SOURCES", false)
    @Test fun sources_true() = probe("SOURCES", true)
    @Test fun listen_signed_out_false() = probe("LISTEN_SIGNED_OUT", false)
    @Test fun listen_signed_out_true() = probe("LISTEN_SIGNED_OUT", true)
}
