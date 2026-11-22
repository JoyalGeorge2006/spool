package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import kotlin.test.Test

/**
 * DIAGNOSTIC probe for the two pre-existing RelatedMusicPanelTest failures
 * (baseline-proven by run #72 on the untouched base). It changes nothing and
 * gates nothing: it mounts the panel exactly the way the failing tests do,
 * then reports whether "Track a" exists in the semantics tree, whether it is
 * placed, and where its bounds sit - once right after waitForIdle(), then
 * again after one more explicit frame. That distinguishes:
 *  - node exists but unplaced/offscreen on the first frame and settles after
 *    another frame  -> desktop harness first-frame artifact, base tests need
 *    a one-frame settle, not a rewrite;
 *  - node missing or never placed                         -> real layout bug.
 * The exact #74 failure (AssertionError from assertIsDisplayed, not "no node
 * found") already says the node EXISTS but is not displayed; this probe pins
 * down which placement state that is.
 */
@OptIn(ExperimentalTestApi::class)
class RelatedMusicPanelFirstFrameProbe {
    private fun song(id: String) = Song(videoId = id, title = "Track $id", artist = "Artist", thumbnailUrl = null)

    @Test fun firstFramePlacementProbe() = runComposeUiTest {
        installQueueTestHost()
        val base = PlayerPlatform.host
        PlayerPlatform.install(object : PlayerHost by base {
            override suspend fun relatedSongs(song: Song) = Result.success(listOf(song("a")))
        })
        setContent {
            MaterialTheme { CompositionLocalProvider(LocalPlayerFlat provides true) {
                RelatedMusicPanel(song("seed"), false, false, {}, {}, {}, false, 14.dp, {}, Modifier.width(320.dp).height(700.dp))
            } }
        }
        waitForIdle()
        fun dump(tag: String) {
            val matches = onAllNodesWithText("Track a").fetchSemanticsNodes()
            if (matches.isEmpty()) {
                System.err.println("PROBE[$tag]: no semantics node with text 'Track a'")
                return
            }
            val node = matches.first()
            System.err.println(
                "PROBE[$tag]: nodes=${matches.size} boundsInRoot=${node.boundsInRoot} " +
                    "positionInRoot=${node.positionInRoot} size=${node.size} boundsInWindow=${node.boundsInWindow}",
            )
            val displayed = try {
                onAllNodesWithText("Track a").onFirst().assertIsDisplayed()
                true
            } catch (e: AssertionError) {
                false
            }
            System.err.println("PROBE[$tag]: displayed=$displayed")
        }
        dump("after-waitForIdle")
        mainClock.advanceTimeByFrame()
        waitForIdle()
        dump("after-one-more-frame")
        mainClock.advanceTimeBy(1000)
        waitForIdle()
        dump("after-1000ms")
    }
}
