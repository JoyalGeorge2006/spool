package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import com.music.bitchord.ui.icons.SfIcons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import com.music.bitchord.R
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.theme.stableStatusBarInsets
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

/**
 * The bar's own height, above whatever inset it is sitting under.
 *
 * The single source of truth for it: the bar lays itself out to this, and
 * everything that has to clear the bar — page content padding, either top
 * backdrop, fixed headers that sit directly beneath it — measures from here
 * rather than from a copy of the number.
 */
val TopBarContentHeight = 52.dp

/**
 * The breathing room between the bar's bottom edge and the first thing under
 * it, so content rests below the glass instead of against it.
 */
val TopBarContentGap = 12.dp

/**
 * How much of each end of the bar is spoken for, so a long title truncates
 * instead of running under what sits there.
 *
 * Only ever consumed through the larger of the two — see the title's padding.
 * They are kept apart rather than collapsed into one number because they
 * describe two different things, and the wordmark is the one that changes when
 * the logo or the Dev badge does.
 */
private val BackInset = 54.dp
private val WordmarkInset = 96.dp
private val ActionsInset = 56.dp

/** Between a filling accessory and the actions pill. */
private val ACCESSORY_GAP = 8.dp

/** What the leading end of the bar needs: a back button, or the wordmark. */
private fun leadingInset(hasBack: Boolean): Dp = if (hasBack) BackInset else WordmarkInset

/**
 * How far down the window the bar actually ends: the status bar inset it is
 * pinned under, plus its own height.
 *
 * This has to be read at composition rather than baked in as a constant — the
 * inset is a property of the device and of the window, not of the app. A phone
 * with a cutout, one without, and a freeform window with no status bar at all
 * are all different numbers, and a fixed guess is wrong on all but one of them:
 * too tight and content is clipped under the bar, too loose and every page
 * opens on a band of empty space.
 *
 * The inset is read whether the status bar is showing or not: the player can
 * hide it while it is open, and every page under the player would otherwise
 * slide up into the space it left behind.
 */
@Composable
fun topBarHeight(): Dp =
    stableStatusBarInsets().asPaddingValues().calculateTopPadding() + TopBarContentHeight

/**
 * Where page content should start: clear of the bar, plus [TopBarContentGap].
 */
@Composable
fun topBarContentPadding(): Dp = topBarHeight() + TopBarContentGap

/**
 * The top bar's content — title, back affordance, actions — over no blur of its
 * own.
 *
 * The app mounts it with [transparentBackdrop] and [artworkPageChrome] on every
 * page, in either material: no full-width pane, just floating circles over the
 * shared top gradient — liquid glass when that is on, the navbar's blur when it
 * is not. The bounded-pane path ([TopBarBlur] underneath, lower hairline, solid
 * fill under Reduce dynamic blur) is kept for previews only.
 *
 * Apple Music behaviour: the big in-list header owns the title at rest;
 * once the list scrolls, the small centered title fades in.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FrostedTopBar(
    title: String,
    controlsOnly: Boolean = false,
    rootTitle: String? = null,
    onRootHeaderMeasured: (Int) -> Unit = {},
    statusBarInsets: WindowInsets = stableStatusBarInsets(),
    // A lambda, not a value: this flips as a feed leaves its very top, and
    // reading it in the caller would recompose the whole app on that frame of
    // the scroll.
    scrolled: () -> Boolean,
    modifier: Modifier = Modifier,
    /** Leaves the whole bar transparent so the page's own top gradient is the backdrop. */
    transparentBackdrop: Boolean = false,
    /** Circular back surface and no collapsing title, for artwork-led pages. */
    artworkPageChrome: Boolean = false,
    /**
     * Artwork detail pages (album / artist / playlist / local) carry bare
     * header glyphs - no circle under the back arrow, no capsule around the
     * actions - per the approved detail references. Everywhere else keeps the
     * floating circles.
     */
    bareDetailChrome: Boolean = false,
    /** Neutral back circle for the settings home, with a 48dp touch target. */
    settingsHomeChrome: Boolean = false,
    /** Source sampled by floating top-bar surfaces when liquid glass is off. */
    backButtonHazeState: HazeState? = null,
    trailingTitle: String? = null,
    onBack: (() -> Unit)? = null,
    refreshing: Boolean = false,
    // A lambda, not a value: the drag changes every frame, and reading it in
    // the caller would recompose the whole app on each one.
    pullFraction: () -> Float = { 0f },
    /**
     * A page control filling the bar from the leading gutter to [actions] — the
     * Search tab's field — set on a pill of its own; see [AccessoryPill].
     * Root tabs have no back button there for it to collide with.
     */
    accessory: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    if (controlsOnly) {
        // Only the controls float. There is no full-width pane, scrim or title.
        Column(modifier.fillMaxWidth().onSizeChanged { onRootHeaderMeasured(it.height) }.windowInsetsPadding(statusBarInsets)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.Top) {
                if (onBack != null) HeaderActionButton(onBack, bare = false) {
                    HeaderIcon(SfIcons.ArrowBack, stringResource(R.string.back))
                }
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                ) {
                    CompositionLocalProvider(LocalHeaderBare provides false, LocalHeaderHaze provides null) {
                        actions()
                    }
                }
            }
            RefreshPuck(refreshing = refreshing, pullFraction = pullFraction)
        }
        return
    }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val frost = rememberFrostEnabled()
    val useFloatingChrome = artworkPageChrome ||
        (LocalLiquidGlassEnabled.current && isGlassSupported())
    val titleAlpha by animateFloatAsState(
        targetValue = if (scrolled() || trailingTitle != null) 1f else 0f,
        animationSpec = tween(220),
        label = "topBarTitleAlpha",
    )
    // Every bounded bar uses the same hairline, whether its pane is blurred or
    // solid. A transparent artwork page has no pane edge for a line to mark.
    val dividerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.outline.copy(
            alpha = when {
                transparentBackdrop -> 0f
                else -> 0.6f
            },
        ),
        animationSpec = tween(220),
        label = "topBarDivider",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (rootTitle != null) Modifier.onSizeChanged { onRootHeaderMeasured(it.height) }
                .background(MaterialTheme.colorScheme.background) else Modifier)
            .then(
                if (settingsHomeChrome) {
                    // A pinned settings header must cover the scrolling rows,
                    // including the system status strip. No translucent fall-off.
                    Modifier.background(
                        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.Black
                        else Color(0xFFF1F1F1),
                    )
                } else if (reduceDynamicBlur && !transparentBackdrop) {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                } else if (frost && backButtonHazeState != null && scrolled()) {
                    // Blur effects (Appearance): once the page has scrolled
                    // under the bar, the bar frosts over it. At rest the
                    // page's own top stays the backdrop, as it is today.
                    Modifier.hazeEffect(
                        state = backButtonHazeState,
                        style = SpoolFrost.style(MaterialTheme.colorScheme.surface),
                    )
                } else Modifier,
            ),
    ) {
        if (rootTitle != null) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(statusBarInsets)
                    .heightIn(min = TopBarContentHeight).padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { testTag = "root-header" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(rootTitle, style = com.music.bitchord.ui.components.v6PageTitleStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f).semantics { testTag = "root-header-title" })
                ArtworkPageActions(hazeState = backButtonHazeState, content = actions)
            }
        } else Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(statusBarInsets)
                .height(TopBarContentHeight)
                // Bare glyphs sit straight on the page's art; a surface-coloured
                // fall-off keeps the onSurface glyphs readable on bright covers.
                .then(
                    if (bareDetailChrome) {
                        Modifier.background(
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                1f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            ),
                        )
                    } else Modifier,
                ),
        ) {
            /* Temporarily hidden: keep the scrolling center-title implementation
             * intact so it can be restored without rebuilding its transitions.
             *
            // Artwork pages keep their large in-page heading and never create a
            // duplicate title in the status bar, including in accessibility.
            if (!artworkPageChrome) {
                AnimatedContent(
                    targetState = trailingTitle,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(260)) + slideInHorizontally(animationSpec = tween(260)) { it / 3 }) togetherWith
                            (fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { -it / 3 })
                    },
                    label = "topBarTitleAnimation",
                    modifier = Modifier
                        .align(Alignment.Center)
                        // Reserve room for whatever flanks the title — and reserve
                        // the *same* room on both sides. Equal is the whole point:
                        // the title is centered within this padded box, so an inset
                        // that differs end to end moves it off the bar's centre by
                        // half that difference. Reserving what each side actually
                        // needs (96dp for the wordmark, 56dp for the actions) put
                        // every root tab's title 20dp right of centre, which is
                        // visible against a status bar clock that is not.
                        .padding(horizontal = max(leadingInset(onBack != null), ActionsInset))
                        .fillMaxWidth()
                        .graphicsLayer { alpha = titleAlpha },
                ) { trailing ->
                    if (trailing != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = trailing,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
             */
            // On a pushed page the back affordance is always visible, since
            // there is no large in-list header to fall back on.
            if (onBack != null) {
                HeaderActionButton(onClick = onBack, bare = bareDetailChrome,
                    hazeState = backButtonHazeState,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) {
                    HeaderIcon(SfIcons.ArrowBack, stringResource(R.string.back))
                }
            }
            // No wordmark at the leading end: the root tabs carry it above
            // their large heading instead (LargePageTitle in :sharedUi).
            // The accessory takes everything from the leading gutter up to the
            // actions, which keep their own width at the trailing end.
            val edge = if (useFloatingChrome) BAR_GUTTER else 4.dp
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .then(if (accessory != null) Modifier.fillMaxWidth().padding(start = edge) else Modifier)
                    // The page's gutter (the navbar keeps Apple's wider one);
                    // the pill's inset below is internal padding around the
                    // icons, not extra margin.
                    .padding(end = edge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (accessory != null) {
                    Box(Modifier.weight(1f)) {
                        AccessoryPill(hazeState = backButtonHazeState, content = accessory)
                    }
                    Spacer(Modifier.width(ACCESSORY_GAP))
                }
                if (useFloatingChrome && !bareDetailChrome) {
                    ArtworkPageActions(hazeState = backButtonHazeState, content = actions)
                } else if (bareDetailChrome) {
                    ArtworkPageActions(hazeState = backButtonHazeState, bare = true, content = actions)
                } else {
                    actions()
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = androidx.compose.ui.graphics.Color.Transparent)
        RefreshPuck(refreshing = refreshing, pullFraction = pullFraction)
    }
}

/** Separated controls: each action owns one surface, never a shared capsule. */
@Composable
private fun ArtworkPageActions(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    bare: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHeaderBare provides bare,
        LocalHeaderHaze provides hazeState,
        LocalMinimumInteractiveComponentSize provides 48.dp) {
        ActionsRow(modifier = modifier, content = content)
    }
}

/** Geometry is shared; artwork pages keep their approved bare-glyph surface. */
internal val LocalHeaderBare = staticCompositionLocalOf { false }

/** The page's haze, handed to bare header circles so Blur effects frosts them. */
internal val LocalHeaderHaze = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun HeaderActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bare: Boolean = LocalHeaderBare.current,
    /** The page's haze; the local from the enclosing ArtworkPageActions otherwise. */
    hazeState: HazeState? = null,
    content: @Composable () -> Unit,
) {
    val haze = hazeState ?: LocalHeaderHaze.current
    val frost = rememberFrostEnabled()
    IconButton(onClick = onClick, enabled = enabled,
        modifier = modifier.size(48.dp).semantics { testTag = "header-action-target" }) {
        Box(Modifier.size(40.dp).clip(CircleShape)
            .then(
                when {
                    // A frosted circle over the page's art while Blur effects
                    // is on; the approved bare glyph when it is off.
                    bare && frost && haze != null ->
                        Modifier.hazeEffect(haze, SpoolFrost.style(MaterialTheme.colorScheme.surface))
                    bare -> Modifier
                    else -> Modifier.background(MaterialTheme.colorScheme.surface)
                }
            )
            .semantics { testTag = if (bare) "header-action-bare" else "header-action-surface" },
            contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun HeaderIcon(imageVector: ImageVector, contentDescription: String?,
    modifier: Modifier = Modifier.size(20.dp), tint: Color = MaterialTheme.colorScheme.onSurface) {
    Icon(imageVector, contentDescription, modifier, tint)
}

/**
 * The actions side by side, centred on the pill's height, with Apple's gap
 * between each and the one before it.
 *
 * Each gap opens with the width of the action after it, so one sliding in from
 * nothing — the download ring — brings its gap with it rather than the row
 * jumping by a gap's width the frame it appears.
 */
@Composable
private fun ActionsRow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val target = TOP_CONTROL_SIZE.roundToPx()
        val gap = ACTION_GAP.toPx()
        val gaps = placeables.mapIndexed { index, placeable ->
            if (index == 0) 0 else (gap * (placeable.width.toFloat() / target).coerceIn(0f, 1f)).roundToInt()
        }
        val width = placeables.sumOf { it.width } + gaps.sum()
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var x = 0
            placeables.forEachIndexed { index, placeable ->
                x += gaps[index]
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width
            }
        }
    }
}

/**
 * The bar's accessory — the Search tab's field — as a rounded block at the
 * height of the controls beside it. Blur effects frosts it over the page like
 * the bars; otherwise the field's own solid fill. Cornered like the app's
 * other inputs, not a pill.
 */
@Composable
private fun AccessoryPill(hazeState: HazeState?, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val frost = rememberFrostEnabled()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOP_CONTROL_SIZE)
            .clip(shape)
            .then(
                if (frost && hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = SpoolFrost.style(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                },
            ),
    ) {
        CompositionLocalProvider(LocalSearchFieldOnSurface provides true, content = content)
    }
}

/**
 * The account affordance at the right end of the bar.
 *
 * It is the signed-in Google account's own photo — the same one YouTube Music
 * shows there — and tapping it opens Settings, where the account lives. Signed
 * out, or before the account menu has come back, it falls back to a person
 * glyph on a filled circle so the tap target never disappears.
 *
 * The hairline ring is what keeps a photo with light edges from dissolving into
 * the bar's glass; it is the same one thumbnails elsewhere carry.
 */
@Composable
fun TopBarAccountButton(
    account: Account?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The Home title row's treatment: the same plain 40dp circle as the
     * history button beside it - no ring, so the pair reads as one set.
     */
    plain: Boolean = false,
) {
    // Wrapped in an IconButton so it keeps the touch target, the ripple and the
    // spacing every other action in this bar has.
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).semantics { testTag = "header-action-target" },
    ) {
        val photo = account?.thumbnailUrl
        val bare = LocalHeaderBare.current
        val haze = LocalHeaderHaze.current
        val frost = rememberFrostEnabled()
        Box(Modifier.size(40.dp).clip(CircleShape)
            .then(
                when {
                    photo != null -> Modifier
                    // Bare on an artwork page: frosted while Blur effects is
                    // on, the bare glyph when it is off.
                    bare && frost && haze != null ->
                        Modifier.hazeEffect(haze, SpoolFrost.style(MaterialTheme.colorScheme.surface))
                    bare -> Modifier
                    else -> Modifier.background(MaterialTheme.colorScheme.surface)
                }
            )
            .semantics { testTag = "header-profile-surface" }, contentAlignment = Alignment.Center) {
            if (photo != null) AsyncImage(model = photo,
                contentDescription = stringResource(R.string.settings), contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape))
            else HeaderIcon(Icons.Rounded.Person, stringResource(R.string.settings))
        }
    }
}

/**
 * The refresh indicator: a round puck that slides out from under the bar's
 * bottom edge as the list is pulled, Chrome-style.
 *
 * It is drawn by the bar rather than by the pull-to-refresh box, so it lands on
 * top of the glass instead of behind it, and it is clipped to the band just
 * below the bar so it emerges from the edge instead of appearing over the
 * status bar. Its position is the pull state's own distance fraction — which
 * the pull box holds at 1 while refreshing and animates back to 0 when done —
 * so the slide down, the rest while loading and the slide back up are all one
 * value. The arc fills with the drag, then spins once the refresh is away.
 *
 * Laid out at zero height, so the bar keeps its size and the puck overhangs
 * the content without taking any touches from it.
 */
@Composable
private fun RefreshPuck(refreshing: Boolean, pullFraction: () -> Float, modifier: Modifier = Modifier) {
    val currentFraction by rememberUpdatedState(pullFraction)
    val pulling by remember { derivedStateOf { currentFraction() > 0f } }
    if (!refreshing && !pulling) return

    val travel = with(LocalDensity.current) { (PUCK_REST + PUCK_SIZE).toPx() }
    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                layout(placeable.width, 0) { placeable.place(0, 0) }
            }
            .fillMaxWidth()
            .height(PUCK_REST + PUCK_SIZE + PUCK_OVERSHOOT + 12.dp)
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val f = currentFraction().coerceIn(0f, 2f)
                    // Past the threshold the puck keeps following, but
                    // reluctantly, like the list under it.
                    val eased = if (f <= 1f) f else 1f + (f - 1f) * 0.35f
                    translationY = travel * eased - PUCK_SIZE.toPx()
                    alpha = (f * 3f).coerceAtMost(1f)
                }
                .size(PUCK_SIZE)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val indicatorModifier = Modifier.size(22.dp)
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = indicatorModifier,
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round,
                )
            } else {
                CircularProgressIndicator(
                    progress = { (currentFraction() * 0.8f).coerceIn(0f, 0.8f) },
                    modifier = indicatorModifier.graphicsLayer {
                        rotationZ = currentFraction().coerceIn(0f, 2f) * 180f
                    },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
            }
        }
    }
}

private val PUCK_SIZE = 40.dp

/**
 * The floating top controls' height — the back circle and the actions pill
 * alike — and each action's slot in the pill: Apple's 44pt.
 */
private val TOP_CONTROL_SIZE = 48.dp

/**
 * Between two actions in the pill, and from each end to the action there,
 * measured off Apple Music's share-and-more pill: 101pt wide, its glyphs
 * centred 23.4pt in from the ends and 54pt apart.
 */
private val ACTION_GAP = 8.dp
private val ACTION_EDGE_INSET = 1.5.dp

/** How far below the bar's edge the puck rests while a refresh runs. */
private val PUCK_REST = 16.dp

/** Headroom for the damped travel past the threshold. */
private val PUCK_OVERSHOOT = 20.dp

/**
 * The account photo's diameter.
 *
 * Smaller than an icon's 24dp box: a filled circle carries more weight than a
 * glyph does, and at 24 it sat heavier in the bar than the wordmark opposite it.
 */
private val AVATAR_SIZE = 28.dp

/** The Home title row's account circle, matched to the history button. */
private val PLAIN_AVATAR_SIZE = 40.dp

/** Approved Listen/Search controls. Opaque, no haze, no title, two separate 48dp targets. */
@Composable
fun FloatingHistoryProfileCapsule(
    account: Account?,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics { testTag = "floating-history-profile" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onHistory, modifier = Modifier.size(48.dp)) {
            Icon(androidx.compose.material.icons.Icons.Rounded.History,
                stringResource(R.string.listening_history),
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
        TopBarAccountButton(account = account, onClick = onProfile, plain = true)
    }
}
