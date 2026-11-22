package com.music.bitchord

import com.music.bitchord.data.stats.*
import com.music.bitchord.ui.replay.albumRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReplayAlbumIdentityTest {
    private fun rows(albums: List<RankedEntry>) = ReplaySummary(
        ReplayPeriod.THIS_YEAR, "2026", 0L, 0, emptyList(), emptyList(), albums,
        emptyList(), List(24) { 0L }, null, 0L, 0, 0, albums.size, null,
    ).albumRows(10)

    @Test fun distinctReleasesWithSameAlbumAndArtistHaveDistinctKeys() {
        val entries = listOf("release-a", "release-b").map {
            RankedEntry("Same album", "Same artist", null, it, 60000L, 1)
        }
        val rows = rows(entries)
        assertNotEquals(rows[0].key, rows[1].key)
        assertEquals(listOf("Same album", "Same album"), rows.map { it.title })
        assertEquals(listOf(1, 2), rows.map { it.rank })
    }

    @Test fun localAlbumFallbackDoesNotCollideOnVisibleSeparator() {
        val rows = rows(listOf(
            RankedEntry("A|B", "C", null, null, 60000L, 1),
            RankedEntry("A", "B|C", null, null, 60000L, 1),
        ))
        assertNotEquals(rows[0].key, rows[1].key)
    }
}
