package com.music.bitchord.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.playback.PlaybackPosition
import kotlin.test.Test
import kotlin.test.assertEquals

/** Real bounded lyrics panel; no Android gesture/inset or online provider claims. */
@OptIn(ExperimentalTestApi::class)
class InlineLyricsInteractionTest {
    private fun ComposeUiTest.mount(inline: Boolean, scale: Float = 1f, picking: Boolean = false,
        seeks: MutableList<Long> = mutableListOf(), picks: MutableList<Int> = mutableListOf()) {
        installQueueTestHost()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                    Box(Modifier.width(360.dp).height(500.dp)) {
                        LyricsPanel(
                            lines = listOf(LyricLine(0, "First line"), LyricLine(10000, "Second line"), LyricLine(20000, "Third line")),
                            trackKey = "test", playhead = LyricPlayhead(PlaybackPosition(), mutableStateOf(0)),
                            looking = false, isPlaying = false, onSeekToLine = { seeks += it },
                            controlsOpen = true, onRevealControls = {}, onHideControls = {},
                            canPick = picking, picking = picking, onTogglePick = { picks += it },
                            flatInline = inline,
                        )
                    }
                }
            }
        }
    }
    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
    @Test fun `inline text keeps compact size and tapping a line seeks its timestamp`() = runComposeUiTest {
        val seeks = mutableListOf<Long>()
        mount(true, seeks = seeks)
        val node = onNodeWithText("Second line")
        node.assertIsDisplayed().performClick()
        assertEquals(listOf(10000L), seeks)
        assertEquals(17.5.sp, node.layout().layoutInput.style.fontSize)
    }
    @Test fun `legacy panel retains its original synced font size`() = runComposeUiTest {
        mount(false)
        assertEquals(34.sp, onNodeWithText("Second line").layout().layoutInput.style.fontSize)
    }
    @Test fun `inline text honors system font scale`() = runComposeUiTest {
        mount(true, scale = 2f)
        val result = onNodeWithText("Second line").layout()
        assertEquals(17.5.sp, result.layoutInput.style.fontSize)
        assertEquals(2f, result.layoutInput.density.fontScale)
    }
    @Test fun `selection mode toggles the picked row without seeking`() = runComposeUiTest {
        val seeks = mutableListOf<Long>(); val picks = mutableListOf<Int>()
        mount(true, picking = true, seeks = seeks, picks = picks)
        onNodeWithText("Second line").performClick()
        assertEquals(listOf(1), picks)
        assertEquals(emptyList(), seeks)
    }
}
