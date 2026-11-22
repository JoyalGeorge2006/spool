package com.music.bitchord

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.ui.screens.approvedSettingsColorScheme
import com.music.bitchord.ui.screens.settingsRootColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPaletteTest {
    @Test fun darkSettingsAndChoicesUseTheSameOpaqueTokens() {
        val palette = approvedSettingsColorScheme(darkColorScheme())
        assertEquals(Color.Black, palette.background)
        assertEquals(Color(0xFF1B1B1B), palette.surface)
        assertEquals(palette.surface, palette.surfaceVariant)
        assertEquals(Color(0xFFF1F1F1), palette.onBackground)
        assertEquals(palette.onBackground, palette.onSurface)
    }
    @Test fun lightSettingsAndChoicesUseTheSameOpaqueTokens() {
        val palette = approvedSettingsColorScheme(lightColorScheme())
        assertEquals(Color(0xFFF1F1F1), palette.background)
        assertEquals(Color.White, palette.surface)
        assertEquals(palette.surface, palette.surfaceVariant)
        assertEquals(Color(0xFF1B1B1B), palette.onBackground)
        assertEquals(palette.onBackground, palette.onSurface)
    }
    // Palette split approved by the user on WhatsApp 2026-11-19 ("Every thing
    // is approved" 22:12, "Approved" 22:14): the neutral tokens belong to
    // Astryx only — under Material You, settings pages (choice pages
    // included) run in the selected dynamic scheme, which follows the
    // wallpaper (2026-11-20 08:06).
    // ColorScheme equality is reference-based here, so compare token by
    // token: assertEquals on two instances fails even when every value
    // matches.
    private fun assertSameTokens(expected: androidx.compose.material3.ColorScheme, actual: androidx.compose.material3.ColorScheme) {
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.onPrimary, actual.onPrimary)
        assertEquals(expected.primaryContainer, actual.primaryContainer)
        assertEquals(expected.onPrimaryContainer, actual.onPrimaryContainer)
        assertEquals(expected.inversePrimary, actual.inversePrimary)
        assertEquals(expected.secondary, actual.secondary)
        assertEquals(expected.onSecondary, actual.onSecondary)
        assertEquals(expected.secondaryContainer, actual.secondaryContainer)
        assertEquals(expected.onSecondaryContainer, actual.onSecondaryContainer)
        assertEquals(expected.tertiary, actual.tertiary)
        assertEquals(expected.onTertiary, actual.onTertiary)
        assertEquals(expected.tertiaryContainer, actual.tertiaryContainer)
        assertEquals(expected.onTertiaryContainer, actual.onTertiaryContainer)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.onBackground, actual.onBackground)
        assertEquals(expected.surface, actual.surface)
        assertEquals(expected.onSurface, actual.onSurface)
        assertEquals(expected.surfaceVariant, actual.surfaceVariant)
        assertEquals(expected.onSurfaceVariant, actual.onSurfaceVariant)
        assertEquals(expected.surfaceTint, actual.surfaceTint)
        assertEquals(expected.inverseSurface, actual.inverseSurface)
        assertEquals(expected.inverseOnSurface, actual.inverseOnSurface)
        assertEquals(expected.error, actual.error)
        assertEquals(expected.onError, actual.onError)
        assertEquals(expected.errorContainer, actual.errorContainer)
        assertEquals(expected.onErrorContainer, actual.onErrorContainer)
        assertEquals(expected.outline, actual.outline)
        assertEquals(expected.outlineVariant, actual.outlineVariant)
        assertEquals(expected.scrim, actual.scrim)
        assertEquals(expected.surfaceBright, actual.surfaceBright)
        assertEquals(expected.surfaceDim, actual.surfaceDim)
        assertEquals(expected.surfaceContainer, actual.surfaceContainer)
        assertEquals(expected.surfaceContainerHigh, actual.surfaceContainerHigh)
        assertEquals(expected.surfaceContainerHighest, actual.surfaceContainerHighest)
        assertEquals(expected.surfaceContainerLow, actual.surfaceContainerLow)
        assertEquals(expected.surfaceContainerLowest, actual.surfaceContainerLowest)
    }
    @Test fun astryxKeepsTheOpaqueTokens() {
        val base = lightColorScheme()
        assertSameTokens(approvedSettingsColorScheme(base), settingsRootColorScheme(PaletteMode.ASTRYX, base))
        val dark = darkColorScheme()
        assertSameTokens(approvedSettingsColorScheme(dark), settingsRootColorScheme(PaletteMode.ASTRYX, dark))
    }
    @Test fun materialYouKeepsTheDynamicScheme() {
        val light = lightColorScheme(primary = Color(0xFF6750A4))
        assertSameTokens(light, settingsRootColorScheme(PaletteMode.MATERIAL_YOU, light))
        val dark = darkColorScheme(primary = Color(0xFFD0BCFF))
        assertSameTokens(dark, settingsRootColorScheme(PaletteMode.MATERIAL_YOU, dark))
    }
}
