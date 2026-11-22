package com.music.bitchord.ui

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.music.bitchord.data.settings.ThemeMode

/** Tell Android the app's selected mode before its next starting window is painted.
 * This changes only this package's resource mode, never the device's theme. */
internal object SpoolStartupTheme {
    internal fun platformMode(mode: ThemeMode): Int = when (mode) {
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        // For setApplicationNightMode, AUTO clears the package override to
        // UI_MODE_NIGHT_UNDEFINED, inheriting the actual system configuration.
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    }

    fun sync(context: Context, mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < 31) return
        context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(platformMode(mode))
    }
}
