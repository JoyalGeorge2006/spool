package com.music.bitchord.playback

import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedContextRequestsTest {

    @After
    fun tearDown() {
        BoundedContextRequests.clear()
    }

    @Test
    fun `take consumes only the matching newest token and keeps snapshot`() {
        val first = request("first")
        val staleToken = BoundedContextRequests.put(first)
        val newestToken = BoundedContextRequests.put(request("newest"))

        assertNull(BoundedContextRequests.take(staleToken))
        val taken = BoundedContextRequests.take(newestToken)

        assertEquals("newest", taken?.songs?.single()?.videoId)
        assertEquals(newestToken, taken?.handoffToken)
        assertEquals("newest", BoundedContextRequests.snapshot.value?.songs?.single()?.videoId)
        assertNull(BoundedContextRequests.take(newestToken))
    }

    @Test
    fun `publish grows the process snapshot and rejects a stale handoff`() {
        val token = BoundedContextRequests.put(request("one", continuation = "next"))
        val taken = requireNotNull(BoundedContextRequests.take(token))
        val second = song("two")

        assertTrue(BoundedContextRequests.publish(taken, listOf(song("one"), second), "last"))
        val snapshot = requireNotNull(BoundedContextRequests.snapshot.value)
        assertEquals(listOf("one", "two"), snapshot.songs.map(Song::videoId))
        assertEquals("last", snapshot.continuationToken)
        assertEquals(token, snapshot.request.handoffToken)

        assertFalse(BoundedContextRequests.publish("stale", listOf(second), null))
        assertEquals("last", BoundedContextRequests.snapshot.value?.continuationToken)
    }

    @Test
    fun `listener provenance survives handoff and subsequent snapshot publication`() {
        val token = BoundedContextRequests.put(request("one").copy(listenerGeneration = 7L))
        val taken = BoundedContextRequests.take(token)!!
        assertEquals(7L, taken.listenerGeneration)
        assertTrue(BoundedContextRequests.publish(taken, listOf(song("two")), null))
        assertEquals(7L, BoundedContextRequests.snapshot.value?.request?.listenerGeneration)
    }

    @Test
    fun `unknown restored listener is not guessed from current session`() {
        val restored = BoundedContextRequests.restore(request("old"))
        assertNull(restored.listenerGeneration)
        assertNull(BoundedContextRequests.snapshot.value?.request?.listenerGeneration)
    }

    private fun request(id: String, continuation: String? = null) =
        BoundedContextRequests.Request(
            songs = listOf(song(id)),
            startIndex = 0,
            source = QueueSource("Release", PlaybackSourceType.BROWSE, "VLrelease"),
            continuationToken = continuation,
            shuffle = false,
        )

    private fun song(id: String) = Song(
        videoId = id,
        title = id,
        artist = "Artist",
        thumbnailUrl = null,
    )
}
