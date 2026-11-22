package com.music.bitchord.ui.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlayerSectionTabsTest {
    @Test fun `main offers three independent section buttons`() = runComposeUiTest {
        installQueueTestHost()
        val requests = mutableListOf<PlayerPaneTab>()
        setContent { MaterialTheme { PlayerPaneTabRow(selected = null, onSelect = { requests += it }) } }
        onNodeWithText("Queue").assertIsNotSelected().performClick()
        onNodeWithText("Lyrics").assertIsNotSelected().performClick()
        onNodeWithText("Related").assertIsNotSelected().performClick()
        assertEquals(listOf(PlayerPaneTab.UP_NEXT, PlayerPaneTab.LYRICS, PlayerPaneTab.RELATED), requests)
        onNodeWithText("Song").assertDoesNotExist()
        onNodeWithText("Video").assertDoesNotExist()
    }
    @Test fun `selected section cannot reopen itself`() = runComposeUiTest {
        installQueueTestHost()
        val requests = mutableListOf<PlayerPaneTab>()
        setContent { MaterialTheme { PlayerPaneTabRow(selected = PlayerPaneTab.LYRICS, onSelect = { requests += it }) } }
        onNodeWithText("Lyrics").assertIsSelected().performClick()
        assertEquals(emptyList(), requests)
        onNodeWithText("Queue").performClick()
        assertEquals(listOf(PlayerPaneTab.UP_NEXT), requests)
    }
    @Test fun `all section touch targets remain accessible`() = runComposeUiTest {
        installQueueTestHost()
        setContent { MaterialTheme { PlayerPaneTabRow(selected = null, onSelect = {}) } }
        listOf("Queue", "Lyrics", "Related").forEach { onNodeWithText(it).assertHeightIsAtLeast(48.dp) }
    }
}
