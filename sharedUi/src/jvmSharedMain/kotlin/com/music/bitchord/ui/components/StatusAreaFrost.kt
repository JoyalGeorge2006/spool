package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Coordinates are fractions of the approved 720px-wide portrait reference. */
object StatusAreaFrostGeometry {
    const val ReferenceWidth = 720f
    const val End = 93f
    const val Blur = 19f
    const val FullStrengthEnd = 47f

    fun tintAlpha(t: Float): Float {
        val y = t.coerceIn(0f, 1f)
        return 0.90f * (1f - y * y * y)
    }

    fun compositeAlpha(t: Float): Float {
        val y = t.coerceIn(0f, 1f) * End
        return if (y <= FullStrengthEnd) 1f else ((End - y) / (End - FullStrengthEnd)).coerceIn(0f, 1f)
    }

    fun endForWidth(width: Float, inset: Float): Float =
        if (inset <= 0f) 0f else maxOf(inset, width * End / ReferenceWidth)
}

/**
 * A drawing-only window-top layer. It does not add insets, move controls or
 * intercept touches. The window owner keeps its existing raw inset/touch guard.
 * It deliberately does not read the page's Blur-effects switch: this status
 * material is the same with that switch on or off.
 *
 * The reference blur is mapped through measured window width and density, not
 * interpreted as 19dp. Device pixels still decide its final visual calibration.
 */
@Composable
fun StatusAreaFrost(
    hazeState: HazeState?,
    statusInset: Dp,
    windowWidth: Dp,
    windowHeight: Dp,
    modifier: Modifier = Modifier,
    dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
) {
    if (statusInset.value <= 0f) return
    val shortEdge = minOf(windowWidth, windowHeight)
    val height = Dp(StatusAreaFrostGeometry.endForWidth(shortEdge.value, statusInset.value))
    val neutral = if (dark) Color.Black else Color.White
    val density = LocalDensity.current
    val heightPx = with(density) { height.toPx() }
    val tint = remember(neutral, heightPx) {
        Brush.verticalGradient(
            colorStops = Array(33) { i ->
                val t = i / 32f
                t to neutral.copy(alpha = StatusAreaFrostGeometry.tintAlpha(t))
            },
            endY = heightPx,
        )
    }
    val effectMask = remember(heightPx) {
        Brush.verticalGradient(
            0f to Color.White,
            (StatusAreaFrostGeometry.FullStrengthEnd / StatusAreaFrostGeometry.End) to Color.White,
            1f to Color.Transparent,
            endY = heightPx,
        )
    }
    val fallback = remember(neutral, heightPx) {
        Brush.verticalGradient(
            colorStops = Array(33) { i ->
                val t = i / 32f
                t to neutral.copy(alpha = StatusAreaFrostGeometry.tintAlpha(t) * StatusAreaFrostGeometry.compositeAlpha(t))
            },
            endY = heightPx,
        )
    }
    val style = remember(neutral, tint, shortEdge) {
        HazeStyle(
            backgroundColor = neutral,
            blurRadius = shortEdge * (StatusAreaFrostGeometry.Blur / StatusAreaFrostGeometry.ReferenceWidth),
            noiseFactor = 0f,
            tints = listOf(
                HazeTint(Color.Gray, BlendMode.Saturation),
                HazeTint(tint),
            ),
            fallbackTint = HazeTint(tint),
        )
    }
    Box(
        modifier.fillMaxWidth().height(height)
            .then(if (hazeState != null) Modifier.hazeEffect(hazeState, style) {
                blurEnabled = true
                this.mask = effectMask
            } else Modifier.background(fallback))
            .testTag("status-area-frost"),
    )
}
