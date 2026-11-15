package com.music.bitchord

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Single spool launcher. Old aliases are compatibility entries, not choices. */
object AppIcon {
    private val legacyAliases = listOf(
        "LauncherDefault",
        "LauncherMonochrome",
        "LauncherGlassLight",
        "LauncherGlassDark",
        "LauncherGlassClear",
        "LauncherGlassClearDark",
        "LauncherSamsungGlass",
        "LauncherSamsungGlassDark",
        "LauncherSamsungGlassClear",
        "LauncherSamsungGlassClearDark",
        "LauncherPixelGlass",
        "LauncherPixelGlassDark",
        "LauncherPixelGlassClear",
        "LauncherPixelGlassClearDark",
        "LauncherNeonSilver",
        "LauncherNeonCyan",
        "LauncherNeonViolet",
        "LauncherSamsungNeonSilver",
        "LauncherSamsungNeonCyan",
        "LauncherSamsungNeonViolet",
        "LauncherPixelNeonSilver",
        "LauncherPixelNeonCyan",
        "LauncherPixelNeonViolet",
    )

    /** Enable one filtered launcher and repair explicit legacy targets without killing playback. */
    fun ensureSingleLogo(context: Context) {
        val pm = context.packageManager
        fun component(alias: String) = ComponentName(context.packageName, "com.music.bitchord.$alias")
        val current = component("LauncherSpool")
        if (pm.getComponentEnabledSetting(current) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(current, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        legacyAliases.forEach { alias ->
            val old = component(alias)
            if (pm.getComponentEnabledSetting(old) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(old, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
