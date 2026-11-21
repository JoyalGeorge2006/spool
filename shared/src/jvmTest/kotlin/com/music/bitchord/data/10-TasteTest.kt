package com.music.bitchord.data

import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.TopPickMood
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TasteTest {

    private fun song(id: String, artist: String, linked: List<String> = emptyList()): Song =
        Song(
            videoId = id,
            title = "Track $id",
            artist = artist,
            thumbnailUrl = null,
            artists = linked.map { ArtistRef(name = it, browseId = "UC$it") },
        )

    private fun queue(size: Int, artistPrefix: String = "Fresh"): List<Song> =
        (1..size).map { song("$artistPrefix$it", "$artistPrefix Artist $it") }

    // ---- Languages -----------------------------------------------------------

    @Test fun `a Malayalam listener's genres steer shelves to malayalam, not just bollywood`() {
        val languages = Taste.deriveLanguages(listOf("Malayalam", "Mollywood", "Indian Pop"))
        assertTrue("malayalam" in languages)
        assertTrue("indian" in languages)
    }

    @Test fun `unknown genres yield no scene words and casing is irrelevant`() {
        assertTrue(Taste.deriveLanguages(listOf("Dream pop", "Shoegaze")).isEmpty())
        assertTrue("k-pop" in Taste.deriveLanguages(listOf("K-POP")))
    }

    // ---- Shelf scoring ---------------------------------------------------------

    @Test fun `mood words outweigh listener hints on a shelf`() {
        val mood = TopPickMood.CHILL
        val moodHit = Taste.shelfScore("Chilled bollywood acoustic", mood, emptyList())
        val hintOnly = Taste.shelfScore("Malayalam hits", mood, listOf("malayalam"))
        assertTrue(moodHit > hintOnly)
        assertEquals(1, hintOnly)
    }

    @Test fun `blank hints never score and matching is case-insensitive`() {
        assertEquals(0, Taste.shelfScore("Anything", TopPickMood.CHILL, listOf("", "   ")))
        assertTrue(Taste.shelfScore("CHILL VIBES", TopPickMood.CHILL, emptyList()) > 0)
    }

    // ---- Rerank -----------------------------------------------------------------

    @Test fun `no profile leaves the queue untouched`() {
        val songs = queue(20)
        assertEquals(songs, Taste.rerank(songs, null))
        assertEquals(songs, Taste.rerank(songs, Taste.Snapshot()))
    }

    @Test fun `thumbed-down tracks leave the queue, their artists stay`() {
        val songs = queue(19) + song("Extra1", "Fresh Artist 2")
        val taste = Taste.Snapshot(
            artists = listOf("fresh artist 1"),
            dislikedIds = listOf("Fresh2", "Fresh3"),
        )
        val out = Taste.rerank(songs, taste)
        assertEquals(18, out.size)
        assertFalse(out.any { it.videoId == "Fresh2" })
        assertFalse(out.any { it.videoId == "Fresh3" })
        // The same artist's other track was not punished for a thumbed-down song.
        assertTrue(out.any { it.videoId == "Extra1" && it.artist == "Fresh Artist 2" })
    }

    @Test fun `a demoted artist sinks to the back but is never banned`() {
        val songs = queue(20)
        val demotedId = "Fresh5"
        val taste = Taste.Snapshot(
            artists = listOf("fresh artist 1"),
            demotedArtists = listOf("fresh artist 5"),
        )
        val out = Taste.rerank(songs, taste)
        assertEquals(20, out.size)
        assertEquals(demotedId, out.last().videoId)
    }

    @Test fun `a queue filtered to a stub comes back unfiltered`() {
        val songs = queue(20)
        val taste = Taste.Snapshot(
            artists = listOf("fresh artist 1"),
            // Removing these nine would leave eleven, under the floor.
            dislikedIds = (2..10).map { "Fresh$it" },
        )
        assertEquals(songs, Taste.rerank(songs, taste))
    }

    @Test fun `the opening half leans familiar, capped, and opens on one`() {
        // Ten familiar (profile artists), ten fresh.
        val familiar = queue(10, "Own")
        val fresh = queue(10, "New")
        val taste = Taste.Snapshot(artists = (1..10).map { "own artist $it" })
        val out = Taste.rerank(familiar + fresh, taste)
        assertEquals(20, out.size)
        val firstHalf = out.take(10)
        val familiarIds = familiar.mapTo(HashSet()) { it.videoId }
        // Three of five, no more: the mood survives its own listener.
        assertEquals(6, firstHalf.count { it.videoId in familiarIds })
        assertTrue(out.first().videoId in familiarIds)
        assertTrue(out.all { it.videoId in familiarIds || it.videoId.startsWith("New") })
    }

    @Test fun `liked songs count as familiar even off the artist chart`() {
        val fresh = queue(20)
        val taste = Taste.Snapshot(
            artists = listOf("nobody on the queue"),
            likedIds = listOf("Fresh7"),
        )
        val out = Taste.rerank(fresh, taste)
        assertEquals("Fresh7", out.first().videoId)
    }

    @Test fun `twice-skipped songs sink behind everything not skipped`() {
        val songs = queue(20)
        val taste = Taste.Snapshot(
            artists = listOf("fresh artist 1"),
            skippedIds = mapOf("Fresh5" to 3),
        )
        val out = Taste.rerank(songs, taste)
        assertEquals("Fresh5", out.last().videoId)
    }

    @Test fun `no familiar faces keeps the fetched order`() {
        val songs = queue(20)
        val taste = Taste.Snapshot(artists = listOf("someone else entirely"))
        assertEquals(songs, Taste.rerank(songs, taste))
    }

    // ---- Blend -------------------------------------------------------------------

    @Test fun `own toppers blend in only when the mood already carries the artist`() {
        val songs = queue(20)
        val taste = Taste.Snapshot(
            artists = listOf("fresh artist 1"),
            topSongs = listOf(
                Taste.TasteSong("Top1", "Own hit", "Fresh Artist 1"),
                Taste.TasteSong("Top2", "Stranger hit", "Unknown Artist"),
            ),
        )
        val out = Taste.blend(songs, taste)
        assertEquals(21, out.size)
        assertTrue(out.any { it.videoId == "Top1" })
        assertFalse(out.any { it.videoId == "Top2" })
        // Blended, not stacked: the addition lands inside the queue.
        assertTrue(out.indexOfFirst { it.videoId == "Top1" } in 1 until out.size)
    }

    @Test fun `blending never repeats a queued track and respects the cap`() {
        val songs = queue(20)
        val tops = (1..30).map { Taste.TasteSong("Top$it", "Own $it", "Fresh Artist 1") }
            .toMutableList()
        tops += Taste.TasteSong("Fresh1", "Already there", "Fresh Artist 1")
        val taste = Taste.Snapshot(artists = listOf("fresh artist 1"), topSongs = tops)
        val out = Taste.blend(songs, taste)
        assertEquals(20 + Taste.BLEND_CAP, out.size)
        assertEquals(out.size, out.distinctBy { it.videoId }.size)
    }

    @Test fun `disliked and heavily skipped toppers are not blended back`() {
        val songs = queue(20)
        val taste = Taste.Snapshot(
            artists = listOf("fresh artist 1"),
            topSongs = listOf(
                Taste.TasteSong("Top1", "Thumbed down", "Fresh Artist 1"),
                Taste.TasteSong("Top2", "Skipped a lot", "Fresh Artist 1"),
                Taste.TasteSong("Top3", "Fine", "Fresh Artist 1"),
            ),
            dislikedIds = listOf("Top1"),
            skippedIds = mapOf("Top2" to Taste.SKIP_BLEND_LIMIT),
        )
        val out = Taste.blend(songs, taste)
        assertTrue(out.none { it.videoId == "Top1" })
        assertTrue(out.none { it.videoId == "Top2" })
        assertTrue(out.any { it.videoId == "Top3" })
    }

    @Test fun `blend is a no-op without a profile`() {
        val songs = queue(20)
        assertEquals(songs, Taste.blend(songs, null))
        assertEquals(songs, Taste.blend(songs, Taste.Snapshot()))
    }

    // ---- Snapshot storage ---------------------------------------------------------

    @Test fun `snapshot round-trips through its JSON`() {
        val snapshot = Taste.Snapshot(
            artists = listOf("artist one", "artist two"),
            languages = listOf("malayalam"),
            genres = listOf("malayalam", "pop"),
            likedIds = listOf("abc"),
            dislikedIds = listOf("def"),
            topSongs = listOf(Taste.TasteSong("abc", "Song", "Artist One", "UC1", null)),
            skippedIds = mapOf("ghi" to 2),
            demotedArtists = listOf("artist three"),
        )
        assertEquals(snapshot, Taste.decode(Taste.encode(snapshot)))
        assertTrue(Taste.decode("not json") == null)
    }

    // ---- Region fallback -------------------------------------------------------------

    @Test fun `indian region fallback carries the south-indian scenes`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("en", "IN"))
            val hints = TopPicks.regionHints()
            assertTrue("malayalam" in hints)
            assertTrue("tamil" in hints)
            assertTrue("bollywood" in hints)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
