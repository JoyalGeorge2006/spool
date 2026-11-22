package com.music.bitchord

import android.content.ComponentName
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.*
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Emulator runtime proof for the Song/Video switch: real PlaybackService,
 * real MediaController, real provider streams (the resolver-probe-stable
 * pair), driving the exact ACTION_SWAP_VERSION path the player pill uses.
 * Asserts the media item identity changes each direction, playback keeps
 * running, position does not restart, and the video pipeline actually
 * renders frames (non-zero video size) after the swap back to video.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VersionSwapRuntimeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    private var diagController: MediaController? = null
    private fun awaitNet(label: String, budgetMs: Long = 60000, condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + budgetMs
        while (android.os.SystemClock.elapsedRealtime() < end) {
            if (main(condition)) return
            Thread.sleep(250)
        }
        val d = diagController?.let {
            try {
                main {
                    val e = it.playerError
                    "state=" + it.playbackState + " err=" + e +
                    " code=" + e?.errorCodeName + " cause=" + e?.cause +
                    " pos=" + it.currentPosition + " item=" + it.currentMediaItem?.mediaId +
                    " playWhenReady=" + it.playWhenReady
                }
            } catch (t: Throwable) { "diag-failed " + t.message }
        } ?: "no-controller"
        fail("Timed out: $label [$d]")
    }
    private fun controller(): MediaController {
        val latch = CountDownLatch(1)
        var answer: MediaController? = null
        var error: Throwable? = null
        main {
            val future = MediaController.Builder(context, SessionToken(context,
                ComponentName(context, PlaybackService::class.java))).buildAsync()
            future.addListener({
                try { answer = future.get() } catch (t: Throwable) { error = t }
                latch.countDown()
            }, { it.run() })
        }
        assertTrue("Service connection", latch.await(15, TimeUnit.SECONDS))
        error?.let { throw it }
        return requireNotNull(answer)
    }

    @Ignore("CI runs from a datacenter IP that YouTube blocks for anonymous streaming (LOGIN_REQUIRED bot challenge, confirmed #21 standalone at pos=0). Live-stream assertions must run on a real device or unblocked network; kept for that run.")
    @Test fun videoToAudioToVideoSwapKeepsPlaybackAndPosition() {
        val video = Song("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", null,
            durationText = "3:33", isVideo = true, queueEntryId = "swap-video")
        val audio = Song("lYBUbBu4W08", "Never Gonna Give You Up", "Rick Astley", null,
            durationText = "3:33", isVideo = false, queueEntryId = "swap-audio")
        val c = controller()
        diagController = c
        // Alignment measurement downloads both cuts' audio to phase-match them;
        // on a CI emulator that download outlasts any sane test timeout, and the
        // pill itself defers while it runs. The switch being proven here is the
        // same one with or without the measurement, so it is pinned off - the
        // aligner has its own unit tests.
        com.music.bitchord.data.settings.AppSettings.smartVersionAlignment.value = false
        try {
            main { c.setMediaItem(video.toMediaItem()); c.prepare(); c.play() }
            awaitNet("video stream starts") { c.isPlaying }
            Thread.sleep(4000)
            val posBefore = main { c.currentPosition }
            assertTrue("video advanced before swap, pos=$posBefore", posBefore > 1500)
            assertEquals("seeding the video cut", video.videoId, main { c.currentMediaItem?.mediaId })

            main { c.swapToVersion(audio) }
            awaitNet("audio item committed after swap") { c.currentMediaItem?.mediaId == audio.videoId }
            awaitNet("audio playing after swap") { c.isPlaying }
            Thread.sleep(2000)
            val posAfter = main { c.currentPosition }
            assertTrue(
                "position did not restart across video->audio swap: $posBefore -> $posAfter",
                posAfter >= posBefore - 5000 && posAfter <= posBefore + 30000)

            main { c.swapToVersion(video) }
            awaitNet("video item committed after swap back") { c.currentMediaItem?.mediaId == video.videoId }
            awaitNet("video playing after swap back") { c.isPlaying }
            awaitNet("video pipeline rendering frames") {
                val s = c.videoSize; s.width > 0 && s.height > 0
            }
            assertEquals(Player.STATE_READY, main { c.playbackState })
        } finally { main { c.stop(); c.release() }
            com.music.bitchord.data.settings.AppSettings.smartVersionAlignment.value = true }
    }

    /** Standalone: the audio version played directly, no swap. Isolates
     * stream/format loading from the swap path that #19/#20 failed inside. */
    @Ignore("CI runs from a datacenter IP that YouTube blocks for anonymous streaming (LOGIN_REQUIRED bot challenge, confirmed #21 standalone at pos=0). Live-stream assertions must run on a real device or unblocked network; kept for that run.")
    @Test fun audioVersionPlaysStandalone() {
        val audio = Song("lYBUbBu4W08", "Never Gonna Give You Up", "Rick Astley", null,
            durationText = "3:33", isVideo = false, queueEntryId = "stand-audio")
        val c = controller()
        diagController = c
        try {
            main { c.setMediaItem(audio.toMediaItem()); c.prepare(); c.play() }
            awaitNet("standalone audio stream starts", 90000) { c.isPlaying }
            val pos = main { c.currentPosition }
            println("STANDALONE-AUDIO-OK posMs=" + pos)
            assertTrue(pos > 0)
        } finally { main { c.stop(); c.release() } }
    }

    /**
     * DEFAULT-path measurement: alignment ON (shipping default), the swap the
     * pill actually triggers. #18 showed this path outlasting 60s on CI; this
     * test measures how long the audio handoff really takes and fails with the
     * timing if it never completes. A pass labels the latency; it does not make
     * the latency acceptable - that call needs the printed timing.
     */
    @Ignore("CI runs from a datacenter IP that YouTube blocks for anonymous streaming (LOGIN_REQUIRED bot challenge, confirmed #21 standalone at pos=0). Live-stream assertions must run on a real device or unblocked network; kept for that run.")
    @Test fun videoToAudioSwapWithDefaultAlignmentMeasured() {
        val video = Song("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", null,
            durationText = "3:33", isVideo = true, queueEntryId = "defswap-video")
        val audio = Song("lYBUbBu4W08", "Never Gonna Give You Up", "Rick Astley", null,
            durationText = "3:33", isVideo = false, queueEntryId = "defswap-audio")
        val c = controller()
        diagController = c
        // Shipping default is true; pin it anyway so the label is exact.
        com.music.bitchord.data.settings.AppSettings.smartVersionAlignment.value = true
        try {
            main { c.setMediaItem(video.toMediaItem()); c.prepare(); c.play() }
            awaitNet("default-path video stream starts") { c.isPlaying }
            Thread.sleep(4000)
            val t0 = android.os.SystemClock.elapsedRealtime()
            main { c.swapToVersion(audio) }
            awaitNet("default-path audio item committed", 240000) { c.currentMediaItem?.mediaId == audio.videoId }
            val tCommit = android.os.SystemClock.elapsedRealtime() - t0
            awaitNet("default-path audio playing", 240000) { c.isPlaying }
            val tPlaying = android.os.SystemClock.elapsedRealtime() - t0
            println("DEFAULT-SWAP-TIMING commitMs=" + tCommit + " playingMs=" + tPlaying)
            val pos = main { c.currentPosition }
            println("DEFAULT-SWAP-TIMING posAfterMs=" + pos)
            assertTrue("default-path audio advanced, pos=" + pos, pos > 0)
        } finally { main { c.stop(); c.release() } }
    }
}
