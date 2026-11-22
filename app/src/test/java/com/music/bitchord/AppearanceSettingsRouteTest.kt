package com.music.bitchord

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.screens.SettingsCategory
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w390dp-h844dp-mdpi")
class AppearanceSettingsRouteTest {
 @get:Rule val compose=createComposeRule()
 private fun probe(dark:Boolean,large:Boolean) {
  val c=ApplicationProvider.getApplicationContext<Context>()
  AppSettings.init(c,AuthStore(c));AppSettings.setPaletteMode(PaletteMode.ASTRYX);AppSettings.setBlurEffects(false);AppSettings.setThemeMode(if(dark)ThemeMode.DARK else ThemeMode.LIGHT)
  val fixture=V6ScreenshotTest().also { it.installUiHost() }
  try {
   compose.setContent {
    val palette by AppSettings.paletteMode.collectAsState()
    CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
     BitChordTheme(darkTheme=dark,palette=palette) {
      Box(Modifier.width(390.dp).height(800.dp).background(MaterialTheme.colorScheme.background)) { fixture.SettingsShot(SettingsCategory.APPEARANCE) }
     }
    }
   }
   compose.waitForIdle()
   compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_appearance_${dark}_${large}_top.png")
   val list=compose.onAllNodes(hasScrollAction()).onFirst()
   list.performScrollToNode(hasText("Material You"));compose.waitForIdle()
   compose.onNodeWithText("Material You").assertIsDisplayed()
   compose.onNodeWithText("Material You").performClick();compose.waitForIdle()
   assertEquals(PaletteMode.MATERIAL_YOU,AppSettings.paletteMode.value)
   assertFalse(AppSettings.blurEffects.value)
   compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_appearance_${dark}_${large}_colours.png")
   list.performScrollToNode(hasText("Blur effects"));compose.waitForIdle()
   compose.onNodeWithText("Blur effects").performClick();compose.waitForIdle()
   assertTrue(AppSettings.blurEffects.value);assertEquals(PaletteMode.MATERIAL_YOU,AppSettings.paletteMode.value)
   AppSettings.reload();compose.waitForIdle()
   assertTrue(AppSettings.blurEffects.value);assertEquals(PaletteMode.MATERIAL_YOU,AppSettings.paletteMode.value)
   compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings_appearance_${dark}_${large}_blur.png")
   list.performScrollToNode(hasText("Astryx neutral"));compose.onNodeWithText("Astryx neutral").performClick();compose.waitForIdle()
   assertEquals(PaletteMode.ASTRYX,AppSettings.paletteMode.value);assertTrue(AppSettings.blurEffects.value)
  } finally { AppSettings.setPaletteMode(PaletteMode.ASTRYX);AppSettings.setBlurEffects(false) }
 }
 @Test fun light()=probe(false,false)
 @Test fun dark()=probe(true,false)
 @Test fun largeLight()=probe(false,true)
 @Test fun largeDark()=probe(true,true)
}
