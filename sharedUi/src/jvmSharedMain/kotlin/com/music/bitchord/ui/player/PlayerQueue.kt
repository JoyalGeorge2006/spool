package com.music.bitchord.ui.player

import com.music.bitchord.ui.components.trackArtSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.components.ExplicitSongTitle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.alpha
import kotlin.math.abs
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import coil3.compose.AsyncImage
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.autoplaySectionStart
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swallows whatever scroll the queue list itself didn't use. The player is a
 * ModalBottomSheet, and the sheet's own nested-scroll handler reads that
 * leftover as "drag me down" — so scrolling the queue would slide the player
 * away. Consuming it here keeps the gesture inside the list.
 *
 * A downward *fling* has to be caught in the pre-phase, before the sheet sees
 * it, but only at the top of the list — otherwise the queue could never fling.
 */
internal fun keepScrollInList(listState: LazyListState, allowParentScroll: Boolean = false, allowHorizontalParent: Boolean = false) = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (allowParentScroll) Offset.Zero else if (allowHorizontalParent) Offset(0f, available.y) else available

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (!allowParentScroll && available.y > 0f && !listState.canScrollBackward) {
            if (allowHorizontalParent) Velocity(0f, available.y) else available
        } else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (allowParentScroll) Velocity.Zero else if (allowHorizontalParent) Velocity(0f, available.y) else available
}

/**
 * How a queue row moves when the running order changes under it.
 *
 * Placement only — the fades are off deliberately. A row Compose treats as
 * removed or inserted goes on being drawn at the slot it used to hold for as
 * long as it takes to fade, and a queue row's background is transparent: the
 * fading copy and whichever row is sliding through that slot both draw their
 * title and artist into the same few pixels, which reads as one song's name
 * printed over another's rather than as anything moving. Without the fades a
 * row that leaves is gone the moment it leaves, so movement is the only thing
 * left to see.
 *
 * Bounded rather than the default spring for a related reason: every track
 * change moves the section boundary — see [autoplaySectionStart] — so the
 * newly current row and the AutoPlay heading trade places, and a low-stiffness
 * spring is still visibly settling that trade long after the track changed.
 */
private val QUEUE_ROW_MOTION = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)

/** Softens the list where it meets the header and the scrubber. */
internal fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** One track in [InlineQueue], and where it sits in the player's timeline. */
internal data class QueueTrack(val timelineIndex: Int, val song: Song, val key: String)

/** The groups a queue row can be dragged within; a drag never leaves its own. */
internal enum class QueueSection { USER, CONTEXT, AUTOPLAY }

/** The queue as [InlineQueue] lists it: what's playing, then each section in running order. */
internal class QueueTracks(
    val nowPlaying: QueueTrack?,
    val user: List<QueueTrack>,
    val context: List<QueueTrack>,
    val autoplay: List<QueueTrack>,
    val history: List<QueueTrack> = emptyList(),
) {
    operator fun get(section: QueueSection): List<QueueTrack> = when (section) {
        QueueSection.USER -> user
        QueueSection.CONTEXT -> context
        QueueSection.AUTOPLAY -> autoplay
    }

    companion object {
        val EMPTY = QueueTracks(null, emptyList(), emptyList(), emptyList())
    }
}

internal fun splitQueue(queue: List<Song>, currentIndex: Int, showHistory: Boolean = false): QueueTracks {
    if (currentIndex !in queue.indices) return QueueTracks.EMPTY
    // Keyed by the entry's own id, so a row keeps its identity through a reorder.
    // Entries without one (a party's tracks, a queue restored from before ids
    // existed) fall back to the song plus how many times it has come up so far
    // — never its position, which changes on every swap and would end the drag.
    val seen = HashMap<String, Int>()
    val occurrence = IntArray(queue.size) { i ->
        val id = queue[i].videoId
        seen.getOrDefault(id, 0).also { seen[id] = it + 1 }
    }
    // A lazy list throws on a repeated key, and two rows can share an entry id
    // (a queue restored twice, an add replayed); the repeat gets a suffix
    // instead of taking the whole player down.
    val usedKeys = HashSet<String>()
    fun track(index: Int, prefix: String): QueueTrack {
        val song = queue[index]
        val base = prefix + "_" + (song.queueEntryId ?: "${song.videoId}#${occurrence[index]}")
        var key = base
        var repeat = 1
        while (!usedKeys.add(key)) key = base + "~" + repeat++
        return QueueTrack(index, song, key)
    }
    val user = ArrayList<QueueTrack>()
    val context = ArrayList<QueueTrack>()
    val autoplay = ArrayList<QueueTrack>()
    for (index in currentIndex + 1 until queue.size) {
        when (queue[index].queueTier) {
            QueueTier.USER_QUEUE -> user += track(index, "user")
            QueueTier.CONTEXT -> context += track(index, "context")
            QueueTier.AUTOPLAY -> autoplay += track(index, "autoplay")
        }
    }
    val history = if (showHistory) (0 until currentIndex).map { track(it, "history") } else emptyList()
    return QueueTracks(track(currentIndex, "np"), user, context, autoplay, history)
}

/** Placement-only item motion; see [QUEUE_ROW_MOTION]. */
private fun LazyItemScope.queueMotion(): Modifier =
    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = QUEUE_ROW_MOTION)

/** The live queue, in the player itself. */
@Composable
internal fun InlineQueue(
    queue: List<Song>,
    currentIndex: Int,
    autoplayEnabled: Boolean,
    /**
     * Read-only: the party's running order is the host's while this is set, so
     * the queue is here to be looked at and scrolled, not worked.
     */
    controlsLocked: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onScrollingChange: (Boolean) -> Unit = {},
    onDragActiveChange: (Boolean) -> Unit = {},
    /** Phone only: let queue scrolling dismiss/restore the lower half player. */
    collapsePlayerOnScroll: Boolean = false,
    onRevealPlayer: () -> Unit = {},
    onHidePlayer: () -> Unit = {},
    /**
     * Drawn under the player's tab row: the page title row is dropped (the tab
     * names the page). [relatedOnly] opens read-only current-track discovery,
     * independently of the timeline's AutoPlay tier.
     */
    tabbed: Boolean = false,
    relatedOnly: Boolean = false,
    onPlayRelated: (Song) -> Unit = {},
    onAddRelated: (Song) -> Unit = {},
    onToggleAutoplay: () -> Unit = {},
    /** Nested in the flat player page: pass list-edge drag to its artwork scroll. */
    allowParentScroll: Boolean = false,
    /** Outer list inset only. Row content and touch targets keep their padding. */
    sideGutter: androidx.compose.ui.unit.Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    if (relatedOnly) {
        RelatedMusicPanel(
            song = queue.getOrNull(currentIndex),
            autoplayEnabled = autoplayEnabled,
            controlsLocked = controlsLocked,
            onPlay = onPlayRelated,
            onAdd = onAddRelated,
            onToggleAutoplay = onToggleAutoplay,
            allowParentScroll = allowParentScroll,
            sideGutter = sideGutter,
            onScrollingChange = onScrollingChange,
            modifier = modifier,
        )
        return
    }
    // The reference pads the rows 16dp, under the page's tabs and under the
    // player's gutter alike — the bleed below reaches the row past the player's
    // column, and this is the inset the row's own contents sit at.
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect(onScrollingChange)
    }
    val paging = LocalPlayerPageGestures.current
    val keepScroll = remember(listState, allowParentScroll, paging) { keepScrollInList(listState, allowParentScroll, paging) }
    val controlsOnScroll = rememberPlayerControlsOnScroll(
        enabled = collapsePlayerOnScroll,
        onReveal = onRevealPlayer,
        onHide = onHidePlayer,
    )
    val showHistory by PlayerSettings.showQueueHistory.collectAsStateWithLifecycle()
    val tracks = remember(queue, currentIndex, showHistory) { splitQueue(queue, currentIndex, showHistory) }
    val drag = rememberQueueDrag(listState, tracks, onMove, onDragActiveChange)
    val nowPlaying = tracks.nowPlaying
    val contextSong = tracks.context.firstOrNull()?.song
    val contextTitle = contextSong?.playbackSource?.takeIf { it.isNotBlank() }
        ?: contextSong?.albumName?.takeIf { it.isNotBlank() }
        ?: nowPlaying?.song?.playbackSource?.takeIf { it.isNotBlank() }
        ?: nowPlaying?.song?.albumName?.takeIf { it.isNotBlank() }

    // Keep Now playing in view; history is available by scrolling upwards.
    // Include the entry key because trimming history can leave the index unchanged.
    var hasScrolledOnce by remember { mutableStateOf(false) }
    LaunchedEffect(relatedOnly) { if (relatedOnly) listState.scrollToItem(0) }
    LaunchedEffect(currentIndex, nowPlaying?.key, showHistory, relatedOnly) {
        val nowPlayingRow = if (tracks.history.isEmpty()) 0 else tracks.history.size + 1
        val scrolledAway = listState.firstVisibleItemIndex != nowPlayingRow || listState.firstVisibleItemScrollOffset != 0
        if (!relatedOnly && drag.held == null && nowPlaying != null && scrolledAway) {
            if (hasScrolledOnce) {
                listState.animateScrollToItem(nowPlayingRow)
            } else {
                listState.scrollToItem(nowPlayingRow)
                hasScrolledOnce = true
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        if (!tabbed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // As the list below: past the player's column, then the
                    // reference inset, so the heading and the rows share a left edge.
                    .bleedHorizontally(PLAYER_GUTTER)
                    .padding(horizontal = sideGutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.queue),
                    // The flat theme's queue header reads as a page title.
                    style = if (LocalPlayerFlat.current) {
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = chromeWhite(1f),
                    modifier = Modifier.weight(1f),
                )
                if (tracks.user.isNotEmpty()) {
                    QueueClearButton(MaterialTheme.typography.titleMedium, controlsLocked, onClear)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!tabbed || LocalPlayerFlat.current) Modifier.bleedHorizontally(PLAYER_GUTTER) else Modifier)
                .fadingEdges(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    // Without this the sheet treats the list's leftover scroll as a
                    // drag on itself and slides the whole player away.
                    .nestedScroll(keepScroll)
                    .then(if (collapsePlayerOnScroll) Modifier.nestedScroll(controlsOnScroll) else Modifier),
                contentPadding = PaddingValues(start = sideGutter, end = sideGutter, bottom = 18.dp),
            ) {
                if (!relatedOnly && tracks.history.isNotEmpty()) {
                    item(key = "header-history") {
                        QueueHeading(
                            title = stringResource(Res.string.previously_played),
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                        )
                    }
                    items(tracks.history, key = { it.key }) { track ->
                        InlineQueueRow(
                            song = track.song,
                            isCurrent = false,
                            onClick = { onJumpTo(track.timelineIndex) },
                            onRemove = { onRemove(track.timelineIndex) },
                            locked = controlsLocked,
                            modifier = queueMotion(),
                        )
                    }
                }
                if (!relatedOnly && nowPlaying != null) {
                    item(key = "header-now-playing") {
                        QueueHeading(
                            title = stringResource(Res.string.now_playing),
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                        )
                    }
                    item(key = nowPlaying.key) {
                        InlineQueueRow(
                            song = nowPlaying.song,
                            isCurrent = true,
                            onClick = { onJumpTo(nowPlaying.timelineIndex) },
                            onRemove = { onRemove(nowPlaying.timelineIndex) },
                            locked = controlsLocked,
                            modifier = queueMotion(),
                        )
                    }
                }
                if (!relatedOnly && tracks.user.isNotEmpty()) {
                    item(key = "header-user-queue") {
                        QueueHeading(
                            title = stringResource(Res.string.next_in_queue),
                            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).then(queueMotion()),
                        ) {
                            QueueClearButton(MaterialTheme.typography.labelLarge, controlsLocked, onClear)
                        }
                    }
                    queueSection(tracks.user, QueueSection.USER, drag, controlsLocked, onJumpTo, onRemove)
                }
                if (!relatedOnly && tracks.context.isNotEmpty()) {
                    item(key = "header-context") {
                        QueueHeading(
                            title = if (contextTitle != null) {
                                stringResource(Res.string.next_from, contextTitle)
                            } else {
                                stringResource(Res.string.queue)
                            },
                            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).then(queueMotion()),
                        )
                    }
                    queueSection(tracks.context, QueueSection.CONTEXT, drag, controlsLocked, onJumpTo, onRemove)
                }
                if (autoplayEnabled || tracks.autoplay.isNotEmpty()) {
                    item(key = "autoplay-heading") {
                        AutoplayHeading(hasTracks = tracks.autoplay.isNotEmpty(), modifier = queueMotion())
                    }
                    queueSection(tracks.autoplay, QueueSection.AUTOPLAY, drag, controlsLocked, onJumpTo, onRemove, reorderable = !relatedOnly)
                }
            }
            // The held row, drawn over the list where the finger is. Positioned
            // from the finger alone, so nothing the list does underneath — a
            // swap, a relayout, a scroll — can move it.
            drag.held?.let { held ->
                Box(Modifier.matchParentSize().padding(horizontal = sideGutter)) {
                    InlineQueueRow(
                        song = held.song,
                        isCurrent = false,
                        onClick = {},
                        onRemove = {},
                        draggable = true,
                        lifted = true,
                        modifier = Modifier.offset { IntOffset(0, drag.heldTop.roundToInt()) },
                    )
                }
            }
        }
    }
}

/**
 * One section's rows. The row being dragged stays in the list as an invisible
 * stand-in: it keeps the gesture and trades slots with its neighbours, which
 * slide aside, while the copy the user sees is drawn over the list by
 * [InlineQueue].
 */
private fun LazyListScope.queueSection(
    rows: List<QueueTrack>,
    section: QueueSection,
    drag: QueueDrag,
    locked: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    /** False under the Related tab, which the reference draws without handles. */
    reorderable: Boolean = true,
) {
    itemsIndexed(rows, key = { _, track -> track.key }) { index, track ->
        val held = drag.held?.key == track.key
        if (held) {
            // Scrolled far enough to be disposed, the row takes its gesture
            // with it and neither drag-end callback runs — end the drag here.
            DisposableEffect(Unit) { onDispose { drag.end(track.key) } }
        }
        InlineQueueRow(
            song = track.song,
            isCurrent = false,
            onClick = { onJumpTo(track.timelineIndex) },
            onRemove = { onRemove(track.timelineIndex) },
            locked = locked,
            draggable = !locked && reorderable,
            onDragStart = { drag.start(track.key, section) },
            onDrag = drag::drag,
            onDragEnd = { drag.end(track.key) },
            groupFirst = index == 0,
            groupLast = index == rows.lastIndex,
            modifier = if (held) Modifier.alpha(0f) else queueMotion(),
        )
    }
}

@Composable
private fun QueueHeading(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            // Small label like the reference's section headings (11.5sp bold,
            // tracked). Uppercase via the locale-aware String call.
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            ),
            color = chromeWhite(0.6f),
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        trailing()
    }
}

@Composable
private fun QueueClearButton(style: TextStyle, locked: Boolean, onClear: () -> Unit) {
    Text(
        text = stringResource(Res.string.clear),
        style = style,
        color = chromeWhite(if (locked) 0.25f else 0.75f),
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(enabled = !locked, onClick = onClear)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun AutoplayHeading(hasTracks: Boolean, modifier: Modifier = Modifier) {
    // Compact and neutral, as the reference's AutoPlay section: a small tracked
    // label with a 16dp glyph and a one-line description, no title-sized type.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                BitChordIcons.Infinity,
                contentDescription = null,
                tint = chromeWhite(0.6f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = stringResource(Res.string.autoplay),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = chromeWhite(0.6f),
            )
        }
        Text(
            text = stringResource(
                if (hasTracks) Res.string.autoplay_queue_description else Res.string.autoplay_empty_description,
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
            color = chromeWhite(0.6f),
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * How far in from either end of the queue a held row starts scrolling the list,
 * and how fast it scrolls once it is all the way at the edge.
 *
 * The zone is a shade deeper than the 28.dp the list fades out over, so the
 * list is already moving by the time the row begins to disappear into the fade
 * rather than only once it has. The speed at the edge is about six rows a
 * second: quick enough to cross a long queue without waiting on it, slow
 * enough to still read the titles going past and stop on the right one.
 */
private val QUEUE_EDGE_SCROLL_ZONE = 40.dp
private val QUEUE_EDGE_SCROLL_SPEED = 340.dp

/**
 * The pace, in pixels a second, to scroll a list at while a row occupying
 * [top] to [bottom] is held in a viewport spanning [viewportStart] to
 * [viewportEnd] — negative towards the start of the list, positive towards its
 * end, and zero while the row is clear of both edges.
 *
 * Ramped by how far into the [zone] the row has reached, so how fast the queue
 * goes by stays the user's to choose — but from a fifth of [speed] rather than
 * from nothing, since a row just inside the zone should visibly move the list
 * instead of creeping a pixel a second until it is pushed further. A viewport
 * too short to hold the row clear of both edges at once scrolls neither way,
 * rather than picking one arbitrarily and running away with it.
 */
fun edgeScrollSpeed(
    top: Float,
    bottom: Float,
    viewportStart: Int,
    viewportEnd: Int,
    zone: Float,
    speed: Float,
): Float {
    if (zone <= 0f) return 0f
    val intoStart = (viewportStart + zone) - top
    val intoEnd = bottom - (viewportEnd - zone)
    val reach = when {
        intoStart > 0f && intoEnd <= 0f -> -intoStart
        intoEnd > 0f && intoStart <= 0f -> intoEnd
        else -> return 0f
    }
    val ramp = speed * (0.2f + 0.8f * (abs(reach) / zone).coerceAtMost(1f))
    return if (reach < 0f) -ramp else ramp
}

@Composable
private fun rememberQueueDrag(
    listState: LazyListState,
    tracks: QueueTracks,
    onMove: (Int, Int) -> Unit,
    onActiveChange: (Boolean) -> Unit,
): QueueDrag {
    val drag = remember(listState) { QueueDrag(listState) }
    drag.tracks = tracks
    drag.onMove = onMove
    drag.onActiveChange = onActiveChange
    with(LocalDensity.current) {
        drag.edgeZone = QUEUE_EDGE_SCROLL_ZONE.toPx()
        drag.edgeSpeed = QUEUE_EDGE_SCROLL_SPEED.toPx()
    }
    // Held near either edge, the row scrolls the list under itself. The finger
    // stays put, so each frame's scroll is settled as if the list had moved and
    // the row hadn't — it swaps through the rows going past on the same terms.
    val direction = drag.autoScrollDir
    LaunchedEffect(drag, direction) {
        if (direction == 0) return@LaunchedEffect
        listState.scroll {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // A dropped frame paid back in full lands as a lurch, so it isn't.
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                // Nowhere left to scroll: let go of the list rather than spin.
                if (scrollBy(drag.autoScrollSpeed * seconds) == 0f) break
                drag.scrolled()
            }
        }
    }
    return drag
}

/**
 * The one drag [InlineQueue] can have at a time.
 *
 * Everything turns on [heldCenter], where the finger holds the row's centre in
 * the list's viewport. Only the finger moves it. Swaps are read off the live
 * layout against it, each sent to the player the moment the row crosses a
 * neighbour, and the row itself is drawn at [heldTop] by an overlay — never
 * from its own slot, which jumps a whole row the frame a swap lands.
 */
private class QueueDrag(private val listState: LazyListState) {
    var tracks = QueueTracks.EMPTY
    var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** See [PartySync.beginQueueDrag]: a jam hears about the reorder once, on drop. */
    var onActiveChange: (Boolean) -> Unit = {}

    /** [QUEUE_EDGE_SCROLL_ZONE] and [QUEUE_EDGE_SCROLL_SPEED], in pixels. */
    var edgeZone = 0f
    var edgeSpeed = 0f

    /** The row being moved; null at rest. */
    var held by mutableStateOf<QueueTrack?>(null)
        private set

    /** Where the held row is drawn: its top in the viewport, kept on screen. */
    var heldTop by mutableFloatStateOf(0f)
        private set

    /** -1, 0 or 1 — state, because it starts and stops the scroll loop. */
    var autoScrollDir by mutableIntStateOf(0)
        private set

    /** Signed, px a second. Not state: only the loop reads it, once a frame. */
    var autoScrollSpeed = 0f
        private set

    private var section = QueueSection.USER
    private var heldCenter = 0f
    private var heldSize = 0

    /** Where the last swap sent will put the row, until the list shows it there. */
    private var awaiting: Int? = null

    fun start(key: String, section: QueueSection) {
        val track = tracks[section].firstOrNull { it.key == key } ?: return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        this.section = section
        heldSize = item.size
        heldCenter = item.offset + item.size / 2f
        heldTop = item.offset.toFloat()
        awaiting = null
        held = track
        onActiveChange(true)
    }

    fun drag(deltaY: Float) = settle(deltaY)

    fun scrolled() = settle(0f)

    fun end(key: String) {
        if (held?.key != key) return
        held = null
        awaiting = null
        setAutoScroll(0f)
        onActiveChange(false)
    }

    private fun settle(deltaY: Float) {
        val key = held?.key ?: return
        val rows = tracks[section]
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        val half = heldSize / 2f

        // Stopped at the ends of its own section where those are on screen,
        // rather than running on past them into rows it can't trade with.
        var center = heldCenter + deltaY
        rows.firstOrNull()?.let { first ->
            items.firstOrNull { it.key == first.key }?.let { center = center.coerceAtLeast(it.offset + half) }
        }
        rows.lastOrNull()?.let { last ->
            items.firstOrNull { it.key == last.key }?.let { center = center.coerceAtMost(it.offset + it.size - half) }
        }
        heldCenter = center
        val top = center - half
        val minTop = info.viewportStartOffset.toFloat()
        heldTop = top.coerceIn(minTop, (info.viewportEndOffset - heldSize).toFloat().coerceAtLeast(minTop))

        // Its slot is out of view: nothing to swap against until the list brings it back.
        val dragged = items.firstOrNull { it.key == key } ?: return setAutoScroll(0f)
        aimAutoScroll(top, key, rows)

        // A swap already sent but not laid out yet — deciding another off the
        // old layout would send the same one twice.
        awaiting?.let {
            if (dragged.index != it) return
            awaiting = null
        }
        val target = items
            .filter { item -> item.key != key && rows.any { it.key == item.key } }
            .minByOrNull { abs(it.offset + it.size / 2f - center) }
            ?: return
        // Half a row of overlap per swap, so a row isn't traded back and forth
        // over a single pixel of travel.
        if (abs(center - (target.offset + target.size / 2f)) > target.size / 2f) return
        // LazyColumn keeps its place by the key of its first visible row; move
        // that row while there's list above it and the whole list follows it
        // along by a row. Waiting a few frames for the scroll to bring in the
        // next row up avoids it.
        if (target.index == listState.firstVisibleItemIndex && listState.canScrollBackward) return
        val from = rows.firstOrNull { it.key == key }?.timelineIndex ?: return
        val to = rows.firstOrNull { it.key == target.key }?.timelineIndex ?: return
        onMove(from, to)
        awaiting = target.index
    }

    /** Only while there's a row that way to trade with, and list left to scroll. */
    private fun aimAutoScroll(top: Float, key: String, rows: List<QueueTrack>) {
        val info = listState.layoutInfo
        val speed = edgeScrollSpeed(
            top = top,
            bottom = top + heldSize,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
            zone = edgeZone,
            speed = edgeSpeed,
        )
        val blocked = when {
            speed < 0f -> rows.firstOrNull()?.key == key || !listState.canScrollBackward
            speed > 0f -> rows.lastOrNull()?.key == key || !listState.canScrollForward
            else -> true
        }
        setAutoScroll(if (blocked) 0f else speed)
    }

    private fun setAutoScroll(speed: Float) {
        autoScrollSpeed = speed
        val direction = when {
            speed > 0f -> 1
            speed < 0f -> -1
            else -> 0
        }
        if (autoScrollDir != direction) autoScrollDir = direction
    }
}

@Composable
internal fun InlineQueueRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    /** @see InlineQueue */
    locked: Boolean = false,
    /** Shows the handle; drags on it go to the callbacks below. */
    draggable: Boolean = false,
    /** The copy drawn under the finger while the row is being moved. */
    lifted: Boolean = false,
    groupFirst: Boolean = true,
    groupLast: Boolean = true,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    // Swipe either way to remove, through the same onRemove as the close button.
    // Horizontal only, so vertical scroll and the handle-only reorder drag are
    // untouched (the handle consumes its own drag first, which cancels this
    // gesture). The offset is plain gesture state; only the settle animates.
    // Latest callbacks/flags are read at use, and the detector is keyed on the
    // gate so a lock or lift mid-gesture cancels it and the offset resets.
    val currentOnRemove by rememberUpdatedState(onRemove)
    val currentLocked by rememberUpdatedState(locked)
    val paging = LocalPlayerPageGestures.current
    val largePagedRow = paging && LocalDensity.current.fontScale >= 1.5f
    val swipeEnabled = !locked && !lifted && !paging
    var swipeX by remember { mutableFloatStateOf(0f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(swipeEnabled) { if (!swipeEnabled) swipeX = 0f }
    // A pulse the moment the drag crosses the remove threshold: the distance
    // alone was easy to misjudge, and a row that slid and snapped back with no
    // signal read as the swipe having been ignored.
    val haptics = LocalHapticFeedback.current
    var thresholdHit by remember { mutableStateOf(false) }
    LaunchedEffect(swipeX, rowWidthPx) {
        val over = rowWidthPx > 0f && abs(swipeX) >= rowWidthPx * 0.4f
        if (over && !thresholdHit) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        thresholdHit = over
    }
    val swipeModifier = if (!swipeEnabled) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                change.consume()
                swipeX += over
            } ?: run { swipeX = 0f; return@awaitEachGesture }
            val finished = horizontalDrag(slop.id) { change ->
                swipeX += change.positionChange().x
                change.consume()
            }
            val limit = rowWidthPx * 0.4f
            // Locked is re-checked at release: a party lock taken mid-swipe
            // must not remove. A refused/ignored removal leaves the row, which
            // settles back to rest below.
            if (finished && !currentLocked && limit > 0f && abs(swipeX) >= limit) {
                currentOnRemove()
            }
            swipeX = 0f
        }
    }
    val swipeRest by animateFloatAsState(
        targetValue = swipeX,
        animationSpec = if (swipeX == 0f) tween(180) else snap(),
        label = "queueSwipe",
    )
    val grouped = LocalPlayerFlat.current
    val groupShape = RoundedCornerShape(
        topStart = if (groupFirst) 14.dp else 0.dp,
        topEnd = if (groupFirst) 14.dp else 0.dp,
        bottomStart = if (groupLast) 14.dp else 0.dp,
        bottomEnd = if (groupLast) 14.dp else 0.dp,
    )
    val groupColor = MaterialTheme.colorScheme.surface
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(if (grouped) groupShape else RoundedCornerShape(12.dp))
            .then(if (grouped) Modifier.background(groupColor).drawBehind {
                if (!groupFirst) drawLine(androidx.compose.ui.graphics.Color.Transparent,
                    Offset(62.dp.toPx(), 0f), Offset(size.width - 8.dp.toPx(), 0f), 1.dp.toPx())
            } else Modifier),
    ) {
        // Revealed as the row slides away: the remove cue the swipe is aimed
        // at. With nothing behind the row the gesture looked like it did
        // nothing at all, whatever it actually did.
        if (swipeRest != 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = if (swipeRest < 0f) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(Res.string.remove),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidthPx = it.width.toFloat() }
                .offset { IntOffset(swipeRest.roundToInt(), 0) }
            .graphicsLayer {
                alpha = if (rowWidthPx > 0f) (1f - abs(swipeRest) / rowWidthPx).coerceIn(0.3f, 1f) else 1f
            }
            .then(swipeModifier)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    lifted -> chromeWhite(0.06f)
                    // Reference: a faint card (#f7f6f4 on white, #1c1a20 on black).
                    isCurrent -> chromeWhite(if (LocalPlayerInk.current) 0.03f else 0.05f)
                    else -> Color.Transparent
                },
            )
            // Disabled rather than ignored: a tap that ripples and then does
            // nothing reads as the app having missed it.
            .clickable(enabled = !locked, onClick = onClick)
            .then(if (grouped) Modifier.heightIn(min = 56.dp) else Modifier)
            .padding(horizontal = 8.dp, vertical = if (grouped) 4.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = rememberRemoteArtworkUrl(song),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .trackArtSize(if (grouped) 44.dp else 52.dp, song.isVideo)
                    .clip(RoundedCornerShape(8.dp))
                    .thumbnailBorder(RoundedCornerShape(8.dp))
                    .background(chromeWhite(0.08f)),
            )
            if (isCurrent && LocalPlayerInk.current) {
                // The reference's `.playingeq`: the whole thumbnail dimmed, a white
                // waveform on it, no inner disc. Static, so nothing to gate.
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x70000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = stringResource(Res.string.now_playing),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            } else if (isCurrent) {
                // Static glyph over a dim: the app-wide reduce-motion policy
                // never has to gate it because nothing here animates.
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = stringResource(Res.string.now_playing),
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color(0x66000000), CircleShape)
                        .padding(3.dp),
                )
            }
        }
        Spacer(Modifier.width(if (grouped) 10.dp else 12.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) chromeWhite(1f) else chromeWhite(0.92f),
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = chromeWhite(0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Only the song's own duration text, as the reference's `.qdur`. Absent
        // when the data has none (a fixture or a stream with no length); nothing
        // is made up in its place.
        song.durationText?.takeIf { it.isNotBlank() && !largePagedRow }?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = chromeWhite(0.6f),
                maxLines = 1,
            )
        }
        if (paging && !lifted && isCurrent) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                androidx.compose.material3.IconButton(onClick = { menuOpen = true }, enabled = !locked, modifier = Modifier.testTag("queue-row-menu-${song.queueEntryId ?: song.videoId}")) {
                    Icon(Icons.Rounded.MoreHoriz, stringResource(Res.string.more), tint = chromeWhite(0.7f), modifier = Modifier.size(20.dp))
                }
                androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(Res.string.remove_from_queue)) },
                        onClick = { menuOpen = false; if (!currentLocked) currentOnRemove() }, enabled = !locked,
                    )
                }
            }
        }
        if (paging && !lifted && !isCurrent) {
            // Direct removal keeps the existing callback/Undo owner and does
            // not intercept the separate reorder handle or the row's play tap.
            androidx.compose.material3.IconButton(
                onClick = { if (!currentLocked) currentOnRemove() },
                enabled = !locked,
                modifier = Modifier.size(48.dp)
                    .testTag("queue-row-remove-${song.queueEntryId ?: song.videoId}"),
            ) {
                Icon(Icons.Rounded.Close, stringResource(Res.string.remove_from_queue),
                    tint = chromeWhite(0.7f), modifier = Modifier.size(20.dp))
            }
        }
        if (draggable) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = stringResource(Res.string.drag_to_reorder),
                tint = chromeWhite(0.4f),
                modifier = Modifier
                    // 48dp touch target around the 20dp glyph, at the row's end
                    // as the reference draws it.
                    .size(48.dp)
                    .padding(14.dp)
                    .pointerInput(Unit) {
                        // This handle reorders vertically only. An all-axis
                        // detector consumes a row-removal swipe that starts
                        // near the handle before the row can own it.
                        detectVerticalDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                onDrag(amount)
                            },
                        )
                    },
            )
        }

    }
    }
}
