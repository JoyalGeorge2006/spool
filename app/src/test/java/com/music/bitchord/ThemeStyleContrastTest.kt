package com.music.bitchord

import androidx.compose.ui.graphics.Color
import com.music.bitchord.data.settings.ThemeStyle
import com.music.bitchord.ui.theme.themeStyleScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.pow

/**
 * WCAG contrast guard for the Material You Theme Style schemes. Every style x
 * mode x seed combination must keep text/icon roles readable; hairlines and
 * dividers are decorative and deliberately exempt.
 */
class ThemeStyleContrastTest {

    private fun luminance(c: Color): Double {
        fun ch(v: Float) = if (v <= 0.04045f) v / 12.92 else ((v + 0.055f) / 1.055f).toDouble().pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val l1 = luminance(a); val l2 = luminance(b)
        return (max(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun check(seed: Color, dark: Boolean, style: ThemeStyle) {
        // The production override hands `surface` the low container tone.
        val s = themeStyleScheme(seed, dark, style).let { it.copy(surface = it.surfaceContainerLow) }
        val where = "style=$style dark=$dark seed=${Integer.toHexString(seed.value.toInt())}"
        val body = mapOf(
            "onBackground/background" to (s.onBackground to s.background),
            "onSurface/surface" to (s.onSurface to s.surface),
            "onSurface/background" to (s.onSurface to s.background),
            "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
            "onPrimary/primary" to (s.onPrimary to s.primary),
            "onSecondary/secondary" to (s.onSecondary to s.secondary),
            "onTertiary/tertiary" to (s.onTertiary to s.tertiary),
            "onError/error" to (s.onError to s.error),
            "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
            "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
            "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
            "onSurface/surfaceContainer" to (s.onSurface to s.surfaceContainer),
            "onSurface/surfaceContainerHigh" to (s.onSurface to s.surfaceContainerHigh),
            "onSurface/surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
        )
        for ((name, pair) in body) {
            val r = ratio(pair.first, pair.second)
            assertTrue("$where $name contrast %.2f below WCAG AA 4.5".format(r), r >= 4.5)
        }
    }

    @Test
    fun allStylesAllModesKeepReadableRoles() {
        val seeds = listOf(
            Color(0xFF6750A4), // Material baseline purple (the fallback seed)
            Color(0xFFA3C9A8), // green wallpaper
            Color(0xFFB3261E), // red wallpaper
            Color(0xFF0B57D0), // blue wallpaper
        )
        for (seed in seeds) for (dark in listOf(false, true)) for (style in ThemeStyle.entries) {
            check(seed, dark, style)
        }
    }
}
