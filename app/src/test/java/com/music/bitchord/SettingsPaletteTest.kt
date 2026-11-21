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
    // Header/palette work approved 21 Nov: the neutral tokens belong to Astryx
    // only — under Material You, settings pages (choice pages included) run in
    // the selected dynamic scheme.
    @Test fun astryxKeepsTheOpaqueTokens() {
        val base = lightColorScheme()
        assertEquals(approvedSettingsColorScheme(base), settingsRootColorScheme(PaletteMode.ASTRYX, base))
    }
    @Test fun materialYouKeepsTheDynamicScheme() {
        val light = lightColorScheme(primary = Color(0xFF6750A4))
        assertEquals(light, settingsRootColorScheme(PaletteMode.MATERIAL_YOU, light))
        val dark = darkColorScheme(primary = Color(0xFFD0BCFF))
        assertEquals(dark, settingsRootColorScheme(PaletteMode.MATERIAL_YOU, dark))
    }
}
