package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AudioVersionSearchFailureTest {
    private val song = Song(videoId = "source", title = "Midnight Rain", artist = "Taylor Swift", thumbnailUrl = null, durationText = "2:54", isVideo = true)
    @Test fun `successful empty responses mean no match`() = runBlocking<Unit> {
        var requests = 0
        val match = YtMusicRepository.findAudioVersionBySearch(song) { requests++; Result.success(emptyList()) }
        assertTrue(requests > 0)
        assertNull(match)
    }
    @Test fun `provider failure is not converted into no match`() = runBlocking<Unit> {
        val offline = IllegalStateException("offline fixture")
        val error = assertFailsWith<IllegalStateException> {
            YtMusicRepository.findAudioVersionBySearch(song) { Result.failure(offline) }
        }
        assertSame(offline, error)
    }
    @Test fun `partial failed lookup cannot establish absence`() = runBlocking<Unit> {
        var requests = 0
        assertFailsWith<IllegalStateException> {
            YtMusicRepository.findAudioVersionBySearch(song) {
                if (requests++ == 0) Result.failure(IllegalStateException("network fixture")) else Result.success(emptyList())
            }
        }
    }
    @Test fun `cancellation is never swallowed`() = runBlocking<Unit> {
        var requests = 0
        assertFailsWith<CancellationException> {
            YtMusicRepository.findAudioVersionBySearch(song) { requests++; Result.failure(CancellationException("canceled fixture")) }
        }
        assertEquals(1, requests)
    }
    @Test fun `successful match after earlier failure remains usable`() = runBlocking<Unit> {
        val audio = song.copy(videoId = "audio-target", isVideo = false)
        var requests = 0
        val match = YtMusicRepository.findAudioVersionBySearch(song) {
            if (requests++ == 0) Result.failure(IllegalStateException("network fixture"))
            else Result.success(listOf(audio))
        }
        assertTrue(requests > 1)
        assertEquals(audio, match)
    }
}
