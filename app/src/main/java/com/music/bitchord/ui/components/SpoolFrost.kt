package com.music.bitchord.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * Spool's frost material, in one place so every frosted surface draws the same
 * glass. The recipe itself lives in sharedUi's [FrostMaterial] so the player
 * world's drawers and popups draw the identical material; this object keeps
 * the app module's name and settings-backed gate.
 *
 * Solid fallback: the container colour at full opacity, drawn by Haze where
 * blur cannot run, and by callers for the Blur effects switch off or below
 * Android 12.
 */
/** Identity of the unchanged page capture, selected only by explicitly approved sibling effects. */
internal object PageFrostSourceKey

object SpoolFrost {
    const val DarkTintAlpha = FrostMaterial.DarkTintAlpha
    const val LightTintAlpha = FrostMaterial.LightTintAlpha
    val BlurRadius = FrostMaterial.BlurRadius

    fun style(containerColor: Color): HazeStyle = FrostMaterial.style(containerColor)
}

/**
 * True when the Blur effects switch (Settings - Appearance) is on and this
 * phone can run real backdrop blur (Android 12+). Every frosted surface gates
 * on this, so the switch and the solid fallback behave the same everywhere.
 */
@Composable
fun rememberFrostEnabled(): Boolean {
    val blurEffects by AppSettings.blurEffects.collectAsStateWithLifecycle()
    return blurEffects && isGlassSupported()
}

/**
 * The alert/sheet card material: frosts over the page's own haze when Blur
 * effects is on and this window can sample it, else the solid container fill.
 * In a separate window (a real Popup/Dialog the haze cannot reach) Haze draws
 * the style's opaque fallback tint, which is the same solid fill.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.frostedCard(
    hazeState: HazeState?,
    container: Color,
    shape: Shape,
    style: HazeStyle? = null,
    pageSourceKey: Any? = null,
): Modifier {
    val frost = rememberFrostEnabled()
    return clip(shape).then(
        if (frost && hazeState != null) {
            Modifier.hazeEffect(state = hazeState, style = style ?: SpoolFrost.style(container)) {
                canDrawArea = if (pageSourceKey != null) ({ area -> area.key === pageSourceKey }) else null
            }
        } else {
            Modifier.background(container)
        },
    )
}
