package com.music.bitchord.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import com.music.bitchord.data.settings.ThemeStyle

/**
 * Builds a full Material 3 [ColorScheme] for one Material You [ThemeStyle] from
 * a single seed colour, using the official Material Color Utilities schemes
 * (the materialkolor fork, same HCT/2025-spec math Google ships).
 *
 * The three styles on offer are the spread the listener approved: Tonal Spot
 * (low chroma, the 2021 Material You default), Expressive (medium chroma, hue
 * rotated for variety - Spool's default) and Vibrant (maximum chroma). The
 * other five variants stay out of the picker.
 */
fun themeStyleScheme(seed: Color, dark: Boolean, style: ThemeStyle): ColorScheme {
    val sourceHct = Hct.fromInt(seed.toArgb())
    val scheme: DynamicScheme = when (style) {
        ThemeStyle.TONAL_SPOT -> SchemeTonalSpot(sourceHct, dark, 0.0)
        ThemeStyle.EXPRESSIVE -> SchemeExpressive(sourceHct, dark, 0.0)
        ThemeStyle.VIBRANT -> SchemeVibrant(sourceHct, dark, 0.0)
    }
    val colors = MaterialDynamicColors()
    fun DynamicColor.resolve(): Color = Color(getArgb(scheme))
    return ColorScheme(
        primary = colors.primary().resolve(),
        onPrimary = colors.onPrimary().resolve(),
        primaryContainer = colors.primaryContainer().resolve(),
        onPrimaryContainer = colors.onPrimaryContainer().resolve(),
        inversePrimary = colors.inversePrimary().resolve(),
        secondary = colors.secondary().resolve(),
        onSecondary = colors.onSecondary().resolve(),
        secondaryContainer = colors.secondaryContainer().resolve(),
        onSecondaryContainer = colors.onSecondaryContainer().resolve(),
        tertiary = colors.tertiary().resolve(),
        onTertiary = colors.onTertiary().resolve(),
        tertiaryContainer = colors.tertiaryContainer().resolve(),
        onTertiaryContainer = colors.onTertiaryContainer().resolve(),
        background = colors.background().resolve(),
        onBackground = colors.onBackground().resolve(),
        surface = colors.surface().resolve(),
        onSurface = colors.onSurface().resolve(),
        surfaceVariant = colors.surfaceVariant().resolve(),
        onSurfaceVariant = colors.onSurfaceVariant().resolve(),
        surfaceTint = colors.surfaceTint().resolve(),
        inverseSurface = colors.inverseSurface().resolve(),
        inverseOnSurface = colors.inverseOnSurface().resolve(),
        error = colors.error().resolve(),
        onError = colors.onError().resolve(),
        errorContainer = colors.errorContainer().resolve(),
        onErrorContainer = colors.onErrorContainer().resolve(),
        outline = colors.outline().resolve(),
        outlineVariant = colors.outlineVariant().resolve(),
        scrim = colors.scrim().resolve(),
        surfaceBright = colors.surfaceBright().resolve(),
        surfaceDim = colors.surfaceDim().resolve(),
        surfaceContainer = colors.surfaceContainer().resolve(),
        surfaceContainerHigh = colors.surfaceContainerHigh().resolve(),
        surfaceContainerHighest = colors.surfaceContainerHighest().resolve(),
        surfaceContainerLow = colors.surfaceContainerLow().resolve(),
        surfaceContainerLowest = colors.surfaceContainerLowest().resolve(),
    )
}
