package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics

/**
 * The v6 switch: a small pill track with a round knob, which stretches a
 * little toward where it is going while pressed. Approved 21:06 with the
 * settings redesign: ON is the page accent (near-white on dark, near-black on
 * light) with an on-accent knob; OFF is a faint neutral fill with a tertiary
 * knob. State reads from both the knob's side and the track's contrast, never
 * from colour alone - and never green.
 *
 * Plain drawing only — a track, a knob, a shadow. Every settings page has a
 * column of these, and anything heavier per switch is paid that many times
 * over on every scroll frame.
 *
 * It takes Material3's `Switch` call shape so a settings row adopts it by name
 * alone. [thumbContent] and [colors] are accepted and ignored: the switch
 * keeps the one approved treatment whatever the theme's accent.
 *
 * A null [onCheckedChange] makes it a read-out, the way M3's does: the row
 * around it owns the tap.
 *
 * Interactive, it takes up M3's 48dp touch target around the drawn capsule, as
 * M3's does. The settings rows were spaced around that height — a sub-row has
 * no top padding of its own — and without it they close up on each other.
 *
 * It buzzes when [checked] flips rather than when it is tapped: most rows also
 * toggle from their own `onClick`, which never passes through here, and the
 * value changing is the one thing every path has in common.
 */
val LocalNeutralSwitchMetrics = staticCompositionLocalOf { false }

@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: SwitchColors? = null,
) {
    val modern = LocalNeutralSwitchMetrics.current
    val trackWidth = if (modern) 48.dp else TrackWidth
    val trackHeight = if (modern) 29.dp else TrackHeight
    val thumbWidth = if (modern) 24.dp else ThumbWidth
    val thumbHeight = if (modern) 24.dp else ThumbHeight
    val thumbPadding = if (modern) 2.5.dp else ThumbPadding
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
        label = "iosSwitchThumb",
    )
    val stretch by animateFloatAsState(
        targetValue = if (pressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "iosSwitchStretch",
    )
    val track by animateColorAsState(
        targetValue = when {
            checked && dark -> Color(0xFFF1F1F1)
            checked -> Color(0xFF1B1B1B)
            modern && dark -> Color.White.copy(alpha = 0.17f)
            modern -> Color.Black.copy(alpha = 0.12f)
            dark -> Color.White.copy(alpha = 0.10f)
            else -> Color.Black.copy(alpha = 0.06f)
        },
        label = "iosSwitchTrack",
    )
    val knob by animateColorAsState(
        targetValue = when {
            checked && dark -> Color(0xFF111111)
            checked -> Color.White
            modern && dark -> Color(0xFFD8D8D8)
            modern -> Color.White
            dark -> Color(0xFF7D7D7D)
            else -> Color(0xFF919191)
        },
        label = "iosSwitchKnob",
    )
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val haptics = rememberHaptics()
    // Skips the first run, so a switch scrolling into view stays quiet.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (settled) haptics.play(if (checked) Haptic.ToggleOn else Haptic.ToggleOff)
        settled = true
    }

    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .then(
                if (onCheckedChange != null) {
                    Modifier.minimumInteractiveComponentSize().toggleable(
                        value = checked,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            )
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val travel = (trackWidth - thumbWidth - thumbPadding * 2).toPx()
                    val x = thumbPadding.toPx() + travel * position
                    translationX = if (ltr) x else -x
                    // Grows from the edge it is leaving, so the stretch reads
                    // as reaching toward the other side.
                    transformOrigin = TransformOrigin(if (checked == ltr) 1f else 0f, 0.5f)
                    scaleX = stretch
                }
                .size(thumbWidth, thumbHeight)
                .background(knob, CircleShape),
        )
    }
}

private val TrackWidth = 44.dp
private val TrackHeight = 26.dp
private val ThumbWidth = 20.dp
private val ThumbHeight = 20.dp
private val ThumbPadding = 3.dp
