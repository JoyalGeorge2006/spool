package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The Song / Video switch in the player's top strip.
 *
 * Only a view: [video] is what is playing now, and [onSelect] is the host's
 * existing version switch (or its revert). Nothing here decides whether a
 * switch is possible; while one is running [enabled] is false so a second tap
 * cannot stack on the first, and a locked party passes null for [onSelect] at
 * the call site so the pill is not drawn at all.
 *
 * Labels are string resources (spool_player_song / _video).
 */
@Composable
internal fun SongVideoPill(
    video: Boolean,
    enabled: Boolean,
    onSelect: (video: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(chromeWhite(0.10f))
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillSegment(stringResource(Res.string.spool_player_song), selected = !video, enabled = enabled) { if (video) onSelect(false) }
        PillSegment(stringResource(Res.string.spool_player_video), selected = video, enabled = enabled) { if (!video) onSelect(true) }
    }
}

@Composable
private fun PillSegment(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // The hit area is the full 48dp tall outer box; only the inner box is drawn.
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled && !selected, role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) chromeWhite(1f) else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            // Selected text is the background colour on the ink fill (chromeWhite(1f)
            // is the text colour in the flat player); the unselected one is dimmed.
            color = if (selected) MaterialTheme.colorScheme.background else chromeWhite(0.65f),
        )
      }
    }
}
