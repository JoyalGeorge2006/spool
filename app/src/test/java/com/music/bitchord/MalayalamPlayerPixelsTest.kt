package com.music.bitchord

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.*
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.PlayerTheme
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.sharedui.resources.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/** Malayalam production player composables with deterministic offline songs.
 * Fixture pixel proof, not installed playback/provider or real system-inset proof. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ml-rIN-w390dp-h844dp-mdpi")
class MalayalamPlayerPixelsTest {
    @get:Rule val compose = createComposeRule()
    private fun verify(dark: Boolean) {
        val locale = Locale.getDefault()
        val oldPane = AppSettings.lastPlayerScreen.value
        val oldTheme = AppSettings.playerTheme.value
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val host = fixture.installVideoStubHost()
        Locale.setDefault(Locale.forLanguageTag("ml-IN"))
        AppSettings.lastPlayerScreen.value = LastPlayerScreen.MAIN
        AppSettings.playerTheme.value = PlayerTheme.FLAT
        compose.mainClock.autoAdvance = false
        try {
            val labels = runBlocking { listOf(getString(Res.string.queue), getString(Res.string.spool_player_lyrics), getString(Res.string.spool_player_related)) }
            assertTrue("Malayalam resource environment must be active", labels.none { it in listOf("Queue", "Lyrics", "Related") })
            compose.setContent { BitChordTheme(darkTheme = dark) {
                Box(Modifier.width(390.dp).height(780.dp).testTag("ml-player")) {
                    fixture.PlayerShot(playing = false)
                }
            } }
            fun settle() { repeat(12) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() } }
            fun capture(name: String) {
                compose.onNodeWithTag("ml-player").captureRoboImage("build/outputs/roborazzi/ml_player_${dark}_$name.png",
                    roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
            }
            settle()
            compose.onNodeWithText("Midnight Rain").assertIsDisplayed()
            capture("main")
            val screen = compose.onNodeWithTag("ml-player").fetchSemanticsNode().boundsInRoot
            val visible = SemanticsMatcher("inside Malayalam player viewport") {
                val b = it.boundsInRoot
                b.width > 0f && b.height > 0f && screen.contains(b.center)
            }
            for ((index, label) in labels.withIndex()) {
                compose.onNode(hasText(label) and visible).assertIsDisplayed().performClick()
                settle()
                compose.onNode(hasText(label) and visible).assertIsSelected()
                compose.onNodeWithTag("player-section-header").assertIsDisplayed()
                when(index) {
                    0 -> compose.onNodeWithText("Track 1").assertIsDisplayed()
                    1 -> compose.onNodeWithText("First line of the song").assertIsDisplayed()
                    2 -> compose.onNodeWithText("Track 5").assertIsDisplayed()
                }
                capture(listOf("queue", "lyrics", "related")[index])
            }
        } finally {
            AppSettings.lastPlayerScreen.value = oldPane
            AppSettings.playerTheme.value = oldTheme
            PlayerPlatform.install(host)
            Locale.setDefault(locale)
        }
    }
    @Test fun light() = verify(false)
    @Test fun dark() = verify(true)
}
