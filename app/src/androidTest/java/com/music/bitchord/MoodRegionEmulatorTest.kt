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
    /** Copy the proof files somewhere the host shell can still read after the test app is uninstalled. */
    private fun keep() {
        runCatching {
            val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("sh -c 'mkdir -p /data/local/tmp/mood && cp -f ${dir}/* /data/local/tmp/mood/ && chmod -R a+rw /data/local/tmp/mood'")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }
    }

    private fun say(s: String) {
        println("MOOD-PROOF $s"); log.appendLine(s)
        runCatching {
            val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            File(dir, "mood-proof.txt").appendText(s + "\n")
        }
        keep()
    }

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
        keep()
    }

    private fun settle(ms: Long = 1200) { compose.waitForIdle(); Thread.sleep(ms); compose.waitForIdle() }

    private fun textShown(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun regionChipsPillAndRealMoodPlayback() {
        try { runProof() } catch (e: Throwable) { say("TEST-FAILURE ${e::class.java.name}: ${e.message}"); runCatching { shot("region-failure") }; throw e }
    }

    private fun runProof() {
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
        say("STEP pill screenshot taken")

        // Open the region page through the pill.
        compose.onNode(hasText("India", substring = true), useUnmergedTree = true).performClick()
        settle(1500)
        say("STEP pill clicked")
        assertTrue("confirmation prompt shown", textShown("Choose the languages you listen to"))
        for (l in listOf("Malayalam", "Tamil", "Hindi", "Telugu", "Kannada", "English")) assertTrue("$l chip for India", textShown(l))
        assertTrue("no Spanish for India", !textShown("Spanish"))
        shot("region-02-india-chips-unconfirmed")
        say("STEP chips asserted")
        compose.onNodeWithText("Malayalam").performClick(); settle(600)
        compose.onNodeWithText("Tamil").performClick(); settle(600)
        // The first tap starts from the phone default shown selected (English), so it stays until untapped.
        assertEquals(setOf("malayalam", "tamil", "english"), MusicRegion.activeLanguages().map { it.id }.toSet())
        shot("region-03a-india-ml-ta-with-phone-english")
        compose.onNodeWithText("English").performClick(); settle(600)
        assertEquals(setOf("malayalam", "tamil"), MusicRegion.activeLanguages().map { it.id }.toSet())
        shot("region-03-india-malayalam-tamil-selected")
        say("STEP ml+ta selected")

        // Switch to the US: US languages only, then back: Indian choice is kept.
        AppSettings.setMusicCountry("US"); settle(1200)
        for (l in listOf("English", "Spanish", "Hindi", "Korean")) assertTrue("$l chip for US", textShown(l))
        assertTrue("no Malayalam for US", !textShown("Malayalam"))
        shot("region-04-united-states-chips")
        AppSettings.setMusicCountry("IN"); settle(1200)
        assertEquals(setOf("malayalam", "tamil"), MusicRegion.activeLanguages().map { it.id }.toSet())
        shot("region-05-india-selection-kept")
        say("STEP country switch ok")
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
        say("STEP controller connected")
        var played = 0
        for (label in listOf("Energy", "Sleep", "Relax", "Party", "Love")) {
            runCatching {
                compose.onAllNodes(hasScrollToNodeAction(), useUnmergedTree = true)[0].performScrollToNode(hasText(label))
            }.onFailure { say("$label: scroll-to failed: ${it.message}") }
            settle(600)
            // Keep the tile clear of the bottom navigation bar before tapping it.
            runCatching {
                val rootH = compose.onRoot().fetchSemanticsNode().size.height
                val b = compose.onAllNodesWithText(label, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
                say("$label tile bounds top=${b.top} bottom=${b.bottom} rootHeight=$rootH")
                if (b.bottom > rootH - 450) {
                    compose.onAllNodes(hasScrollToNodeAction(), useUnmergedTree = true)[0]
                        .performTouchInput { swipe(androidx.compose.ui.geometry.Offset(centerX, height * 0.8f), androidx.compose.ui.geometry.Offset(centerX, height * 0.5f), 400) }
                    settle(800)
                    val b2 = compose.onAllNodesWithText(label, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
                    say("$label tile bounds after nudge top=${b2.top} bottom=${b2.bottom}")
                }
            }.onFailure { say("$label: bounds/nudge failed: ${it.message}") }
            compose.runOnUiThread { c.clearMediaItems() }
            val tile = compose.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().size
            if (tile == 0) { say("$label: no such tile on the page"); continue }
            compose.onAllNodesWithText(label, useUnmergedTree = true)[0].performClick()
            var queued = false
            try {
                compose.waitUntil(30_000) {
                    var n = 0
                    compose.runOnUiThread { n = c.mediaItemCount }
                    n > 0
                }
                queued = true
            } catch (e: Throwable) { say("$label: nothing queued within 30s") }
            var ok = false
            if (queued) {
                try {
                    compose.waitUntil(40_000) {
                        var ready = false
                        compose.runOnUiThread { ready = c.playbackState == androidx.media3.common.Player.STATE_READY && c.isPlaying }
                        ready
                    }
                    ok = true
                } catch (e: Throwable) { say("$label: queued but not READY+playing within 40s") }
            }
            val items = ArrayList<String>()
            var count = 0; var state = -1; var err = ""
            compose.runOnUiThread {
                count = c.mediaItemCount; state = c.playbackState; err = c.playerError?.message.orEmpty()
                for (i in 0 until minOf(c.mediaItemCount, 20)) {
                    val m = c.getMediaItemAt(i).mediaMetadata
                    items.add("${m.title} | ${m.artist}")
                }
            }
            var p1 = -1L; var p2 = -1L; var vol = -1f
            compose.runOnUiThread { p1 = c.currentPosition }
            Thread.sleep(5000)
            compose.runOnUiThread { p2 = c.currentPosition; vol = c.volume }
            val advanced = ok && p2 - p1 >= 3000
            say("STEP $label tapped")
            say("$label queued=$count playbackState=$state(3=READY) playerError=$err readyAndPlaying=$ok positionAdvancedMs=${p2 - p1} (position moving is not proof of audible audio) volume=$vol titles=" + items.joinToString(" ;; "))
            if (advanced) played++
            settle(1500)
            shot("region-07-mood-${label.lowercase()}-playing")
            compose.runOnUiThread { c.pause() }
        }
        say("SUMMARY moods with advancing position: $played of 5")
    }
}
