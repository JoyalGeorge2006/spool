package com.music.bitchord

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Legacy controller commands against the real audio-only service, using generated local PCM. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class LocalVersionSwapIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun <T> main(block: () -> T): T {
        var answer: Result<T>? = null
        instrumentation.runOnMainSync { answer = runCatching(block) }
        return answer!!.getOrThrow()
    }
    private fun await(label: String, timeoutMs: Long = 12000, condition: () -> Boolean) {
        val until = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < until) {
            if (main(condition)) return
            Thread.sleep(20)
        }
        fail("Timed out: $label")
    }
    private fun controller(): MediaController {
        val latch = CountDownLatch(1)
        var result: MediaController? = null
        var failure: Throwable? = null
        main {
            val future = MediaController.Builder(context, SessionToken(context,
                ComponentName(context, PlaybackService::class.java))).buildAsync()
            future.addListener({
                try { result = future.get() } catch (e: Throwable) { failure = e }
                latch.countDown()
            }, { it.run() })
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS))
        failure?.let { throw it }
        return requireNotNull(result)
    }
    private fun song(id: String, video: Boolean = false): Song {
        val file = File(context.cacheDir, "version-local-$id.wav")
        val data = ByteArray(16000 * 30)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(data.size)
        file.writeBytes(header.array() + data)
        return Song("local:version-$id", "Local $id", "Fixture", null,
            durationText = "0:30", localUri = Uri.fromFile(file).toString(), isVideo = video,
            queueEntryId = "same-occurrence")
    }
    private fun seed(c: MediaController, source: Song, playing: Boolean = false) {
        main {
            AppSettings.smartVersionAlignment.value = false
            AppSettings.setAutoplay(false)
            c.setMediaItem(source.toMediaItem())
            c.repeatMode = Player.REPEAT_MODE_ONE
            c.prepare()
            c.playWhenReady = playing
        }
        await("local source ready") { c.playbackState == Player.STATE_READY }
    }
    @Test fun legacySwapCannotReplacePausedTrackOrPosition() {
        val c = controller()
        try {
            val source = song("paused")
            seed(c, source)
            assertFalse(main { c.isSessionCommandAvailable(SessionCommand(ACTION_SWAP_VERSION, Bundle.EMPTY)) })
            main { c.seekTo(4000L) }
            val result = main { c.swapToVersion(song("forbidden-video", true)) }.get(5, TimeUnit.SECONDS)
            assertNotEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            assertEquals(source.videoId, main { c.currentMediaItem?.mediaId })
            assertEquals(source.localUri, main { c.currentMediaItem?.toSong()?.localUri })
            assertEquals("same-occurrence", main { c.currentMediaItem?.toSong()?.queueEntryId })
            assertFalse(main { c.playWhenReady })
            assertTrue(main { c.currentPosition in 3500L..4500L })
        } finally { main { c.stop(); c.clearMediaItems(); c.release() } }
    }
    @Test fun missingLegacyTargetCannotInterruptWorkingAudio() {
        val c = controller()
        try {
            val source = song("working")
            val missing = source.copy(videoId = "local:missing", isVideo = true,
                localUri = Uri.fromFile(File(context.cacheDir, "does-not-exist-version.wav")).toString())
            seed(c, source, playing = true)
            val result = main { c.swapToVersion(missing) }.get(5, TimeUnit.SECONDS)
            assertNotEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            assertEquals(source.videoId, main { c.currentMediaItem?.mediaId })
            assertNull(main { c.playerError })
            assertTrue(main { c.playWhenReady })
            assertFalse(main { AppSettings.versionAlignmentInProgress.value })
        } finally { main { c.stop(); c.clearMediaItems(); c.release() } }
    }
    @Test fun staleSourceCommandCannotReplaceANewerTrack() {
        val c = controller()
        try {
            val source = song("newer")
            seed(c, source)
            assertFalse(main { c.isSessionCommandAvailable(SessionCommand(ACTION_SWAP_VERSION, Bundle.EMPTY)) })
            val result = main { c.sendCustomCommand(SessionCommand(ACTION_SWAP_VERSION, Bundle.EMPTY), Bundle().apply {
                putBundle(EXTRA_SWAP_MEDIA_ITEM, song("stale-target", true).toSongBundle())
                putString(EXTRA_SWAP_SOURCE_MEDIA_ID, "local:old-track")
            }) }.get(5, TimeUnit.SECONDS)
            assertNotEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            assertEquals(source.videoId, main { c.currentMediaItem?.mediaId })
            assertFalse(main { AppSettings.versionAlignmentInProgress.value })
        } finally { main { c.stop(); c.clearMediaItems(); c.release() } }
    }
    @Test fun repeatedLegacyCommandsAreRejectedWithoutRestartingPlayback() {
        val c = controller()
        try {
            val source = song("repeated")
            seed(c, source, playing = true)
            val results = main { (1..3).map { c.swapToVersion(song("target-$it", true)) } }
            results.forEach { assertNotEquals(SessionResult.RESULT_SUCCESS, it.get(5, TimeUnit.SECONDS).resultCode) }
            assertEquals(source.videoId, main { c.currentMediaItem?.mediaId })
            assertTrue(main { c.playWhenReady })
            assertNull(main { c.playerError })
        } finally { main { c.stop(); c.clearMediaItems(); c.release() } }
    }
}
