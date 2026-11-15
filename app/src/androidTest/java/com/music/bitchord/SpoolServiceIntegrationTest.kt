package com.music.bitchord

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Emulator-only integration: real service/controller/ExoPlayer, local generated PCM, no music network. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SpoolServiceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    private fun await(label: String, condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 15000
        while (android.os.SystemClock.elapsedRealtime() < end) {
            if (main(condition)) return
            Thread.sleep(50)
        }
        fail("Timed out: $label")
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
    private fun localSongs(): List<Song> {
        val file = File(context.cacheDir, "integration-silence.wav")
        val data = ByteArray(16000) // 1 second, mono 8kHz PCM16
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(data.size)
        file.writeBytes(header.array() + data)
        return (1..3).map { Song("local:test-$it", "Fixture $it", "Test", null,
            durationText = "0:01", localUri = Uri.fromFile(file).toString(),
            queueEntryId = "integration-$it") }
    }
    private fun remove(c: MediaController, song: Song, index: Int): SessionResult {
        val args = Bundle().apply {
            putInt(EXTRA_QUEUE_UNDO_INDEX, index)
            putString(EXTRA_QUEUE_UNDO_ENTRY, song.queueEntryId)
            putString(EXTRA_QUEUE_UNDO_MEDIA, song.videoId)
        }
        return main { c.sendCustomCommand(SessionCommand(ACTION_REMOVE_QUEUE_UNDO, Bundle.EMPTY), args) }
            .get(10, TimeUnit.SECONDS)
    }
    @Test fun finiteLocalPlaybackEndsThenRemoveUndoUsesRealService() {
        val c = controller()
        try {
            val songs = localSongs()
            com.music.bitchord.data.settings.AppSettings.setAutoplay(false)
            main { c.setMediaItems(songs.map { it.toMediaItem() }, 2, 0L); c.repeatMode = Player.REPEAT_MODE_OFF; c.prepare(); c.play() }
            await("finite local source reaches ENDED") { c.playbackState == Player.STATE_ENDED }
            assertEquals(3, main { c.mediaItemCount })
            val removed = remove(c, songs[0], 0)
            assertEquals(SessionResult.RESULT_SUCCESS, removed.resultCode)
            await("noncurrent removal applied") { c.mediaItemCount == 2 }
            val token = removed.extras.getString(EXTRA_QUEUE_UNDO_TOKEN)
            assertNotNull(token)
            val restored = main { c.sendCustomCommand(SessionCommand(ACTION_RESTORE_QUEUE_UNDO, Bundle.EMPTY),
                Bundle().apply { putString(EXTRA_QUEUE_UNDO_TOKEN, token) }) }.get(10, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, restored.resultCode)
            await("restore applied without duplicate") { c.mediaItemCount == 3 }
            assertEquals(listOf("integration-1", "integration-2", "integration-3"), main {
                (0 until c.mediaItemCount).map { c.getMediaItemAt(it).queueEntryId }
            })
            assertEquals("local:test-3", main { c.currentMediaItem?.mediaId })
            assertEquals(Player.STATE_ENDED, main { c.playbackState })
        } finally { main { c.stop(); c.clearMediaItems(); c.release() } }
    }
    @Suppress("UNCHECKED_CAST")
    @Test fun injectedLockedFollowerIsRejectedByRealServiceThenHostUnlocks() {
        // Test-only state injection isolates service enforcement from network/backend authorization.
        val field = ListenTogether.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        val flow = field.get(ListenTogether) as MutableStateFlow<ListenTogether.State>
        val old = flow.value
        val c = controller()
        try {
            val songs = localSongs()
            main { c.setMediaItems(songs.map { it.toMediaItem() }) }
            await("queue seeded") { c.mediaItemCount == 3 }
            val follower = old.copy(code = "TESTONLY", you = PartyMember(memberId = "fixture", displayName = "Fixture", isHost = false), hostOnlyControl = true)
            main { flow.value = follower }
            assertTrue(flow.value.controlsLocked)
            val refused = remove(c, songs[1], 1)
            assertEquals(androidx.media3.session.SessionError.ERROR_PERMISSION_DENIED, refused.resultCode)
            assertEquals(3, main { c.mediaItemCount })
            main { flow.value = follower.copy(you = follower.you!!.copy(isHost = true)) }
            assertFalse(flow.value.controlsLocked)
            // PartySync can replace local entries when membership changes. The command
            // must refer to the live session entry, not a pre-join fixture identity.
            await("host queue available after party synchronization") { c.mediaItemCount == 3 }
            val liveSong = main { c.getMediaItemAt(1).toSong() }
            println("Host live entry: ${liveSong.videoId} / ${liveSong.queueEntryId}")
            assertEquals(SessionResult.RESULT_SUCCESS, remove(c, liveSong, 1).resultCode)
            await("host removal applied") { c.mediaItemCount == 2 }
        } finally { main { flow.value = old; c.stop(); c.clearMediaItems(); c.release() } }
    }
}
