package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class PlayerSectionViewportTest {
    @get:Rule val compose = createComposeRule()
    private fun probe(dark: Boolean, font: Float) {
        compose.mainClock.autoAdvance = false
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val host = fixture.installVideoStubHost()
        val oldTheme = AppSettings.themeMode.value
        val longTitle = "A long live recording title with featured artists and a longer subtitle"
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                    BitChordTheme(darkTheme = dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("viewport")) {
                            fixture.PlayerShot(titleText = longTitle, playing = false)
                        }
                    }
                }
            }
            repeat(10) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() }
            compose.onNodeWithTag("viewport").captureRoboImage("build/outputs/roborazzi/viewport_main_${dark}_${font}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
            compose.onNodeWithText("Lyrics").assertIsDisplayed().performClick()
            repeat(10) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() }
            compose.onNodeWithTag("viewport").captureRoboImage("build/outputs/roborazzi/viewport_section_${dark}_${font}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
            compose.onNodeWithText(longTitle).assertIsDisplayed()
            compose.onNodeWithText("Playing from Queue").assertDoesNotExist()
            compose.onNodeWithText("First line of the song").assertIsDisplayed()
            compose.onNodeWithText("Related").performClick()
            repeat(10) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() }
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val activePane = SemanticsMatcher("inside active section viewport") {
                val b = it.boundsInRoot
                b.width > 0f && b.height > 0f && viewport.contains(b.center)
            }
            compose.onNode(hasText("Track 5") and activePane).assertIsDisplayed()
            // Pager prewarms adjacent queue composition. It must not be visible in Related.
            compose.onAllNodesWithText("Track 1").fetchSemanticsNodes().forEachIndexed { index, _ ->
                compose.onAllNodesWithText("Track 1")[index].assertIsNotDisplayed()
            }
            compose.onNodeWithTag("viewport").captureRoboImage("build/outputs/roborazzi/viewport_related_${dark}_${font}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
        } finally {
            AppSettings.setThemeMode(oldTheme)
            PlayerPlatform.install(host)
        }
    }
    @Test fun smallLight() = probe(false, 1f)
    @Test fun smallDark() = probe(true, 1f)
    @Test fun largeTextLight() = probe(false, 2f)
    @Test fun largeTextDark() = probe(true, 2f)
}
