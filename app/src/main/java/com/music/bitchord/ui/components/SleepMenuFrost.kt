package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import kotlin.math.max
import kotlin.math.min

/** Shared by the approved sleep-timer and standard player-panel material.
 * The separately reviewed main-menu material and unrelated dialogs are unchanged.
 * Keep the existing blur/tint, then protect only pixels beyond the safe shade:
 * Lighten leaves bright pixels unchanged; Darken leaves dark pixels unchanged.
 * Foreground children are drawn normally after Haze, never blurred or filtered.
 */
internal fun sleepMenuFrostStyle(container: Color, content: Color): HazeStyle =
    playerPanelFrostStyle(container, content)

internal fun playerPanelFrostStyle(
    container: Color,
    content: Color,
    additionalInk: List<Color> = emptyList(),
    pressedOverlayAlpha: Float = 0f,
): HazeStyle {
    val base = SpoolFrost.style(container)
    val lightInk = content.luminance() >= 0.5f
    fun contrastAt(shade: Float): Float {
        val background = Color(shade, shade, shade)
        val inks = additionalInk + content.copy(alpha = 0.85f)
        val normal = inks.minOf { contrast(it.compositeOver(background), background) }
        if (pressedOverlayAlpha == 0f) return normal
        val pressed = content.copy(alpha = pressedOverlayAlpha).compositeOver(background)
        val pressedScore = inks.minOf { contrast(it.compositeOver(pressed), pressed) }
        // Model targets: normal pixels 5:1, pressed wash 4.6:1, with rounding
        // room above 4.5:1. Final Haze/glyph output still needs device checks.
        return min(normal, pressedScore * (5f / 4.6f))
    }
    var low = 0f
    var high = 1f
    repeat(20) {
        val middle = (low + high) / 2f
        val safe = contrastAt(middle) >= 5f
        if (lightInk) {
            if (safe) low = middle else high = middle
        } else {
            if (safe) high = middle else low = middle
        }
    }
    val shade = if (lightInk) low else high
    val protection = HazeTint(
        color = Color(shade, shade, shade),
        blendMode = if (lightInk) BlendMode.Darken else BlendMode.Lighten,
    )
    return base.copy(tints = base.tints + protection)
}

private fun contrast(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}
