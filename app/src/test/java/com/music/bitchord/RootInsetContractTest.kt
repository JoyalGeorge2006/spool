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

/** Real screen/chrome components with production inset values, not MainActivity integration.
 * The reduced-height case models a resized viewport. It does NOT prove platform IME behavior.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class RootInsetContractTest {
    @get:Rule val compose = createComposeRule()
    @OptIn(ExperimentalMaterial3Api::class)
    private fun probe(page: Int, font: Float, dark: Boolean, resized: Boolean = false, playing: Boolean = true) {
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val oldTheme = AppSettings.themeMode.value
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        val names = listOf("Listen", "Explore", "Library", "Search")
        val target = listOf("Crimson Sky 1", "Jazz", "Golden Hour Drive", "Romance")[page]
        val name = "root_contract_${page}_${font}_${dark}_${resized}_${playing}"
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                    BitChordTheme(darkTheme = dark) {
                        val haze = remember { dev.chrisbanes.haze.HazeState() }
                        var chromeHeight by remember { mutableStateOf(0) }
                        Box(Modifier.fillMaxWidth().height(if (resized) 380.dp else 640.dp)
                            .background(MaterialTheme.colorScheme.background).testTag("contract-root")) {
                            val padding = PaddingValues(top = 32.dp, bottom = maxOf(if (playing) 210.dp else 140.dp, with(LocalDensity.current) { chromeHeight.toDp() } + 16.dp))
                            when (page) {
                                0 -> fixture.HomeWithChrome(padding, chrome = false, floatingHeader = true)
                                1 -> fixture.ExploreWithChrome(padding, chrome = false, floatingHeader = true)
                                2 -> fixture.LibraryWithChrome(padding, chrome = false, floatingHeader = true)
                                else -> fixture.SearchWithChrome(padding, chrome = false, floatingHeader = true)
                            }
                            FrostedTopBar(title = "Fixed title absent", controlsOnly = true, statusBarInsets = WindowInsets(top=32.dp), scrolled = { true },
                                modifier = Modifier.align(Alignment.TopCenter).testTag("contract-header"),
                                actions = { FloatingHistoryProfileCapsule(null, {}, {}) })
                            Column(Modifier.align(Alignment.BottomCenter).widthIn(max = FLOATING_BAR_MAX_WIDTH)
                                .fillMaxWidth().onSizeChanged { chromeHeight = it.height }.testTag("contract-bars"), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (playing) {
                                    MiniPlayer(song = Song(videoId = "fixture", title = "A long title in the persistent mini-player", artist = "Nova Rae", thumbnailUrl = null),
                                        isPlaying = false, isLoading = false, hazeState = haze,
                                        onPlayPause = {}, onNext = {}, onPrevious = {}, onExpand = {})
                                    Spacer(Modifier.height(10.dp))
                                }
                                FloatingBottomBar(tabs = listOf(BottomTab(names[0], BitChordIcons.HomeOutline),
                                    BottomTab(names[1], BitChordIcons.Explore), BottomTab(names[2], BitChordIcons.Library),
                                    BottomTab(names[3], BitChordIcons.Search)), selectedIndex = page, onTabSelected = {}, hazeState = haze)
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            if (page == 0 || page == 3) {
                val field = if (page == 3) compose.onNode(hasSetTextAction()) else
                    compose.onNode(hasText("Songs, artists, albums") and hasClickAction())
                val fieldBounds = field.fetchSemanticsNode().boundsInRoot
                val capsuleBottom = compose.onNodeWithContentDescription("Listening history").fetchSemanticsNode().boundsInRoot.bottom
                assertTrue("search field clears floating capsule by at least 12dp: $fieldBounds vs $capsuleBottom", fieldBounds.top >= capsuleBottom + 12f)
            }
            compose.onNodeWithTag("contract-root").captureRoboImage("build/outputs/roborazzi/${name}_top.png")
            val lists = compose.onAllNodes(hasScrollToNodeAction())
            lists.onFirst().performScrollToNode(hasText(target))
            repeat(2) { lists.onFirst().performTouchInput { swipeUp() }; compose.waitForIdle() }
            val root = compose.onNodeWithTag("contract-root").fetchSemanticsNode().boundsInRoot
            val bars = compose.onNodeWithTag("contract-bars").fetchSemanticsNode().boundsInRoot
            val item = compose.onAllNodesWithText(target, useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("contract-root").captureRoboImage("build/outputs/roborazzi/${name}_end.png")
            assertTrue("last content clears actual chrome: $item vs $bars", item.bottom <= bars.top + 1f)
            assertTrue("last content stays in viewport", item.top >= root.top && item.bottom <= root.bottom)
        } finally { AppSettings.setThemeMode(oldTheme) }
    }
    @Test fun listenSmall() = probe(0, 1f, false)
    @Test fun exploreSmall() = probe(1, 1f, false)
    @Test fun librarySmall() = probe(2, 1f, false)
    @Test fun searchSmall() = probe(3, 1f, false)
    @Test fun listenLargeText() = probe(0, 2f, true)
    @Test fun exploreLargeText() = probe(1, 2f, true)
    @Test fun libraryLargeText() = probe(2, 2f, true)
    @Test fun searchLargeText() = probe(3, 2f, true)
    @Test fun searchResizedViewport() = probe(3, 1f, false, resized = true)
    @Test fun searchWithoutMiniPlayer() = probe(3, 2f, true, playing = false)
}
