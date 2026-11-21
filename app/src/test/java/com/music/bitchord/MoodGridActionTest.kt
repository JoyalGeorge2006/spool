package com.music.bitchord

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.Taste
import com.music.bitchord.data.TopPicks
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.TopPickMood
import com.music.bitchord.ui.screens.MoodGridPage
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real grid input -> mood callback -> the station/taste seams used by playMood.
 * Offline media fixtures, not a claim about live provider availability. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-mdpi")
class MoodGridActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyRenderedTileKeepsItsMoodAndPersonalizesWithoutLosingTheMoodQueue() {
        V6ScreenshotTest().installUiHost()
        val labels = listOf("Energy", "Relax", "Feeling Blue", "Feel Good", "Focus", "Heartbreak", "Love",
            "After Hours", "Alone Time", "Chill", "Commuting", "Eating & Cooking", "Evening", "Fitness", "Gaming",
            "Home", "Morning", "Motivation", "Outdoors", "Party", "Sleep", "Social", "Sports", "Summertime Sounds",
            "Holiday", "Weekend", "Wellbeing", "Work")
        val fetched = (1..24).map { Song("f$it", "Track $it", if (it <= 8) "Familiar Artist" else "Fresh Artist $it", null) }
        val taste = Taste.Snapshot(artists = listOf("familiar artist"), languages = listOf("malayalam"),
            dislikedIds = listOf("f24"), skippedIds = mapOf("f23" to 3),
            topSongs = listOf(Taste.TasteSong("own", "Own favorite", "Familiar Artist"),
                Taste.TasteSong("foreign", "Wrong mood artist", "Unrelated Artist")))
        val taps = mutableListOf<Pair<TopPickMood, String>>()
        val queues = mutableListOf<List<Song>>()
        compose.setContent { BitChordTheme(darkTheme = false) {
            Box(Modifier.width(390.dp).height(780.dp)) {
                MoodGridPage(rememberLazyGridState(), { mood, title ->
                    taps += mood to title
                    val station = TopPicks.moodStation(mood, taste.languages)
                    assertEquals(mood, station.mood)
                    assertEquals(taste.languages, station.hints)
                    // Same composition order as moodQueue after provider fetch.
                    queues += Taste.personalize(fetched, taste)
                }, PaddingValues(top = 32.dp, bottom = 80.dp))
            }
        } }
        for ((index, mood) in TopPickMood.entries.withIndex()) {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(labels[index]))
            compose.onNodeWithText(labels[index]).assertIsDisplayed().performClick()
            compose.waitForIdle()
            assertEquals("exactly one callback for $mood", index + 1, taps.size)
            assertEquals(mood, taps.last().first)
            assertEquals(labels[index], taps.last().second)
            val personalized = queues.last()
            assertTrue("$mood keeps allowed own topper", personalized.any { it.videoId == "own" })
            assertFalse("$mood excludes unrelated topper", personalized.any { it.videoId == "foreign" })
            assertFalse("$mood excludes disliked track", personalized.any { it.videoId == "f24" })
            assertEquals("$mood demotes repeated skip", "f23", personalized.last().videoId)
            assertEquals("$mood opens familiar", "Familiar Artist", personalized.first().artist)
            assertTrue("$mood keeps fresh material", personalized.any { it.artist.startsWith("Fresh Artist") })
            assertEquals("$mood keeps queue entries unique", personalized.size, personalized.map { it.videoId }.distinct().size)
            assertEquals("$mood no-profile fallback", fetched, Taste.personalize(fetched, null))
        }
        assertEquals(TopPickMood.entries.toList(), taps.map { it.first })
    }
}
