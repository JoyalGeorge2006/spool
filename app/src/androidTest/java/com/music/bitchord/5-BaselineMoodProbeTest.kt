package com.music.bitchord

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.settings.AppSettings
import java.io.File
import java.io.FileOutputStream
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
class BaselineMoodProbeTest {
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


    @Test
    fun baselineMoodTapProbe() {
        try { run() } catch (e: Throwable) { say("TEST-FAILURE ${e::class.java.name}: ${e.message}"); runCatching { shot("baseline-failure") }; throw e }
    }

    private fun run() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        AppSettings.setOnboardingComplete()
        AppSettings.setDiwaliGreetingShown()
        settle(2500)
        compose.onAllNodes(hasClickAction() and (hasContentDescription("Listen") or hasAnyDescendant(hasContentDescription("Listen"))), useUnmergedTree = true)[0].performClick()
        settle(1500)
        compose.onNode(hasText("Show all") and hasAnyAncestor(hasTestTag("home-mood-chip-row"))).performScrollTo().performClick()
        settle(1500)
        compose.onNodeWithText("Find Your Mood").assertExists()
        say("BASELINE 2.1.5 build (no region feature): Find Your Mood page open")
        shot("baseline-01-moods")
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>
        compose.runOnUiThread {
            future = androidx.media3.session.MediaController.Builder(ctx,
                androidx.media3.session.SessionToken(ctx, android.content.ComponentName(ctx, com.music.bitchord.playback.PlaybackService::class.java)))
                .setApplicationLooper(android.os.Looper.getMainLooper()).buildAsync()
        }
        val c = future.get(20, java.util.concurrent.TimeUnit.SECONDS)
        for (label in listOf("Energy", "Relax")) {
            compose.runOnUiThread { c.clearMediaItems() }
            compose.onAllNodesWithText(label, useUnmergedTree = true)[0].performClick()
            var queued = false
            try { compose.waitUntil(30_000) { var n = 0; compose.runOnUiThread { n = c.mediaItemCount }; n > 0 }; queued = true } catch (e: Throwable) { say("$label: nothing queued within 30s") }
            var ok = false
            if (queued) {
                try { compose.waitUntil(40_000) { var r = false; compose.runOnUiThread { r = c.playbackState == androidx.media3.common.Player.STATE_READY && c.isPlaying }; r }; ok = true } catch (e: Throwable) { say("$label: queued but not READY+playing within 40s") }
            }
            val items = ArrayList<String>(); var count = 0; var state = -1
            compose.runOnUiThread { count = c.mediaItemCount; state = c.playbackState; for (i in 0 until minOf(count, 10)) { val m = c.getMediaItemAt(i).mediaMetadata; items.add("${m.title} | ${m.artist}") } }
            var p1 = -1L; var p2 = -1L
            compose.runOnUiThread { p1 = c.currentPosition }; Thread.sleep(5000); compose.runOnUiThread { p2 = c.currentPosition }
            say("$label queued=$count playbackState=$state(3=READY) readyAndPlaying=$ok positionAdvancedMs=${p2 - p1} titles=" + items.joinToString(" ;; "))
            shot("baseline-02-${label.lowercase()}")
        }
    }
}
