package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.PlayerTheme
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real full player and nested queue. Callback fixtures only, not service or provider playback proof. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PlayerGestureRegressionTest {
    @get:Rule val compose = createComposeRule()
    private fun visibleText(text: String): SemanticsNodeInteraction {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val matches = compose.onAllNodesWithText(text).fetchSemanticsNodes()
        println("VISIBLE_TEXT_PROBE text=$text root=$root bounds=${matches.map { it.boundsInRoot }}")
        return compose.onAllNodesWithText(text).filterToOne(SemanticsMatcher("inside visible player viewport") {
            val bounds = it.boundsInRoot
            bounds.width > 0f && bounds.height > 0f && root.contains(bounds.center)
        })
    }
    private fun probe(dark: Boolean, queuePane: Boolean) {
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val previousHost = fixture.installVideoStubHost()
        val oldPane = AppSettings.lastPlayerScreen.value
        val oldTheme = AppSettings.themeMode.value
        val oldPlayerTheme = AppSettings.playerTheme.value
        var next = 0
        var previous = 0
        var collapsed = 0
        val removed = mutableListOf<Int>()
        AppSettings.playerTheme.value = PlayerTheme.FLAT
        AppSettings.lastPlayerScreen.value = if (queuePane) LastPlayerScreen.QUEUE else LastPlayerScreen.MAIN
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        try {
            compose.setContent { BitChordTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    fixture.PlayerShot(nextRequest = { next++ }, previousRequest = { previous++ },
                        removeRequest = { removed += it }, collapseRequest = { collapsed++ })
                }
            } }
            compose.waitForIdle()
            if (queuePane) {
                visibleText("Queue").performClick()
                compose.waitForIdle()
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                val visibleList = hasScrollToNodeAction() and SemanticsMatcher("visible queue list") {
                    val b = it.boundsInRoot
                    b.width > 0f && b.height > 0f && root.contains(b.center)
                }
                val lazy = compose.onNode(visibleList)
                lazy.performScrollToNode(hasText("Track 3"))
                compose.onNodeWithText("Track 3").assertIsDisplayed()
                val rowBefore = compose.onNodeWithText("Track 3").fetchSemanticsNode().boundsInRoot
                val rootBefore = compose.onRoot().fetchSemanticsNode().boundsInRoot
                println("QUEUE_TOUCH_GEOMETRY row=$rowBefore root=$rootBefore")
                compose.onRoot().captureRoboImage("build/outputs/roborazzi/gestures_queue_before_${if (dark) "dark" else "light"}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
                compose.onNodeWithText("Track 3").performTouchInput { swipeLeft() }
                compose.waitForIdle()
                compose.onRoot().captureRoboImage("build/outputs/roborazzi/gestures_queue_after_left_${if (dark) "dark" else "light"}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
                compose.mainClock.advanceTimeBy(1200L)
                compose.waitForIdle()
                visibleText("Lyrics").assertIsSelected()
                assertTrue("page swipe must never remove queue entries", removed.isEmpty())
                compose.onNodeWithTag("player-section-pager").performTouchInput { swipeRight() }
                compose.mainClock.advanceTimeBy(1200L)
                compose.waitForIdle()
                visibleText("Queue").assertIsSelected()
                assertTrue("reverse page swipe must never remove queue entries", removed.isEmpty())
                lazy.performTouchInput { swipeUp() }
                compose.waitForIdle()
                lazy.performTouchInput { swipeDown() }
                compose.waitForIdle()
                assertEquals("row swipes must not skip the playing track", 0, next)
                assertEquals(0, previous)
                assertEquals("queue vertical scrolling must not collapse the player", 0, collapsed)
                // Lyrics intentionally tick every frame while playing. Bound
                // this navigation frame rather than auto-seeking global idle.
                compose.mainClock.autoAdvance = false
                visibleText("Lyrics").assertIsDisplayed().assertIsEnabled().performClick()
                // Pump layout between small frame advances: a page selection
                // launches its scroll after recomposition, not before it.
                repeat(30) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() }
                compose.onRoot().captureRoboImage("build/outputs/roborazzi/gestures_queue_lyrics_${if (dark) "dark" else "light"}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
                visibleText("First line of the song").assertIsDisplayed()
                visibleText("Queue").assertIsDisplayed().assertIsEnabled().performClick()
                repeat(10) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() }
                compose.mainClock.autoAdvance = true
                compose.onNode(visibleList).performScrollToNode(hasText("Track 6"))
                compose.onNodeWithText("Track 6").assertIsDisplayed()
            } else {
                val title = compose.onNodeWithText("Midnight Rain", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                val y = title.center.y - root.top
                compose.onRoot().performTouchInput { swipe(Offset(width.toFloat() * .85f, y), Offset(width.toFloat() * .15f, y), 300) }
                compose.waitForIdle()
                assertEquals(1, next)
                assertEquals(0, previous)
                compose.onRoot().performTouchInput { swipe(Offset(width.toFloat() * .15f, y), Offset(width.toFloat() * .85f, y), 300) }
                compose.waitForIdle()
                assertEquals(1, previous)
                assertEquals(1, next)
                assertTrue(removed.isEmpty())
            }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/gestures_${if (queuePane) "queue" else "player"}_${if (dark) "dark" else "light"}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
        } finally {
            AppSettings.lastPlayerScreen.value = oldPane
            AppSettings.playerTheme.value = oldPlayerTheme
            AppSettings.setThemeMode(oldTheme)
            PlayerPlatform.install(previousHost)
        }
    }
    private fun lyricsProof(dark: Boolean) {
        // Active lyrics have an intentional withFrameNanos clock. Letting the
        // test auto-advance until global idle never ends that clock. Freeze a
        // bounded frame for this visual check; do not disable lyric playback
        // or turn a capture into a playback/seek acceptance test.
        compose.mainClock.autoAdvance = false
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val previousHost = fixture.installVideoStubHost()
        val oldPane = AppSettings.lastPlayerScreen.value
        val oldTheme = AppSettings.themeMode.value
        val oldPlayerTheme = AppSettings.playerTheme.value
        AppSettings.playerTheme.value = PlayerTheme.FLAT
        AppSettings.lastPlayerScreen.value = LastPlayerScreen.LYRICS
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        try {
            compose.setContent { BitChordTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { fixture.PlayerShot() }
            } }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(2_000L)
            compose.waitForIdle()
            visibleText("Lyrics").performClick()
            repeat(10) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() }
            visibleText("First line of the song").assertIsDisplayed()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/lyrics_clip_probe_${if (dark) "dark" else "light"}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
        } finally {
            AppSettings.lastPlayerScreen.value = oldPane
            AppSettings.playerTheme.value = oldPlayerTheme
            AppSettings.setThemeMode(oldTheme)
            PlayerPlatform.install(previousHost)
        }
    }
    @Test fun lyrics_clip_light() = lyricsProof(false)
    @Test fun lyrics_clip_dark() = lyricsProof(true)
    @Test fun queue_four_directions_light() = probe(false, true)
    @Test fun queue_four_directions_dark() = probe(true, true)
    @Test fun player_skip_both_directions_light() = probe(false, false)
    @Test fun player_skip_both_directions_dark() = probe(true, false)
}
