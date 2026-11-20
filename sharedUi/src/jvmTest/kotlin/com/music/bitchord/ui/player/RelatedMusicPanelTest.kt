package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RelatedMusicPanelTest {
    private fun song(id: String) = Song(videoId = id, title = "Track $id", artist = "Artist", thumbnailUrl = null)

    private fun install(loader: suspend (Song) -> Result<List<Song>>) {
        installQueueTestHost()
        val base = PlayerPlatform.host
        PlayerPlatform.install(object : PlayerHost by base {
            override suspend fun relatedSongs(song: Song) = loader(song)
        })
    }

    private fun ComposeUiTest.mount(
        locked: Boolean = false,
        played: MutableList<String> = mutableListOf(),
        added: MutableList<String> = mutableListOf(),
    ) {
        setContent {
            MaterialTheme { CompositionLocalProvider(LocalPlayerFlat provides true) {
                RelatedMusicPanel(song("seed"), false, locked, { played += it.videoId },
                    { added += it.videoId }, {}, false, 14.dp, {}, Modifier.width(320.dp).height(700.dp))
            } }
        }
    }

    @Test fun `normalization removes seed duplicates and blank ids and limits batch`() {
        val seed = song("seed")
        val result = relatedCandidates(seed, listOf(seed, song(""), song("a"), song("a")) + List(30) { song("$it") })
        assertEquals(20, result.size)
        assertEquals("a", result.first().videoId)
        assertEquals(20, result.map { it.videoId }.distinct().size)
    }

    @Test fun `autoplay off still fetches without implicit play or add`() = runComposeUiTest {
        val played = mutableListOf<String>(); val added = mutableListOf<String>()
        install { Result.success(listOf(song("a"))) }
        mount(played = played, added = added)
        waitForIdle()
        onNodeWithText("Track a").assertIsDisplayed()
        assertEquals(emptyList(), played); assertEquals(emptyList(), added)
        onNodeWithText("Track a").performClick()
        // Production exposes the add affordance as a semantics custom action on
        // the row (RelatedMusicPanel.kt:183), not a contentDescription node.
        val addConfig = onNodeWithTag("related-track-a").fetchSemanticsNode().config
        val addAction = (if (SemanticsActions.CustomActions in addConfig) addConfig[SemanticsActions.CustomActions] else emptyList())
            .firstOrNull { it.label == "Add Track a to queue" }
            ?: error("add-to-queue custom action must exist when unlocked")
        assertEquals(true, addAction.action())
        assertEquals(listOf("a"), played); assertEquals(listOf("a"), added)
    }

    @Test fun `locked party can browse but cannot play or add`() = runComposeUiTest {
        val played = mutableListOf<String>(); val added = mutableListOf<String>()
        install { Result.success(listOf(song("a"))) }
        mount(locked = true, played = played, added = added)
        waitForIdle()
        onNodeWithText("Track a").assertIsDisplayed()
        // Locked: production removes the custom action entirely.
        val lockedConfig = onNodeWithTag("related-track-a").fetchSemanticsNode().config
        val lockedActions = if (SemanticsActions.CustomActions in lockedConfig) lockedConfig[SemanticsActions.CustomActions] else emptyList()
        assertEquals(true, lockedActions.none { it.label == "Add Track a to queue" })
        onNodeWithTag("related-track-a").assertIsNotEnabled()
        assertEquals(emptyList(), played); assertEquals(emptyList(), added)
    }

    @Test fun `failed request has retry then empty state`() = runComposeUiTest {
        var attempts = 0
        install { if (++attempts == 1) Result.failure(IllegalStateException("offline")) else Result.success(emptyList()) }
        mount()
        waitForIdle()
        onNodeWithText("Couldn't load related music").assertIsDisplayed()
        onNodeWithText("Try again").performClick()
        waitForIdle()
        onNodeWithText("No related tracks found").assertIsDisplayed()
        assertEquals(2, attempts)
    }

    @Test fun `track change discards old pending request`() = runComposeUiTest {
        val old = CompletableDeferred<Result<List<Song>>>()
        val selected = mutableStateOf(song("seed"))
        install { if (it.videoId == "seed") old.await() else Result.success(listOf(song("new"))) }
        setContent { MaterialTheme { CompositionLocalProvider(LocalPlayerFlat provides true) {
            RelatedMusicPanel(selected.value, false, false, {}, {}, {}, false, 14.dp, {}, Modifier.width(320.dp).height(700.dp))
        } } }
        onNodeWithText("Finding related music...").assertIsDisplayed()
        runOnIdle { selected.value = song("next") }
        waitForIdle()
        runOnIdle { old.complete(Result.success(listOf(song("old")))) }
        waitForIdle()
        onNodeWithText("Track new").assertIsDisplayed()
        onNodeWithText("Track old").assertDoesNotExist()
    }
}
