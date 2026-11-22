package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import org.junit.Assert.*
import org.junit.Test

class SparseMoodTasteTest {
    private val songs = (1..8).map { Song("s$it", "Song $it", "Artist $it", null) }

    @Test fun exactDislikesAreExcludedFromSparseAndShortPositiveProfiles() {
        val blocked = (1..6).map { "s$it" }
        for (profile in listOf(Taste.Snapshot(dislikedIds = blocked),
            Taste.Snapshot(artists = listOf("artist 8"), dislikedIds = blocked))) {
            assertEquals(listOf("s7", "s8"), Taste.personalizeMood(songs, profile).map { it.videoId })
        }
    }
    @Test fun allBlockedStaysEmptyAndEmptyProfileDoesNotChangeQueue() {
        assertTrue(Taste.personalizeMood(songs, Taste.Snapshot(dislikedIds = songs.map { it.videoId })).isEmpty())
        assertEquals(songs, Taste.personalizeMood(songs, Taste.Snapshot()))
        assertEquals(songs, Taste.personalizeMood(songs, null))
    }
    @Test fun sparseSkipsAndArtistDemotionsMoveBackWithoutBanning() {
        val profile = Taste.Snapshot(skippedIds = mapOf("s1" to 3), demotedArtists = listOf("artist 2"))
        val result = Taste.personalizeMood(songs, profile)
        assertEquals(songs.map { it.videoId }.toSet(), result.map { it.videoId }.toSet())
        assertEquals(listOf("s1", "s2"), result.takeLast(2).map { it.videoId })
    }
    @Test fun existingGeneralPersonalizeSmallQueueFallbackIsUnchanged() {
        val profile = Taste.Snapshot(artists = listOf("artist 8"), dislikedIds = listOf("s1"))
        assertEquals(songs, Taste.personalize(songs, profile))
        assertFalse(Taste.personalizeMood(songs, profile).any { it.videoId == "s1" })
    }
}
