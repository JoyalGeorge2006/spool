package com.music.bitchord.ui.replay

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Active Material You roles for an opaque Replay card. Null at the call site
 * preserves the original Astryx card instead of borrowing wallpaper colours.
 */
@Immutable
data class ReplayCardColors(val container: Color, val content: Color) {
    /** Keep each label's hierarchy only as far as its contrast permits.
     * Pale primary tones use dark ink; deep primary tones can keep light ink.
     * The small margin above 4.5 allows for channel quantisation.
     */
    fun ink(preferredAlpha: Float = 1f): Color {
        val paired = content.copy(alpha = 1f)
        val foreground = if (contrast(paired, container) >= MIN_CONTRAST) paired else {
            if (contrast(Color.Black, container) >= contrast(Color.White, container)) Color.Black else Color.White
        }
        var low = preferredAlpha.coerceIn(0f, 1f)
        if (contrast(foreground.copy(alpha = low).compositeOver(container), container) >= MIN_CONTRAST) {
            return foreground.copy(alpha = low)
        }
        var high = 1f
        repeat(16) {
            val middle = (low + high) / 2f
            if (contrast(foreground.copy(alpha = middle).compositeOver(container), container) >= MIN_CONTRAST) {
                high = middle
            } else {
                low = middle
            }
        }
        return foreground.copy(alpha = (ceil(high * 255f) / 255f).coerceAtMost(1f))
    }
}

private const val MIN_CONTRAST = 4.6f

private fun contrast(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}
