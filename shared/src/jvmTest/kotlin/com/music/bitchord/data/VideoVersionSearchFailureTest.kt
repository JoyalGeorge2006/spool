package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class VideoVersionSearchFailureTest {
    private val song = Song(videoId = "source", title = "Midnight Rain", artist = "Taylor Swift", thumbnailUrl = null, durationText = "2:54")
    @Test fun `successful empty responses mean no match`() = runBlocking<Unit> {
        var requests = 0
        val match = YtMusicRepository.findVideoVersionBySearch(song) { requests++; Result.success(emptyList()) }
        assertTrue(requests > 0)
        assertNull(match)
    }
    @Test fun `provider failure is not converted into no match`() = runBlocking<Unit> {
        val offline = IllegalStateException("offline fixture")
        val error = assertFailsWith<IllegalStateException> {
            YtMusicRepository.findVideoVersionBySearch(song) { Result.failure(offline) }
        }
        assertSame(offline, error)
    }
    @Test fun `partial failed lookup cannot establish absence`() = runBlocking<Unit> {
        var requests = 0
        assertFailsWith<IllegalStateException> {
            YtMusicRepository.findVideoVersionBySearch(song) {
                if (requests++ == 0) Result.failure(IllegalStateException("network fixture")) else Result.success(emptyList())
            }
        }
    }
    @Test fun `cancellation is never swallowed`() = runBlocking<Unit> {
        var requests = 0
        assertFailsWith<CancellationException> {
            YtMusicRepository.findVideoVersionBySearch(song) { requests++; Result.failure(CancellationException("canceled fixture")) }
        }
        assertEquals(1, requests)
    }
}
