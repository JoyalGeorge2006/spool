package com.music.bitchord.ui.player

import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

internal sealed interface RelatedMusicState {
    data object Loading : RelatedMusicState
    data class Ready(val songs: List<Song>) : RelatedMusicState
    data object Failed : RelatedMusicState
}

/** Pure normalization, shared by request handling and regression tests. */
internal fun relatedCandidates(seed: Song, candidates: List<Song>): List<Song> = candidates
    .filter { it.videoId.isNotBlank() && it.videoId != seed.videoId }
    .distinctBy { it.videoId }
    .take(20)

/** Discovery has no queue side effects. A changed song cancels and discards its old request. */
@Composable
internal fun RelatedMusicPanel(
    song: Song?,
    autoplayEnabled: Boolean,
    controlsLocked: Boolean,
    onPlay: (Song) -> Unit,
    onAdd: (Song) -> Unit,
    onToggleAutoplay: () -> Unit,
    allowParentScroll: Boolean,
    sideGutter: Dp,
    onScrollingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var retry by remember(song?.videoId) { mutableIntStateOf(0) }
    var state by remember(song?.videoId) { mutableStateOf<RelatedMusicState>(RelatedMusicState.Loading) }
    val host = PlayerPlatform.host
    LaunchedEffect(song?.videoId, retry, host) {
        state = RelatedMusicState.Loading
        if (song == null) {
            state = RelatedMusicState.Ready(emptyList())
            return@LaunchedEffect
        }
        state = try {
            val result = withTimeoutOrNull(20_000L) { host.relatedSongs(song) }
                ?: Result.failure(IllegalStateException("Related request timed out"))
            // A provider may finish after cancellation; never publish its stale batch.
            coroutineContext.ensureActive()
            result.fold(
                onSuccess = { RelatedMusicState.Ready(relatedCandidates(song, it)) },
                onFailure = { if (it is CancellationException) throw it else RelatedMusicState.Failed },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            RelatedMusicState.Failed
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(song?.videoId) { list.scrollToItem(0) }
    LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress }.collect(onScrollingChange) }
    val paging = LocalPlayerPageGestures.current
    val keepScroll = remember(list, allowParentScroll, paging) {
        keepScrollInList(list, allowParentScroll, paging)
    }
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxWidth().nestedScroll(keepScroll).testTag("related-music"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        item("heading") {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, null, tint = chromeWhite(0.6f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(Res.string.spool_related_heading), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = chromeWhite(0.6f))
                }
                song?.let { Text(stringResource(Res.string.spool_related_based_on, it.title), fontSize = 11.5.sp, color = chromeWhite(0.6f), modifier = Modifier.padding(top = 3.dp)) }
            }
        }
        when (val content = state) {
            RelatedMusicState.Loading -> item("loading") {
                Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = chromeWhite(1f), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.spool_related_loading), color = chromeWhite(0.7f))
                }
            }
            RelatedMusicState.Failed -> item("error") {
                Column(Modifier.padding(vertical = 16.dp).testTag("related-error")) {
                    Text(stringResource(Res.string.spool_related_load_failed), color = chromeWhite(1f), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.spool_related_queue_unchanged), color = chromeWhite(0.65f), fontSize = 13.sp)
                    OutlinedButton(onClick = { retry++ }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(Res.string.spool_related_retry), color = chromeWhite(1f))
                    }
                }
            }
            is RelatedMusicState.Ready -> {
                if (content.songs.isEmpty()) item("empty") {
                    Column(Modifier.padding(vertical = 16.dp).testTag("related-empty")) {
                        Text(stringResource(Res.string.spool_related_empty), color = chromeWhite(1f), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(Res.string.spool_related_empty_hint), color = chromeWhite(0.65f), fontSize = 13.sp)
                        OutlinedButton(onClick = { retry++ }, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(Res.string.spool_related_retry), color = chromeWhite(1f))
                        }
                    }
                }
                itemsIndexed(content.songs, key = { _, candidate -> candidate.videoId }) { index, candidate ->
                    RelatedSongRow(candidate, controlsLocked, index == 0, index == content.songs.lastIndex,
                        onPlay = { onPlay(candidate) }, onAdd = { onAdd(candidate) })
                }
            }
        }
        item("autoplay-control") {
            HorizontalDivider(Modifier.padding(top = 24.dp, bottom = 16.dp), color = androidx.compose.ui.graphics.Color.Transparent)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(Res.string.autoplay), color = chromeWhite(1f), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.spool_related_autoplay_description), color = chromeWhite(0.65f), fontSize = 12.sp)
                }
                Switch(checked = autoplayEnabled, onCheckedChange = { onToggleAutoplay() }, enabled = !controlsLocked,
                    colors = SwitchDefaults.colors(checkedTrackColor = chromeWhite(1f), checkedThumbColor = MaterialTheme.colorScheme.background))
            }
            if (controlsLocked) Text(stringResource(Res.string.spool_related_host_only), color = chromeWhite(0.65f), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}


/** Selected compact grouped-row design, with explicit actions off the duration column. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RelatedSongRow(
    song: Song, locked: Boolean, first: Boolean, last: Boolean,
    onPlay: () -> Unit, onAdd: () -> Unit,
) {
    val playSongLabel = stringResource(Res.string.spool_related_play_song, song.title)
    val songActionsLabel = stringResource(Res.string.spool_related_song_actions)
    val addSongLabel = stringResource(Res.string.spool_related_add_song, song.title)
    var menuOpen by remember(song.videoId) { mutableStateOf(false) }
    val currentLocked by rememberUpdatedState(locked)
    LaunchedEffect(locked) { if (locked) menuOpen = false }
    val large = LocalDensity.current.fontScale >= 1.5f
    val shape = RoundedCornerShape(
        topStart = if (first) 14.dp else 0.dp, topEnd = if (first) 14.dp else 0.dp,
        bottomStart = if (last) 14.dp else 0.dp, bottomEnd = if (last) 14.dp else 0.dp,
    )
    val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
        .drawBehind { if (!first) drawLine(androidx.compose.ui.graphics.Color.Transparent, Offset(62.dp.toPx(), 0f), Offset(size.width - 8.dp.toPx(), 0f), 1.dp.toPx()) }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .combinedClickable(enabled = !locked, onClickLabel = playSongLabel,
                onLongClickLabel = songActionsLabel, onClick = { if (!currentLocked) onPlay() },
                onLongClick = { if (!currentLocked) menuOpen = true })
            .semantics { if (!locked) customActions = listOf(CustomAccessibilityAction(addSongLabel) { if (!currentLocked) { onAdd(); true } else false }) }
            .testTag("related-track-${song.videoId}").padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(chromeWhite(0.08f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = chromeWhite(0.45f), modifier = Modifier.size(22.dp))
                if (!song.thumbnailUrl.isNullOrBlank()) AsyncImage(model = song.thumbnailUrl, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                com.music.bitchord.ui.components.ExplicitSongTitle(song = song, color = chromeWhite(0.92f), style = MaterialTheme.typography.titleMedium)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = chromeWhite(0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (large) song.durationText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = chromeWhite(0.6f))
                }
            }
            if (!large) song.durationText?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.width(8.dp));Text(it, fontSize = 13.sp, color = chromeWhite(0.6f), maxLines = 1)
            }
        }
        DropdownMenu(expanded = menuOpen && !locked, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.spool_related_play_now)) }, onClick = { menuOpen = false; if (!currentLocked) onPlay() }, enabled = !locked)
            DropdownMenuItem(text = { Text(stringResource(Res.string.spool_related_add_to_queue)) }, onClick = { menuOpen = false; if (!currentLocked) onAdd() }, enabled = !locked)
        }
    }
}
