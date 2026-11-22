package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.test.core.app.ApplicationProvider
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioOnlyPolicyTest {
    private fun group(mime: String, selected: Boolean) = androidx.media3.common.Tracks.Group(
        androidx.media3.common.TrackGroup(androidx.media3.common.Format.Builder().setSampleMimeType(mime).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected),
    )
    @Test fun emptyTracksAreNotMisreportedAsASilentFile() {
        assertFalse(hasNoPlayableAudio(androidx.media3.common.Tracks.EMPTY))
    }
    @Test fun pictureOnlyOrUnsupportedSoundCannotSilentlyAdvance() {
        assertTrue(hasNoPlayableAudio(androidx.media3.common.Tracks(listOf(group("video/avc", false)))))
        assertTrue(hasNoPlayableAudio(androidx.media3.common.Tracks(listOf(group("audio/mp4a-latm", false)))))
    }
    @Test fun soundWithOrWithoutAnUnselectedPictureTrackIsPlayable() {
        assertFalse(hasNoPlayableAudio(androidx.media3.common.Tracks(listOf(group("audio/mp4a-latm", true)))))
        assertFalse(hasNoPlayableAudio(androidx.media3.common.Tracks(listOf(group("audio/mp4a-latm", true), group("video/avc", false)))))
    }
    @Test fun noAudioExplanationOverridesCachedStateAndClearsOnTheNextPlayableItem() {
        val picture = androidx.media3.common.Tracks(listOf(group("video/avc", false)))
        val audio = androidx.media3.common.Tracks(listOf(group("audio/mp4a-latm", true)))
        assertEquals(NO_PLAYABLE_AUDIO_MESSAGE, audioOnlyPlaybackError(picture, null))
        assertEquals(NO_PLAYABLE_AUDIO_MESSAGE, audioOnlyPlaybackError(picture, "earlier error"))
        assertNull(audioOnlyPlaybackError(audio, NO_PLAYABLE_AUDIO_MESSAGE))
        assertEquals("server failed", audioOnlyPlaybackError(audio, "server failed"))
    }
    @Test fun noPictureRendererIsConstructed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val renderers = AudioOnlyRenderersFactory(context).createRenderers(handler,
            object : androidx.media3.exoplayer.video.VideoRendererEventListener {},
            object : androidx.media3.exoplayer.audio.AudioRendererEventListener {},
            androidx.media3.exoplayer.text.TextOutput {}, androidx.media3.exoplayer.metadata.MetadataOutput {})
        assertTrue(renderers.any { it.trackType == C.TRACK_TYPE_AUDIO })
        assertFalse(renderers.any { it.trackType == C.TRACK_TYPE_VIDEO })
    }
    @Test fun videoDisabledButAudioRemainsEnabled() {
        val selector = audioOnlyTrackSelector(ApplicationProvider.getApplicationContext())
        try {
            assertTrue(selector.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))
            assertFalse(selector.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))
        } finally { selector.release() }
    }
    @Test fun obsoleteSettingsCannotEnablePicturesOrReplaceTheUpload() {
        val oldCanvas = AppSettings.animatedCanvas.value
        val oldMusic = AppSettings.preferMusicOnly.value
        try {
            AppSettings.setAnimatedCanvas(true)
            AppSettings.setPreferMusicOnly(true)
            assertFalse(AppSettings.animatedCanvas.value)
            assertFalse(AppSettings.preferMusicOnly.value)
        } finally {
            AppSettings.animatedCanvas.value = oldCanvas
            AppSettings.preferMusicOnly.value = oldMusic
        }
    }
    @Test fun musicVideoFileRetainsItsIdentityAndLocalUri() {
        val song = Song(videoId = "local:exact-cut", title = "Live cut", artist = "Artist", thumbnailUrl = null,
            localUri = "content://media/external/video/media/12", isVideo = true, queueEntryId = "saved-entry")
        val restored = song.toMediaItem().toSong()
        assertEquals(song.videoId, restored.videoId)
        assertEquals(song.localUri, restored.localUri)
        assertEquals(song.queueEntryId, restored.queueEntryId)
        assertTrue(restored.isVideo)
    }
}
