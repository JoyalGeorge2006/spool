@file:OptIn(ExperimentalComposeUiApi::class)

package com.music.bitchord.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.utils.containSheetGestures
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import kotlin.math.roundToInt

/** The widest any sheet gets, however wide the window is — M3's own cap. */
private val INLINE_SHEET_MAX_WIDTH = 640.dp

/**
 * The haze of whatever inline sheet or menu is hosting this composition, so a
 * dialog opened from inside one frosts against the same window its host drew
 * in. Null outside a host, or inside one whose window has no source (the
 * raised player's), where the dialog keeps its solid fill. Provided by
 * [InlineFrostSheet] and [HeldContextMenu].
 */
val LocalSheetHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * The overlay slot of the inline sheet hosting this composition, so a child
 * (Song Info from a track menu) can draw a dialog over the whole window the
 * sheet drew in - full scrim, every tap under it blocked - instead of only
 * over the menu's own bounds. Null outside an inline sheet; callers keep
 * their in-menu fallback there.
 */
val LocalSheetOverlay = staticCompositionLocalOf<MutableState<(@Composable () -> Unit)?>?> { null }

/** Dragged this share of its height down, a released sheet is dismissed. */
private const val INLINE_DISMISS_DRAG_FRACTION = 0.25f

/**
 * Hides what a sheet covers from the accessibility tree while it is open: a
 * [androidx.compose.material3.ModalBottomSheet] does this by living in a
 * window of its own, and an inline sheet has to ask for it. Apply to the page
 * container the sheet is drawn over, with the sheet's open state.
 */
fun Modifier.hiddenBehindSheet(open: Boolean): Modifier = this
    .semantics {
        // TalkBack: the covered page leaves the semantics tree entirely.
        if (open) invisibleToUser()
    }
    .focusProperties {
        // Keyboard and DPAD: two-dimensional focus moves stop at the covered
        // page's edge instead of walking into what the sheet hides.
        if (open) enter = { FocusRequester.Cancel }
    }

/**
 * A bottom sheet drawn inline, in the page's own window and composition,
 * instead of a [androidx.compose.material3.ModalBottomSheet]'s window of its
 * own — the one change that lets the sheet frost: Haze cannot sample across
 * windows, so a window sheet could only ever be a solid slab, while this one
 * blurs the page behind it through [hazeState] while Blur effects is on and
 * draws [containerColor] solid when it is off (or when it is composed inside
 * another window that has no haze source, where the caller passes null).
 *
 * Kept behaviours, matching the window sheet it replaces: a scrim that fades
 * with the drag and dismisses on a tap outside, back dismiss, drag down past
 * a quarter of the sheet's height dismisses while the list's own scroll keeps
 * its scroll (the pull reaches the sheet only past the list's top), the IME
 * and navigation bar insets, the 640dp tablet cap, reduced animation, and
 * touches contained so nothing leaks to the page underneath.
 *
 * Accessibility: the sheet is a traversal group titled [paneTitle]; callers
 * hide the covered page with [hiddenBehindSheet] on the page's container.
 *
 * Not kept, by design: the exit animation on dismiss — the caller removes the
 * sheet from composition, as every inline drawer here already behaves — and
 * the partially-expanded stop (the sheets converted to this all used
 * skipPartiallyExpanded or reached full height).
 */
@Composable
fun InlineFrostSheet(
    onDismiss: () -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    paneTitle: String? = null,
    /** Focused again when the sheet leaves composition - the opener, usually. */
    restoreFocusTo: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    /** The tallest the sheet gets, as a share of the window's height. */
    maxHeightFraction: Float = 0.92f,
    /** The grab handle every M3 sheet here wore; false for full-bleed looks. */
    dragHandle: Boolean = true,
    /** Child overlays may retain their old material independently of this frame. */
    childHazeState: HazeState? = hazeState,
    /** Optional material for a specifically approved surface, never its children. */
    frostStyle: HazeStyle? = null,
    /** Opt-in busy guard, without changing existing sheet callers. */
    dragEnabled: Boolean = true,
    /** False when an existing form host already owns hardware Back. */
    handleBack: Boolean = true,
    /** Explicit sibling-source selection for this frame only; never inherited by children. */
    pageSourceKey: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    BackHandler(enabled = handleBack, onBack = onDismiss)

    // How far the sheet has been dragged down, in pixels. Released, it either
    // springs back or goes — see [INLINE_DISMISS_DRAG_FRACTION].
    var drag by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    val canDrag by rememberUpdatedState(dragEnabled)
    LaunchedEffect(dragEnabled) { if (!dragEnabled) drag = 0f }
    val offset by animateFloatAsState(
        targetValue = drag,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "inlineSheetOffset",
    )
    // Fades with the sheet rather than staying at full strength under a sheet
    // halfway off the screen.
    val scrimAlpha = if (height > 0) (1f - offset / height).coerceIn(0f, 1f) else 1f

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // Focus the opener again when the sheet goes away, as a dismissed window
    // sheet does. Null where no opener was passed: the system picks.
    DisposableEffect(Unit) {
        onDispose {
            // The opener may already be detached after navigation; an
            // orphaned requester throws instead of no-opping.
            try { restoreFocusTo?.requestFocus() } catch (_: IllegalStateException) {}
        }
    }
    // Set by a child that needs a window-wide overlay - see [LocalSheetOverlay].
    val overlaySlot = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    // A window sheet takes the window's focus; the inline one moves it into
    // itself instead. A field on the sheet that asks for focus (the rename
    // form) composes after this effect and wins, as the window sheet behaved.
    val sheetFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { sheetFocus.requestFocus() }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * maxHeightFraction

    val dismissOnRelease: () -> Unit = {
        if (canDrag && height > 0 && drag > height * INLINE_DISMISS_DRAG_FRACTION) onDismiss() else drag = 0f
    }
    // The sheet's rows scroll, and that scroll takes the finger before the
    // drag detector below ever sees it. A pull down past the top of the list
    // moves the sheet itself, and pushing back up returns it before the list
    // scrolls again. Kept here rather than handed on to the page, which would
    // otherwise be dragged down along with the sheet.
    val sheetScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canDrag) return Offset.Zero
                if (drag <= 0f || available.y >= 0f) return Offset.Zero
                val used = available.y.coerceAtLeast(-drag)
                drag += used
                return Offset(0f, used)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!canDrag) return Offset.Zero
                if (source == NestedScrollSource.UserInput && available.y > 0f) drag += available.y
                if (drag > 0f && available.y < 0f) {
                    val used = available.y.coerceAtLeast(-drag)
                    drag += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!canDrag || drag <= 0f) return Velocity.Zero
                dismissOnRelease()
                return available
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .containSheetGestures()
            .background(SHEET_SCRIM.copy(alpha = SHEET_SCRIM.alpha * scrimAlpha))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        CompositionLocalProvider(
            LocalSheetOverlay provides overlaySlot,
            LocalSheetHaze provides childHazeState,
        ) {
        AnimatedVisibility(
            visible = shown,
            enter = if (reduceAnimation) {
                EnterTransition.None
            } else {
                slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it }
            },
            exit = if (reduceAnimation) ExitTransition.None else slideOutVertically(tween(180)) { it },
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .widthIn(max = INLINE_SHEET_MAX_WIDTH)
                    .fillMaxWidth()
                    .onSizeChanged { height = it.height }
                    .offset { IntOffset(0, offset.roundToInt()) }
                    .frostedCard(hazeState, containerColor, shape, frostStyle, pageSourceKey)
                    .semantics {
                        isTraversalGroup = true
                        paneTitle?.let { this.paneTitle = it }
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .focusRequester(sheetFocus)
                    .focusable()
                    .nestedScroll(sheetScroll)
                    .pointerInput(dragEnabled) {
                        if (dragEnabled) detectVerticalDragGestures(
                            onDragEnd = dismissOnRelease,
                            onDragCancel = { drag = 0f },
                        ) { _, delta ->
                            drag = (drag + delta).coerceAtLeast(0f)
                        }
                    }
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                if (dragHandle) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 4.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = 0.4f)),
                    )
                }
                CompositionLocalProvider(LocalContentColor provides contentColor,
                    LocalSheetHaze provides childHazeState) {
                    content()
                }
            }
        }
        // Over the sheet and its scrim alike: a full-window dialog from a
        // child of the sheet's content.
        overlaySlot.value?.invoke()
        }
    }
}

private val SHEET_SCRIM = Color.Black.copy(alpha = 0.32f)

/**
 * A centred alert drawn inline, in the page's own window and composition,
 * instead of a [androidx.compose.ui.window.Dialog]'s window of its own — the
 * same trade as [InlineFrostSheet]: the card frosts over the page through
 * [hazeState] while Blur effects is on and draws [containerColor] solid when
 * it is off. Scrim tap and back both dismiss, and the card is a traversal
 * group titled [paneTitle]; callers hide the covered page with
 * [hiddenBehindSheet].
 */
@Composable
fun InlineFrostDialog(
    onDismiss: () -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    paneTitle: String? = null,
    shape: Shape = RoundedCornerShape(ALERT_CORNER),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    width: Dp = ALERT_WIDTH,
    pageSourceKey: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)

    // See the sheet variant: the window dialog owned focus, the inline one
    // moves it into itself.
    val dialogFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { dialogFocus.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .containSheetGestures()
            .background(SHEET_SCRIM)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .frostedCard(hazeState, containerColor, shape, pageSourceKey = pageSourceKey)
                .semantics {
                    isTraversalGroup = true
                    paneTitle?.let { this.paneTitle = it }
                }
                .focusRequester(dialogFocus)
                .focusable()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .imePadding()
                .navigationBarsPadding(),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor,
                LocalSheetHaze provides hazeState) {
                content()
            }
        }
    }
}
