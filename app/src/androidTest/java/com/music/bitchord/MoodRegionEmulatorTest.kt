package com.music.bitchord

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.MusicRegion
import com.music.bitchord.data.settings.AppSettings
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Real app on a real emulator: the Music region page (country, language
 * chips), the region pill on Find Your Mood, per-country memory, and a real
 * mood tap against the live service with the resulting queue written out.
 */
@RunWith(JUnit4::class)
class MoodRegionEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>(
        effectContext = kotlinx.coroutines.test.StandardTestDispatcher(),
    )

    private val log = StringBuilder()
    private fun say(s: String) { println("MOOD-PROOF $s"); log.appendLine(s) }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        Thread.sleep(800)
        val bmp: Bitmap? = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        check(bmp != null) { "no OS screenshot for $name" }
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val out = File(dir, "$name.png")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT-SAVED ${out.absolutePath} bytes=${out.length()}")
    }

    private fun settle(ms: Long = 1200) { compose.waitForIdle(); Thread.sleep(ms); compose.waitForIdle() }

    private fun textShown(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun regionChipsPillAndRealMoodPlayback() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        AppSettings.setOnboardingComplete()
        AppSettings.setDiwaliGreetingShown()
        settle(2500)
        compose.onAllNodes(hasClickAction() and (hasContentDescription("Listen") or hasAnyDescendant(hasContentDescription("Listen"))), useUnmergedTree = true)[0].performClick()
        settle(1500)
        AppSettings.setMusicCountry("IN")
        settle()
        compose.onNode(hasText("Show all") and hasAnyAncestor(hasTestTag("home-mood-chip-row"))).performScrollTo().performClick()
        settle(1500)
        compose.onNodeWithText("Find Your Mood").assertExists()
        say("phone device languages=" + AppSettings.musicRegion.value.deviceLanguages + " country=" + MusicRegion.country + " active=" + MusicRegion.activeLanguages().map { it.id })
        shot("region-01-moods-pill-unconfirmed")

        // Open the region page through the pill.
        compose.onNode(hasText("India", substring = true) and hasClickAction(), useUnmergedTree = true).performClick()
        settle(1500)
        assertTrue("confirmation prompt shown", textShown("Choose the languages you listen to"))
        for (l in listOf("Malayalam", "Tamil", "Hindi", "Telugu", "Kannada", "English")) assertTrue("$l chip for India", textShown(l))
        assertTrue("no Spanish for India", !textShown("Spanish"))
        shot("region-02-india-chips-unconfirmed")
        compose.onNodeWithText("Malayalam").performClick(); settle(600)
        compose.onNodeWithText("Tamil").performClick(); settle(600)
        assertEquals(setOf("malayalam", "tamil"), MusicRegion.activeLanguages().map { it.id }.toSet())
        shot("region-03-india-malayalam-tamil-selected")

        // Switch to the US: US languages only, then back: Indian choice is kept.
        AppSettings.setMusicCountry("US"); settle(1200)
        for (l in listOf("English", "Spanish", "Hindi", "Korean")) assertTrue("$l chip for US", textShown(l))
        assertTrue("no Malayalam for US", !textShown("Malayalam"))
        shot("region-04-united-states-chips")
        AppSettings.setMusicCountry("IN"); settle(1200)
        assertEquals(setOf("malayalam", "tamil"), MusicRegion.activeLanguages().map { it.id }.toSet())
        shot("region-05-india-selection-kept")
        compose.onNodeWithText("Done").performClick(); settle(1500)
        assertTrue("pill shows India and Malayalam", textShown("India") && textShown("Malayalam"))
        shot("region-06-moods-pill-confirmed")

        // Real mood taps against the live service.
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>
        compose.runOnUiThread {
            future = androidx.media3.session.MediaController.Builder(ctx,
                androidx.media3.session.SessionToken(ctx, android.content.ComponentName(ctx, com.music.bitchord.playback.PlaybackService::class.java)))
                .setApplicationLooper(android.os.Looper.getMainLooper()).buildAsync()
        }
        val c = future.get(20, java.util.concurrent.TimeUnit.SECONDS)
        var played = 0
        for (label in listOf("Energy", "Sleep", "Relax", "Party", "Love")) {
            val grid = compose.onAllNodes(hasScrollToNodeAction() and hasAnyDescendant(hasText("Find Your Mood")), useUnmergedTree = true)[0]
            grid.performScrollToNode(hasText(label)); settle(600)
            compose.onAllNodesWithText(label, useUnmergedTree = true)[0].performClick()
            var ok = false
            try {
                compose.waitUntil(45_000) {
                    var ready = false
                    compose.runOnUiThread { ready = c.mediaItemCount > 0 && c.playbackState == androidx.media3.common.Player.STATE_READY && c.isPlaying && c.playerError == null }
                    ready
                }
                ok = true
            } catch (e: Throwable) { say("$label: no playing queue within 45s: ${e.message}") }
            val items = ArrayList<String>()
            compose.runOnUiThread {
                for (i in 0 until minOf(c.mediaItemCount, 12)) {
                    val m = c.getMediaItemAt(i).mediaMetadata
                    items.add("${m.title} | ${m.artist}")
                }
            }
            var p1 = -1L; var p2 = -1L; var vol = -1f; var mime = ""
            compose.runOnUiThread { p1 = c.currentPosition }
            Thread.sleep(5000)
            compose.runOnUiThread { p2 = c.currentPosition; vol = c.volume; mime = c.currentMediaItem?.localConfiguration?.mimeType.orEmpty() }
            val advanced = ok && p2 - p1 >= 3000
            say("$label queued=${c.mediaItemCount} playing=$ok positionAdvancedMs=${p2 - p1} (audible playback needs this >= 3000) volume=$vol first=" + items.joinToString(" ;; "))
            if (advanced) played++
            settle(1500)
            shot("region-07-mood-${label.lowercase()}-playing")
            compose.runOnUiThread { c.pause() }
        }
        File(ctx.getExternalFilesDir(null), "mood-proof.txt").writeText(log.toString())
        println("SHOT-SAVED ${File(ctx.getExternalFilesDir(null), "mood-proof.txt").absolutePath} bytes=${log.length}")
        assertTrue("at least one real mood advanced playback position", played > 0)
    }
}
