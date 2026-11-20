package com.music.bitchord.data.innertube

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Replaces removed online counterpart probes: legacy calls cannot replace an upload or contact a provider. */
@Suppress("DEPRECATION")
class VersionResolutionOnlineTest {
    @Test fun videoOriginRetainsTheExactRecording() = runBlocking {
        val video = Song(videoId = "exact-upload", title = "Live cut", artist = "Fixture", thumbnailUrl = null,
            isVideo = true, localUri = "content://media/video/12", queueEntryId = "saved-occurrence")
        assertSame(video, YtMusicRepository.resolveAudio(video))
    }
    @Test fun catalogueAudioCannotResolveAPictureCounterpart() = runBlocking {
        val audio = Song(videoId = "audio-upload", title = "Recording", artist = "Fixture", thumbnailUrl = null)
        assertNull(YtMusicRepository.resolveVideo(audio))
    }
}
