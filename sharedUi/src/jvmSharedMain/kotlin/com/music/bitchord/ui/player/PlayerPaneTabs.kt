package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

/** The three pages under the player: what plays next, the lyrics, and Related (AutoPlay). */
internal enum class PlayerPaneTab { UP_NEXT, LYRICS, RELATED }

/**
 * Tab row shown over the queue and lyrics panels. A pure view: [onSelect] runs
 * the host's existing open/close transitions, and tapping the selected tab does
 * nothing. Labels are string resources (spool_player_lyrics / _related).
 */
@Composable
internal fun PlayerPaneTabRow(
    selected: PlayerPaneTab?,
    onSelect: (PlayerPaneTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    if (largeText) {
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                PaneTab(stringResource(Res.string.queue), selected == PlayerPaneTab.UP_NEXT, Modifier.weight(1f)) { onSelect(PlayerPaneTab.UP_NEXT) }
                PaneTab(stringResource(Res.string.spool_player_lyrics), selected == PlayerPaneTab.LYRICS, Modifier.weight(1f)) { onSelect(PlayerPaneTab.LYRICS) }
            }
            PaneTab(stringResource(Res.string.spool_player_related), selected == PlayerPaneTab.RELATED, Modifier.fillMaxWidth()) { onSelect(PlayerPaneTab.RELATED) }
        }
        return
    }
    Row(modifier.fillMaxWidth()) {
        PaneTab(stringResource(Res.string.queue), selected == PlayerPaneTab.UP_NEXT, Modifier.weight(1f)) {
            onSelect(PlayerPaneTab.UP_NEXT)
        }
        PaneTab(stringResource(Res.string.spool_player_lyrics), selected == PlayerPaneTab.LYRICS, Modifier.weight(1f)) {
            onSelect(PlayerPaneTab.LYRICS)
        }
        PaneTab(stringResource(Res.string.spool_player_related), selected == PlayerPaneTab.RELATED, Modifier.weight(1f)) {
            onSelect(PlayerPaneTab.RELATED)
        }
    }
}

@Composable
private fun PaneTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = !selected, role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = chromeWhite(if (selected) 1f else 0.6f),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(38.dp)
                    .height(3.dp)
                    .background(chromeWhite(1f), RoundedCornerShape(2.dp)),
            )
        }
    }
}
