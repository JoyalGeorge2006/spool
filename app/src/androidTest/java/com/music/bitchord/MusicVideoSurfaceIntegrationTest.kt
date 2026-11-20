package com.music.bitchord

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.playback.audioOnlyTrackSelector
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Replaces picture-renderer tests: an actual muxed H264/AAC file must select only sound. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class MusicVideoSurfaceIntegrationTest {
    @Test fun pictureOnlyFileStopsWithAnExplanationWithoutReplacingTheFile() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "audio-only-picture-fixture.mp4")
        instrumentation.context.assets.open("audio-only-picture-fixture.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        lateinit var player: ExoPlayer
        var guarded = false
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).setRenderersFactory(com.music.bitchord.playback.AudioOnlyRenderersFactory(context))
                .setTrackSelector(audioOnlyTrackSelector(context)).build()
            player.addListener(object : Player.Listener {
                override fun onEvents(source: Player, events: Player.Events) {
                    if (com.music.bitchord.playback.pauseIfNoPlayableAudio(source)) guarded = true
                }
            })
            player.setMediaItem(MediaItem.Builder().setMediaId("exact-picture-file").setUri(Uri.fromFile(file)).build())
            player.prepare()
            player.play()
        }
        try {
            val end = android.os.SystemClock.elapsedRealtime() + 15000
            var checked = false
            while (!checked && android.os.SystemClock.elapsedRealtime() < end) {
                instrumentation.runOnMainSync {
                    if (guarded) {
                        assertFalse(player.playWhenReady)
                        assertFalse(player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
                        assertTrue("silent timeline never advances", player.currentPosition < 200)
                        assertEquals("exact-picture-file", player.currentMediaItem?.mediaId)
                        assertEquals(1, player.mediaItemCount)
                        assertTrue(com.music.bitchord.playback.NO_PLAYABLE_AUDIO_MESSAGE.contains("no playable audio"))
                        checked = true
                    }
                }
                if (!checked) Thread.sleep(50)
            }
            assertTrue("picture-only file reached the production pause guard", checked)
        } finally {
            instrumentation.runOnMainSync { player.release() }
            file.delete()
        }
    }
    @Test fun muxedLocalFileAdvancesWithAudioAndNoVideoTrack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "audio-only-muxed-fixture.mp4")
        instrumentation.context.assets.open("audio-only-muxed-fixture.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).setRenderersFactory(com.music.bitchord.playback.AudioOnlyRenderersFactory(context))
                .setTrackSelector(audioOnlyTrackSelector(context)).build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            player.play()
        }
        try {
            val end = android.os.SystemClock.elapsedRealtime() + 15000
            var checked = false
            while (!checked && android.os.SystemClock.elapsedRealtime() < end) {
                instrumentation.runOnMainSync {
                    player.playerError?.let { throw AssertionError("Local fixture failed", it) }
                    if (player.playbackState == Player.STATE_READY && player.currentPosition > 200) {
                        assertTrue("sound selected", player.currentTracks.isTypeSelected(C.TRACK_TYPE_AUDIO))
                        assertFalse("picture never selected", player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
                        repeat(player.rendererCount) { assertNotEquals(C.TRACK_TYPE_VIDEO, player.getRendererType(it)) }
                        assertEquals(0, player.videoSize.width)
                        assertEquals(0, player.videoSize.height)
                        checked = true
                    }
                }
                if (!checked) Thread.sleep(50)
            }
            assertTrue("muxed file actually advanced", checked)
        } finally {
            instrumentation.runOnMainSync { player.release() }
            file.delete()
        }
    }
}
