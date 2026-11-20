@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.ui.screens.PaletteCards
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Appearance palette cards, rendered under each palette they can pick. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[30],qualifiers="w390dp-h844dp-mdpi")
class PaletteCardsScreenshotTest {
 @get:Rule val compose=createComposeRule()
 private fun probe(dark:Boolean,palette:PaletteMode) {
  var selected by mutableStateOf(palette);var picks=0
  compose.setContent {
   BitChordTheme(darkTheme=dark,palette=selected) {
    Box(Modifier.width(390.dp).testTag("palette-root")) {
     PaletteCards(selected=selected,onSelect={selected=it;picks++})
    }
   }
  }
  compose.onNodeWithText("Astryx neutral").assertIsDisplayed()
  compose.onNodeWithText("Material You").assertIsDisplayed()
  compose.onNodeWithTag("palette-root").captureRoboImage("build/outputs/roborazzi/palette_cards_${palette}_${dark}.png")
  compose.onNodeWithText(if(palette==PaletteMode.ASTRYX)"Material You" else "Astryx neutral").performClick()
  assertEquals(1,picks)
  compose.onNodeWithTag("palette-root").captureRoboImage("build/outputs/roborazzi/palette_cards_${palette}_${dark}_picked.png")
 }
 @Test fun lightAstryxDefault()=probe(false,PaletteMode.ASTRYX)
 @Test fun darkAstryxDefault()=probe(true,PaletteMode.ASTRYX)
 @Test fun lightMaterialYou()=probe(false,PaletteMode.MATERIAL_YOU)
 @Test fun darkMaterialYou()=probe(true,PaletteMode.MATERIAL_YOU)
}

/**
 * Scheme selection below Android 12, where Material You cannot read the
 * wallpaper: a generated Material You scheme from the fixed baseline seed
 * (#6750A4) in the chosen Theme Style, never the neutral Astryx scheme.
 * The dynamic path (sdk 31+) is deliberately not asserted here - what the
 * phone's wallpaper yields is not the fixture's business.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[30])
class PaletteFallbackTest {
 @get:Rule val compose=createComposeRule()
 @Test fun materialYouBelowAndroid12UsesGeneratedLightScheme() {
  var primary:Color?=null;var background:Color?=null
  compose.setContent { BitChordTheme(darkTheme=false,palette=PaletteMode.MATERIAL_YOU) {
   primary=MaterialTheme.colorScheme.primary;background=MaterialTheme.colorScheme.background
  } }
  // Not the neutral Astryx scheme, and light: the generated Material You one.
  assertNotEquals(Color(0xFF1B1B1B),primary)
  assertNotEquals(Color(0xFFF1F1F1),background)
  assertTrue(background!!.red>0.9f&&background!!.green>0.9f&&background!!.blue>0.9f)
 }
 @Test fun materialYouBelowAndroid12UsesGeneratedDarkScheme() {
  var primary:Color?=null;var background:Color?=null
  compose.setContent { BitChordTheme(darkTheme=true,palette=PaletteMode.MATERIAL_YOU) {
   primary=MaterialTheme.colorScheme.primary;background=MaterialTheme.colorScheme.background
  } }
  assertNotEquals(Color(0xFFF1F1F1),primary)
  assertTrue(background!!.red<0.2f&&background!!.green<0.2f&&background!!.blue<0.2f)
 }
 @Test fun themeStyleIsRespectedBelowAndroid12() {
  // One setContent per test rule: flip a state instead of re-calling setContent.
  val styleState=androidx.compose.runtime.mutableStateOf(com.music.bitchord.data.settings.ThemeStyle.TONAL_SPOT)
  var primary:Color?=null
  compose.setContent { BitChordTheme(darkTheme=true,palette=PaletteMode.MATERIAL_YOU,style=styleState.value) {
   primary=MaterialTheme.colorScheme.primary
  } }
  compose.waitForIdle()
  val tonalSpot=primary
  styleState.value=com.music.bitchord.data.settings.ThemeStyle.VIBRANT
  compose.waitForIdle()
  val vibrant=primary
  assertNotNull(tonalSpot);assertNotNull(vibrant)
  assertNotEquals(tonalSpot,vibrant)
 }
 @Test fun astryxKeepsNeutralSchemeBelowAndroid12() {
  var primary:Color?=null;var background:Color?=null
  compose.setContent { BitChordTheme(darkTheme=false,palette=PaletteMode.ASTRYX) {
   primary=MaterialTheme.colorScheme.primary;background=MaterialTheme.colorScheme.background
  } }
  assertEquals(Color(0xFF1B1B1B),primary)
  assertEquals(Color(0xFFF1F1F1),background)
 }
}

/** The new preferences: defaults, persistence across reload, flat forcings intact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class PaletteSettingsPersistenceTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private val prefs get()=context.getSharedPreferences("bitchord_settings",Context.MODE_PRIVATE)
 private fun boot(){AppSettings.init(context,AuthStore(context))}
 @Before fun setup(){prefs.edit().remove("palette_mode").remove("blur_effects").remove("theme_style").commit();boot()}
 @After fun cleanup(){prefs.edit().remove("palette_mode").remove("blur_effects").remove("theme_style").commit();AppSettings.reload()}
 @Test fun defaultsAreAstryxAndBlurOff() {
  assertEquals(PaletteMode.ASTRYX,AppSettings.paletteMode.value)
  assertFalse(AppSettings.blurEffects.value)
 }
 @Test fun themeStyleDefaultsToExpressive() {
  assertEquals(com.music.bitchord.data.settings.ThemeStyle.EXPRESSIVE,AppSettings.themeStyle.value)
 }
 @Test fun themeStyleChoiceSurvivesReload() {
  AppSettings.setThemeStyle(com.music.bitchord.data.settings.ThemeStyle.VIBRANT)
  assertEquals("VIBRANT",prefs.getString("theme_style",null))
  AppSettings.themeStyle.value=com.music.bitchord.data.settings.ThemeStyle.EXPRESSIVE;AppSettings.reload()
  assertEquals(com.music.bitchord.data.settings.ThemeStyle.VIBRANT,AppSettings.themeStyle.value)
 }
 @Test fun paletteChoiceSurvivesReload() {
  AppSettings.setPaletteMode(PaletteMode.MATERIAL_YOU)
  assertEquals("MATERIAL_YOU",prefs.getString("palette_mode",null))
  AppSettings.paletteMode.value=PaletteMode.ASTRYX;AppSettings.reload()
  assertEquals(PaletteMode.MATERIAL_YOU,AppSettings.paletteMode.value)
 }
 @Test fun unknownStoredPaletteFallsBackToAstryx() {
  prefs.edit().putString("palette_mode","FUTURE_VALUE").commit();AppSettings.reload()
  assertEquals(PaletteMode.ASTRYX,AppSettings.paletteMode.value)
 }
 @Test fun blurChoiceSurvivesReload() {
  AppSettings.setBlurEffects(true)
  assertTrue(prefs.getBoolean("blur_effects",false))
  AppSettings.blurEffects.value=false;AppSettings.reload()
  assertTrue(AppSettings.blurEffects.value)
 }
 @Test fun emptyRestoreResetsPaletteAndBlur() {
  AppSettings.setPaletteMode(PaletteMode.MATERIAL_YOU);AppSettings.setBlurEffects(true)
  AppSettings.importPrefs(emptyMap())
  assertEquals(PaletteMode.ASTRYX,AppSettings.paletteMode.value);assertFalse(AppSettings.blurEffects.value)
 }
 @Test fun flatDesignForcingsUnaffectedByNewSettings() {
  AppSettings.setPaletteMode(PaletteMode.MATERIAL_YOU);AppSettings.setBlurEffects(true);AppSettings.reload()
  assertTrue(AppSettings.reduceDynamicBlur.value)
  assertFalse(AppSettings.liquidGlass.value)
 }
}
