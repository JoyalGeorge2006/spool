package com.music.bitchord.ui.theme

import android.app.Activity
import android.app.WallpaperManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppFont
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.data.settings.ThemeStyle

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF1F1F1),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFF9E9E9E),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0x1AFFFFFF),
    onSecondaryContainer = Color(0xFFF1F1F1),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF111111),
    background = Color(0xFF1B1B1B),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF262626),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0x1AFFFFFF),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFF1F1F1),
    tertiaryContainer = Color(0xFF333333),
    onTertiaryContainer = Color(0xFFF1F1F1),
    inversePrimary = Color(0xFF1B1B1B),
    inverseSurface = Color(0xFFF1F1F1),
    inverseOnSurface = Color(0xFF1B1B1B),
    outlineVariant = Color(0xFF3A3A3A),
    surfaceDim = Color(0xFF141414),
    surfaceBright = Color(0xFF3A3A3A),
    surfaceContainerLowest = Color(0xFF111111),
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF303030),
    surfaceContainerHighest = Color(0xFF3A3A3A),
    surfaceTint = Color.Transparent,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B1B1B),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF474747),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0x0F000000),
    onSecondaryContainer = Color(0xFF1B1B1B),
    tertiary = Color(0xFF474747),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF1F1F1),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF6A6A6A),
    outline = Color(0x0F000000),
    primaryContainer = Color(0xFFE4E4E4),
    onPrimaryContainer = Color(0xFF1B1B1B),
    tertiaryContainer = Color(0xFFE4E4E4),
    onTertiaryContainer = Color(0xFF1B1B1B),
    inversePrimary = Color(0xFFF1F1F1),
    inverseSurface = Color(0xFF262626),
    inverseOnSurface = Color(0xFFF1F1F1),
    outlineVariant = Color(0xFFD9D9D9),
    surfaceDim = Color(0xFFDEDEDE),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF1F1F1),
    surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    surfaceTint = Color.Transparent,
)

/**
 * The fixed Material 3 baseline palette [PaletteMode.MATERIAL_YOU] draws below
 * Android 12, where wallpaper-derived colours do not exist. These are the
 * published M3 baseline tones - fixed, not derived from anything on the phone.
 */
private val FixedM3Light = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inversePrimary = Color(0xFFD0BCFF),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
)

private val FixedM3Dark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inversePrimary = Color(0xFF6750A4),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
)

/** spool semantic tokens (v6): info and success accents. */
val SpoolInfo = Color(0xFF0074E2)
val SpoolInfoDark = Color(0xFF6D9CFE)
val SpoolSuccess = Color(0xFF198100)
val SpoolSuccessDark = Color(0xFF64AF4C)

/**
 * SF Pro Display, the face Apple Music itself is set in, drawn one weight
 * lighter than it is asked for.
 *
 * Measured against Apple Music's own screens, every weight here came out a
 * cut heavier than the same text there: its titles are Semibold where these
 * asked for Bold, its metadata Medium where these asked for Semibold. The
 * shift is made once, in the family, so it reaches the type scale and every
 * weight set in place alike and keeps the steps between them: Heavy draws
 * Bold, Bold draws Semibold, Semibold draws Medium, Medium draws Regular.
 * Regular stays Regular — no lighter cut is bundled, and Apple's body text is
 * Regular too. A FontWeight in this app therefore names the step it sits at in
 * the scale, not the cut it draws.
 *
 * Compose synthesises nothing, so a weight outside these falls back to the
 * nearest one listed.
 */
val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_regular, FontWeight.W500),
    Font(R.font.sf_pro_display_medium, FontWeight.W600),
    Font(R.font.sf_pro_display_semibold, FontWeight.W700),
    Font(R.font.sf_pro_display_bold, FontWeight.W800),
)

/**
 * spool's face (v6): Figtree everywhere, drawn at straight weights - no
 * weight shifting; the v6 type spec names the actual cuts.
 */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.W400),
    Font(R.font.figtree_medium, FontWeight.W500),
    Font(R.font.figtree_semibold, FontWeight.W600),
    Font(R.font.figtree_bold, FontWeight.W700),
    Font(R.font.figtree_extrabold, FontWeight.W800),
)

/*
 * Letter spacing measured off Apple Music's own screens: the spacing the
 * bundled Display cut needs, at the weight it draws, to set a line to the
 * width Apple sets it. Apple sets text under 20pt in SF Pro Text, which is
 * spaced looser than Display, so the small sizes open up — about +0.25 to
 * +0.4 at 12 to 14 — and the spacing closes as the size grows, to just under
 * nothing for semibold titles at 18 to 24. The large titles had nothing to
 * measure against and follow the same trend.
 */
private val SpoolTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.25).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = 0.05.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp, letterSpacing = 0.25.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp, letterSpacing = 0.3.sp),
).withFamily(Figtree)

/**
 * SF Pro Display as the app first shipped it, before [SFProDisplay] moved every
 * weight a cut lighter: each weight draws its own cut, Heavy included. Kept for
 * [AppFont.LEGACY].
 */
val SFProDisplayLegacy = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_medium, FontWeight.W500),
    Font(R.font.sf_pro_display_semibold, FontWeight.W600),
    Font(R.font.sf_pro_display_bold, FontWeight.W700),
    Font(R.font.sf_pro_display_heavy, FontWeight.W800),
)

// Heavy, tight typography — the original scale [AppFont.LEGACY] restores.
private val LegacyTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.W400, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.W400, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.W600, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.W600, fontSize = 11.sp),
).withFamily(SFProDisplayLegacy)

/** Applies [family] to every style in the scale, so nothing is left on Roboto. */
private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/** v6 radii: inner 8, element 12, container 16, chips full. */
private val SpoolShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * The hairline colour for floating-card edges and settings dividers. Always a
 * whisper of the content tone, never the full-strength `outline`: the listener
 * asked for soft edges and faint dividers, with cards separating through fill.
 * Null outside [BitChordTheme]; call sites fall back to `colorScheme.outline`.
 */
val LocalHairline = compositionLocalOf<Color?> { null }

/** Seed for phones without a wallpaper-derived one: the Material baseline purple. */
private val FallbackSeed = Color(0xFF6750A4)

@Composable
fun BitChordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    font: AppFont = AppFont.DEFAULT,
    palette: PaletteMode = PaletteMode.ASTRYX,
    style: ThemeStyle = ThemeStyle.EXPRESSIVE,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        palette == PaletteMode.MATERIAL_YOU -> {
            val context = LocalContext.current
            // Seed the style schemes from the wallpaper's own colour when the
            // system exposes it (API 27+, no permission needed); fall back to
            // the system dynamic scheme's primary (itself wallpaper-derived on
            // API 31+), then to the static fallback seed below that.
            // WallpaperColors.getPrimaryColor() returns android.graphics.Color
            // (instance class since API 26; this method stable since API 27),
            // so convert with toArgb() before constructing the Compose Color.
            val wallpaperSeedArgb: Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    ?.primaryColor
                    ?.toArgb()
            } else {
                null
            }
            val seed: Color = wallpaperSeedArgb?.let { Color(it) }
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
                } else {
                    FallbackSeed
                }
            val base = themeStyleScheme(seed, darkTheme, style)
            // Material You sets `surface` equal to `background`, so every card
            // and bar drawn with `surface` disappears into the page. Google's
            // own surface guidance draws contained areas from the raised
            // container tones instead, so hand `surface` the low container
            // tone: cards and bars read against the page again, in the
            // wallpaper's own palette.
            base.copy(surface = base.surfaceContainerLow)
        }
        else -> if (darkTheme) DarkColors else LightColors
    }
    // Astryx keeps its long-standing hairline tones exactly; Material You's
    // generated `outline` is far louder than either, so it gets a 10% whisper
    // of the content tone instead.
    val hairline = when (palette) {
        PaletteMode.ASTRYX -> if (darkTheme) Color(0x1AFFFFFF) else Color(0x0F000000)
        PaletteMode.MATERIAL_YOU -> colorScheme.onSurface.copy(alpha = 0.10f)
    }
    CompositionLocalProvider(LocalHairline provides hairline) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = SpoolShapes,
            typography = when (font) {
                AppFont.DEFAULT -> SpoolTypography
                AppFont.LEGACY -> LegacyTypography
            },
            content = content,
        )
    }
}

/**
 * Draws the status and navigation bar glyphs dark or light.
 *
 * `enableEdgeToEdge()` decides this from the *system* dark-mode setting, which
 * is the wrong input the moment the in-app theme disagrees with it: Light theme
 * on a phone in dark mode left white icons on a white bar, invisible. The bars
 * have to follow the content the app is actually painting. The player supplies
 * its own stable light-icon value and paints contrast behind it; every other
 * surface follows the theme. Hence a parameter rather than reading it here.
 */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = findWindow(view) ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}

