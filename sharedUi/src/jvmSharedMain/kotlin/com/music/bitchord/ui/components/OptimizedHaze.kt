package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/** The menu panel's charcoal, and the player's — which is dark whatever the theme. */
val MenuFrostDark = Color(0xFF1C1C1E)

/**
 * Spool's frost material, defined once in sharedUi so the app module's
 * SpoolFrost and the player world's drawers and popups all draw the same
 * glass: 24dp blur, the container colour carried in the material, tinted 60%
 * on dark containers and 55% on light — the pair the blur study's contrast
 * measurements cleared, chosen by container luminance rather than by theme,
 * because the player's fills are dark whatever the theme. The opaque fallback
 * tint is the container itself, so a window the haze cannot reach still draws
 * the solid fill.
 */
object FrostMaterial {
    const val DarkTintAlpha = 0.60f
    const val LightTintAlpha = 0.55f
    val BlurRadius = 24.dp

    fun style(containerColor: Color): HazeStyle = HazeStyle(
        blurRadius = BlurRadius,
        backgroundColor = containerColor,
        tint = HazeTint(
            containerColor.copy(
                alpha = if (containerColor.luminance() >= 0.5f) LightTintAlpha else DarkTintAlpha,
            ),
        ),
        fallbackTint = HazeTint(containerColor),
    )
}
