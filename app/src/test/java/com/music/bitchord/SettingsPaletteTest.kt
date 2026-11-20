package com.music.bitchord

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.music.bitchord.ui.screens.approvedSettingsColorScheme
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
}
