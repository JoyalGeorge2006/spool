package com.music.bitchord.data

import com.music.bitchord.data.model.TopPickKind
import com.music.bitchord.data.model.TopPickMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Show all grid lists TopPickMood.entries verbatim (TopPicksShelf's
 * MoodGridPage), and every tile's tap goes through MainActivity.playMood ->
 * TopPicks.moodStation -> the moodQueue path that ends in Taste.personalize.
 * These tests pin the inventory that grid offers, so a tile that drops out
 * of the personalized pipeline, or a grid that quietly grows or shrinks,
 * fails here instead of shipping.
 */
class MoodGridInventoryTest {

    @Test
    fun showAllGridOffersTheWholeMoodInventory() {
        // Seven Find Your Mood stations, then twenty-one moods and activities.
        assertEquals(28, TopPickMood.entries.size)
    }

    @Test
    fun coreMoodsLeadTheGrid() {
        val entries = TopPickMood.entries
        assertEquals(7, entries.count { it.isCoreMood })
        entries.take(7).forEach { assertTrue("$it should be a core mood", it.isCoreMood) }
        entries.drop(7).forEach { assertTrue("$it should not be a core mood", !it.isCoreMood) }
    }

    @Test
    fun everyTileNamesAYouTubeMoodCategory() {
        // A tile's params are its category id on YouTube Music's Moods page;
        // a blank or shifted id would make the tile play nothing.
        TopPickMood.entries.forEach { mood ->
            assertTrue("$mood has no category id", mood.params.isNotBlank())
        }
        assertEquals(
            "the grid should span YouTube's eleven mood categories",
            11,
            TopPickMood.entries.map { it.params }.distinct().size,
        )
    }

    @Test
    fun everyTileBuildsAStationThatCarriesTasteHints() {
        // This is the seam playMood uses: the station the tap opens must keep
        // its mood and the taste hints, because the queue built from it is
        // what Taste.personalize then shapes.
        val hints = listOf("malayalam", "indie")
        TopPickMood.entries.forEach { mood ->
            val station = TopPicks.moodStation(mood, hints)
            assertEquals(TopPickKind.MOOD, station.kind)
            assertEquals(mood, station.mood)
            assertEquals("$mood lost its taste hints", hints, station.hints)
        }
    }

    @Test
    fun moodsSharingACategoryStayDistinct() {
        // Several tiles share a YouTube category (eight sit in Chill). The
        // shelfWords are what steer each to its own shelves, so two tiles in
        // one category must never steer identically.
        TopPickMood.entries
            .groupBy { it.params }
            .filter { it.value.size > 1 }
            .forEach { (params, moods) ->
                val steerings = moods.map { it.shelfWords }
                assertEquals(
                    "tiles sharing category $params steer identically",
                    steerings.distinct().size,
                    steerings.size,
                )
            }
    }

    @Test
    fun shelfScoringFollowsEveryMoodsOwnWords() {
        // The shelf pick behind each tile must actually react to that tile's
        // steering words, or "taste-aware for every mood" is only true of
        // some tiles.
        TopPickMood.entries.filter { it.shelfWords.isNotEmpty() }.forEach { mood ->
            val word = mood.shelfWords.first()
            val matching = Taste.shelfScore("Midnight $word session", mood, emptyList())
            val plain = Taste.shelfScore("Midnight session", mood, emptyList())
            assertTrue("$mood's steering word '$word' did not move its shelf score", matching > plain)
        }
    }

    @Test
    fun steeringWordsAreLowercaseAndTrimmed() {
        // shelfScore matches against a lowercased shelf title, so a stored
        // capital or stray space would silently never match.
        TopPickMood.entries.flatMap { it.shelfWords }.forEach { word ->
            assertEquals("steering word is not lowercase/trimmed: '$word'", word.lowercase().trim(), word)
        }
    }
}
