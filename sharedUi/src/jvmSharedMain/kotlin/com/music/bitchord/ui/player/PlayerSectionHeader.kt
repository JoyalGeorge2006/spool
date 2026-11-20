package com.music.bitchord.ui.player

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Pinned playback and a clear way back. No surface or moving artwork is created. */
@Composable
internal fun PlayerSectionHeader(
    title: String, artist: String, artwork: String?, isPlaying: Boolean, isLoading: Boolean,
    previousEnabled: Boolean, nextEnabled: Boolean,
    onReturn: () -> Unit, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit,
    collapseToMiniPlayerByDrag: Boolean = false,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val controls: @Composable () -> Unit = {
        IconButton(onClick = onPrevious, enabled = previousEnabled) {
            Icon(painterResource(Res.drawable.ic_player_previous), stringResource(Res.string.widget_previous), Modifier.size(20.dp))
        }
        if (isLoading) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else IconButton(onClick = onPlayPause) {
            Icon(painterResource(if (isPlaying) Res.drawable.ic_player_pause else Res.drawable.ic_player_play),
                stringResource(if (isPlaying) Res.string.pause else Res.string.play), Modifier.size(24.dp))
        }
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(painterResource(Res.drawable.ic_player_next), stringResource(Res.string.widget_next), Modifier.size(20.dp))
        }
    }
    Column(Modifier.testTag("player-section-header").bleedHorizontally(PLAYER_GUTTER).fillMaxWidth()
        .pointerInput(onReturn, collapseToMiniPlayerByDrag) {
            // Let the enclosing sheet own collapse distance, velocity and snap-back.
            if (collapseToMiniPlayerByDrag) return@pointerInput
            var pulled = 0f
            detectVerticalDragGestures(onDragStart = { pulled = 0f },
                onVerticalDrag = { change, dy -> pulled = (pulled + dy).coerceAtLeast(0f); change.consume() },
                onDragEnd = { if (pulled >= 64.dp.toPx()) onReturn() })
        }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onReturn) {
                Icon(Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.spool_back_to_player))
            }
            if (!largeText) AsyncImage(model = artwork, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable(onClick = onReturn)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = if (largeText) 2 else 1, overflow = TextOverflow.Ellipsis)
                Text(artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!largeText) controls()
        }
        if (largeText) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) { controls() }
    }
    }
}
