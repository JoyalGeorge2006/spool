@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.ui.screens.*
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real destination composables, both palettes and brightness modes; no network or link submission. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
abstract class AboutPaletteScreenshotBase {
 @get:Rule val compose=createComposeRule()
 private lateinit var old:PaletteMode
 @Before fun setup() { V6ScreenshotTest().installUiHost();old=AppSettings.paletteMode.value }
 @After fun cleanup() {AppSettings.setPaletteMode(old)}
 protected fun capture(palette:PaletteMode,dark:Boolean) {
  AppSettings.setPaletteMode(palette)
  var page by mutableStateOf("updates")
  var outer:ColorScheme?=null;var inner:ColorScheme?=null
  var checks=0;var credits=0;var notice=""
  compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,1f)) {
   BitChordTheme(darkTheme=dark,palette=palette) {
    outer=MaterialTheme.colorScheme
    SpoolSettingsSurface { inner=MaterialTheme.colorScheme }
    Box(Modifier.width(390.dp).height(800.dp).testTag("about-palette-root")) {
     when(page) {
      "updates" -> AppUpdatesContent(PaddingValues(top=56.dp,bottom=36.dp),AppUpdateChecker.CheckState.UpToDate,
       null,AppUpdateChecker.DownloadState.Idle,true,0,false,{checks++},{},{},{},{})
      "developer" -> AboutDeveloperPage(PaddingValues(top=56.dp,bottom=36.dp),{credits++},{})
      "credits" -> CreditsLicensesPage(PaddingValues(top=56.dp,bottom=36.dp),{notice=it})
      else -> LicenseNoticePage(page,PaddingValues(top=56.dp,bottom=36.dp))
     }
    }
   }
  } }
  compose.waitForIdle()
  if(palette==PaletteMode.MATERIAL_YOU) {
   assertEquals("Material You surface must not be neutralized",outer!!.background,inner!!.background)
   assertEquals(outer!!.surfaceVariant,inner!!.surfaceVariant)
   assertEquals(outer!!.primary,inner!!.primary)
  } else {
   val expected=approvedSettingsColorScheme(outer!!)
   assertEquals("Astryx keeps approved settings colors",expected.background,inner!!.background)
   assertEquals(expected.surfaceVariant,inner!!.surfaceVariant)
  }
  fun shot(label:String) {
   compose.waitForIdle()
   compose.onNodeWithTag("about-palette-root").captureRoboImage("build/outputs/roborazzi/about_palette_${palette}_${dark}_$label.png")
  }
  shot("updates_top")
  compose.onNodeWithText("Check for updates").performScrollTo().performClick();assertEquals(1,checks)
  compose.onNodeWithText("Update source").performScrollTo();shot("updates_bottom")
  compose.runOnIdle{page="developer"};compose.waitForIdle();shot("developer_top")
  compose.onNodeWithText("Credits and licenses").performScrollTo().performClick();assertEquals(1,credits)
  compose.onNodeWithText("Source code").performScrollTo();shot("developer_bottom")
  compose.runOnIdle{page="credits"};compose.waitForIdle();shot("credits_top")
  compose.onNodeWithText("GNU GPL v3").performScrollTo().performClick();assertEquals("GPL-3.0.txt",notice)
  compose.onNodeWithText("Independent app").performScrollTo();shot("credits_bottom")
  for(file in listOf("GPL-3.0.txt")+BUNDLED_NOTICES.map{it.first}) {
   compose.runOnIdle{page=file};compose.waitForIdle();shot("notice_"+file.removeSuffix(".txt"))
  }
 }
}
class AboutPaletteScreenshot_materialLight:AboutPaletteScreenshotBase(){@Test fun check()=capture(PaletteMode.MATERIAL_YOU,false)}
class AboutPaletteScreenshot_materialDark:AboutPaletteScreenshotBase(){@Test fun check()=capture(PaletteMode.MATERIAL_YOU,true)}
class AboutPaletteScreenshot_astryxLight:AboutPaletteScreenshotBase(){@Test fun check()=capture(PaletteMode.ASTRYX,false)}
class AboutPaletteScreenshot_astryxDark:AboutPaletteScreenshotBase(){@Test fun check()=capture(PaletteMode.ASTRYX,true)}
