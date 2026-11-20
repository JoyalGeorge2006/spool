package com.music.bitchord

import android.app.UiModeManager
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.SpoolStartupTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class SpoolStartupThemeTest {
    @Test fun explicitLightAndDarkMatchSavedChoice() {
        assertEquals(UiModeManager.MODE_NIGHT_NO, SpoolStartupTheme.platformMode(ThemeMode.LIGHT))
        assertEquals(UiModeManager.MODE_NIGHT_YES, SpoolStartupTheme.platformMode(ThemeMode.DARK))
    }
    @Test fun systemClearsPackageOverride() {
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, SpoolStartupTheme.platformMode(ThemeMode.SYSTEM))
    }
}
