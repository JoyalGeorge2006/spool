package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.PlayerTheme
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Bottom-only viewport evidence. Fixture content, no external actions or locale changes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LowerLayoutScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private fun shot(dark: Boolean, page: String) {
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val oldTheme = AppSettings.themeMode.value
        val oldPane = AppSettings.lastPlayerScreen.value
        val oldPlayerTheme = AppSettings.playerTheme.value
        val previousHost = if (page == "queue") fixture.installVideoStubHost() else null
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        AppSettings.lastPlayerScreen.value = LastPlayerScreen.QUEUE
        AppSettings.playerTheme.value = PlayerTheme.FLAT
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = when (page) {
            "language" -> app.getString(com.music.bitchord.R.string.korean)
            "listen" -> app.getString(com.music.bitchord.R.string.listen_together_custom_server_footer)
            "playlist" -> "June Harbor"
            else -> "Track 6"
        }
        try {
            compose.setContent { BitChordTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (page) {
                        "language" -> com.music.bitchord.ui.components.AppLanguageDialog(remember { dev.chrisbanes.haze.HazeState() }, {})
                        "listen" -> com.music.bitchord.ui.screens.ListenTogetherScreen(signedIn = false, onSignIn = {}, contentPadding = PaddingValues(0.dp), onEditServer = {})
                        "playlist" -> fixture.DetailShot(fixture.playlistPage())
                        else -> fixture.PlayerShot()
                    }
                }
            } }
            compose.waitForIdle()
            if (page == "queue") {
                compose.onNodeWithText("Queue").assertIsDisplayed().performClick()
                compose.mainClock.advanceTimeBy(800L)
                compose.waitForIdle()
            }
            val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val vertical = SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
            val onScreen = SemanticsMatcher("visible target") { viewport.contains(it.boundsInRoot.center) }
            val lazy = compose.onAllNodes(hasScrollToNodeAction() and vertical and SemanticsMatcher("visible page list") {
                val b = it.boundsInRoot
                b.width > 0f && b.height > 0f && viewport.contains(b.center)
            })
            if (lazy.fetchSemanticsNodes().isNotEmpty()) {
                lazy.onLast().performScrollToNode(hasText(target, substring = true))
                repeat(2) { lazy.onLast().performTouchInput { swipeUp() }; compose.waitForIdle() }
            } else {
                compose.onAllNodesWithText(target, substring = true, useUnmergedTree = true).onLast().performScrollTo()
                compose.onNode(hasScrollAction() and vertical and onScreen).performTouchInput { swipeUp() }
            }
            compose.waitForIdle()
            compose.onNode(hasText(target, substring = true) and onScreen, useUnmergedTree = true).assertIsDisplayed()
            compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/lower_${page}_${if (dark) "dark" else "light"}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
        } finally {
            AppSettings.setThemeMode(oldTheme)
            AppSettings.lastPlayerScreen.value = oldPane
            AppSettings.playerTheme.value = oldPlayerTheme
            if (previousHost != null) com.music.bitchord.ui.player.PlayerPlatform.install(previousHost)
        }
    }
    @Test fun lower_language_light() = shot(false, "language")
    @Test fun lower_language_dark() = shot(true, "language")
    @Test fun lower_listen_light() = shot(false, "listen")
    @Test fun lower_listen_dark() = shot(true, "listen")
    @Test fun lower_playlist_light() = shot(false, "playlist")
    @Test fun lower_playlist_dark() = shot(true, "playlist")
    @Test fun lower_queue_light() = shot(false, "queue")
    @Test fun lower_queue_dark() = shot(true, "queue")
}
