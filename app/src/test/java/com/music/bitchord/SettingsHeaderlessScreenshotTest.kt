@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.FrostedTopBar
import com.music.bitchord.ui.components.TopBarAccountButton
import com.music.bitchord.ui.components.TopBarDownloadButton
import com.music.bitchord.ui.screens.SettingsCategory
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The headerless Settings chrome approved 21 Nov: no pinned bar and no Back
 * circle on Settings or any of its subpages, titles scrolling away with the
 * page, live controls (download batch, account) still floating as circles.
 * Composes the page with the same FrostedTopBar parameters MainActivity now
 * passes for a Settings route — controlsOnly, onBack null, no settings pane —
 * so the pixels are of the real route composition, not a paraphrase.
 * Returning is the Android system Back gesture, which never renders a control.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsHeaderlessScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun probe(dark: Boolean, palette: PaletteMode, section: SettingsCategory?) {
        val c = ApplicationProvider.getApplicationContext<Context>()
        AppSettings.init(c, AuthStore(c))
        val oldPalette = AppSettings.paletteMode.value
        AppSettings.setPaletteMode(palette)
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        val backLabel = c.getString(R.string.back)
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val tag = "settings_headerless_${palette.name.lowercase()}_${if (dark) "dark" else "light"}_${section?.name?.lowercase() ?: "main"}"
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    BitChordTheme(darkTheme = dark, palette = AppSettings.paletteMode.value) {
                        Box(Modifier.width(390.dp).height(800.dp).background(MaterialTheme.colorScheme.background)) {
                            fixture.SettingsShot(section)
                            // The exact chrome a Settings route gets in BitChordApp.
                            FrostedTopBar(
                                title = "Settings",
                                controlsOnly = true,
                                statusBarInsets = WindowInsets(top = 24.dp),
                                scrolled = { false },
                                transparentBackdrop = true,
                                artworkPageChrome = true,
                                onBack = null,
                                actions = {
                                    TopBarDownloadButton(onClick = {})
                                    TopBarAccountButton(null) {}
                                },
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            // No Back affordance anywhere on a Settings route.
            assertEquals(0, compose.onAllNodesWithContentDescription(backLabel).fetchSemanticsNodes().size)
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${tag}_top.png")
            // The title rides away with the content instead of sitting in a bar.
            compose.onAllNodes(hasScrollAction()).onFirst()
                .performTouchInput { swipeUp(centerY + height * 0.3f, centerY - height * 0.3f) }
            compose.waitForIdle()
            assertEquals(0, compose.onAllNodesWithContentDescription(backLabel).fetchSemanticsNodes().size)
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${tag}_scrolled.png")
        } finally {
            AppSettings.setPaletteMode(oldPalette)
        }
    }

    @Test fun astryxLightMain() = probe(false, PaletteMode.ASTRYX, null)
    @Test fun astryxDarkMain() = probe(true, PaletteMode.ASTRYX, null)
    @Test fun materialYouLightMain() = probe(false, PaletteMode.MATERIAL_YOU, null)
    @Test fun materialYouDarkMain() = probe(true, PaletteMode.MATERIAL_YOU, null)
    @Test fun astryxDarkPlaybackSubpage() = probe(true, PaletteMode.ASTRYX, SettingsCategory.PLAYBACK)
    @Test fun materialYouDarkPlaybackSubpage() = probe(true, PaletteMode.MATERIAL_YOU, SettingsCategory.PLAYBACK)
}
