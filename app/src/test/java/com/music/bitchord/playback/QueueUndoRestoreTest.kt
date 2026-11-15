package com.music.bitchord.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueUndoRestoreTest {
    @Test fun playingPartyRejectsRestoreThatWouldPublishProtectivePause() {
        assertFalse(canRestoreQueueWithoutPartyPause(true, true, 0, Player.STATE_IDLE))
        assertFalse(canRestoreQueueWithoutPartyPause(true, true, 1, Player.STATE_ENDED))
        assertTrue(canRestoreQueueWithoutPartyPause(true, true, 2, Player.STATE_READY))
        assertTrue(canRestoreQueueWithoutPartyPause(true, false, 0, Player.STATE_IDLE))
        assertTrue(canRestoreQueueWithoutPartyPause(false, true, 0, Player.STATE_IDLE))
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id").build()

    @Test fun soleItemUndoDoesNotRetainPlayWhenReady() {
        val p = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            val only = item("only")
            p.setMediaItem(only)
            p.playWhenReady = true
            p.removeMediaItem(0)
            p.restoreRemovedQueueItem(0, only)
            assertEquals(1, p.mediaItemCount)
            assertFalse(p.playWhenReady)
            assertFalse(p.isPlaying)
            assertEquals("only", p.currentMediaItem?.mediaId)
        } finally { p.release() }
    }

    @Test fun upcomingUndoPreservesCurrentPositionAndPlayIntent() {
        val p = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            p.setMediaItems(listOf(item("now"), item("next")))
            p.seekTo(0, 1234L)
            p.playWhenReady = true
            p.restoreRemovedQueueItem(1, item("restored"))
            assertEquals("now", p.currentMediaItem?.mediaId)
            assertEquals(1234L, p.currentPosition)
            assertTrue(p.playWhenReady)
        } finally { p.release() }
    }

    @Test fun actuallyEndedEmptyPlayerUndoPausesBeforeAdding() {
        val p = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            // Preparing a real empty ExoPlayer reaches ENDED deterministically,
            // without asking a Robolectric audio sink to play finite silence.
            p.prepare()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (p.playbackState != Player.STATE_ENDED && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
                Thread.sleep(5)
            }
            assertEquals("Real empty ExoPlayer must actually reach ended", Player.STATE_ENDED, p.playbackState)
            p.playWhenReady = true
            var observedRestoredTimeline = false
            p.addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    if (p.mediaItemCount > 0) {
                        observedRestoredTimeline = true
                        assertFalse("Already paused when restored timeline is exposed", p.playWhenReady)
                    }
                }
            })
            p.restoreRemovedQueueItem(p.mediaItemCount, item("restored"))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(observedRestoredTimeline)
            assertFalse(p.playWhenReady)
            assertFalse(p.isPlaying)
        } finally { p.release() }
    }
}
