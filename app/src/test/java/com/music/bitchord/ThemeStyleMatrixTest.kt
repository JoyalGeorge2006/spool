@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.data.settings.ThemeStyle
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.icons.SfIcons
import com.music.bitchord.ui.screens.RowDivider
import com.music.bitchord.ui.screens.SettingsCategory
import com.music.bitchord.ui.screens.SettingsRow
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual evidence for the Material You Theme Style setting: the settings card
 * with its faint dividers and the floating bars (soft hairline edges), in each
 * style x light/dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w390dp-h844dp-mdpi")
class ThemeStyleMatrixTest {
 @get:Rule val compose=createComposeRule()

 private fun probe(dark:Boolean,style:ThemeStyle) {
  V6ScreenshotTest().installUiHost()
  val old=AppSettings.blurEffects.value
  AppSettings.blurEffects.value=false
  try {
   compose.setContent {
    BitChordTheme(darkTheme=dark,palette=PaletteMode.MATERIAL_YOU,style=style) {
     val haze=remember { HazeState() }
     Box(Modifier.width(390.dp).height(844.dp).testTag("themestyle-matrix")) {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).hazeSource(haze).background(MaterialTheme.colorScheme.background)) {
       Text("Appearance",color=MaterialTheme.colorScheme.onBackground,style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(16.dp))
       // The settings card: three production rows over the production dividers.
       Column(Modifier.padding(16.dp).background(MaterialTheme.colorScheme.surface,MaterialTheme.shapes.large)) {
        SettingsRow(icon=SfIcons.Notes,title="Synced lyrics")
        RowDivider()
        SettingsRow(icon=SfIcons.Font,title="Lyrics sources",subtitle="LRCLIB, Musixmatch")
        RowDivider()
        SettingsRow(icon=SfIcons.Appearance,title="Translation language",value="English")
       }
       Spacer(Modifier.height(16.dp))
       Text("Theme style: ${style.label}",color=MaterialTheme.colorScheme.onBackground,modifier=Modifier.padding(16.dp))
      }
      Column(Modifier.align(Alignment.BottomCenter)) {
       MiniPlayer(song=Song(videoId="fixture",title="Midnight Rain",artist="Nova Rae",thumbnailUrl=null,durationText="3:48"),isPlaying=false,isLoading=false,hazeState=haze,onPlayPause={},onNext={},onPrevious={},onExpand={})
       Spacer(Modifier.height(8.dp))
       FloatingBottomBar(tabs=listOf(BottomTab("Home",Icons.Rounded.Home),BottomTab("Explore",Icons.Rounded.Search),BottomTab("Library",Icons.Rounded.LibraryMusic),BottomTab("Settings",Icons.Rounded.Settings)),selectedIndex=0,onTabSelected={},hazeState=haze)
      }
     }
    }
   }
   compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
   compose.onNodeWithText("Synced lyrics").assertIsDisplayed()
   compose.onNodeWithTag("themestyle-matrix").captureRoboImage("build/outputs/roborazzi/themestyle_${style.name.lowercase()}_dark${dark}.png")
  } finally { AppSettings.blurEffects.value=old }
 }

 /** The real Appearance settings page (production SettingsScreen) with the
  * Theme Style picker, captured per style x light/dark. The style is selected
  * through the same AppSettings entry the picker writes to, so the capture
  * shows the production row, segmented control and dividers, not a mock. */
 private fun pickerProbe(dark:Boolean,style:ThemeStyle) {
  V6ScreenshotTest().installUiHost()
  val oldPalette=AppSettings.paletteMode.value
  val oldStyle=AppSettings.themeStyle.value
  AppSettings.setPaletteMode(PaletteMode.MATERIAL_YOU)
  AppSettings.setThemeStyle(style)
  try {
   compose.setContent {
    BitChordTheme(darkTheme=dark,palette=PaletteMode.MATERIAL_YOU,style=style) {
     Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("themestyle-picker")) {
      com.music.bitchord.ui.screens.SettingsScreen(
       windowWidth=390.dp,
       signedIn=false,
       account=null,
       onSignIn={},
       onSignOut={},
       onAccountScrobbling={},
       onEqualizer={},
       onOpenReplay={},
       onLyricsSources={},
       onTranslationLanguage={},
       onSources={},
       onListenTogether={},
       onWearOs={},
       onSpotifyCanvasAuth={},
       onAppLanguage={},
       onEditSignatureApi={},
       onConfirmBitPerfect={},
       onConfirmFixedVolume={},
       contentPadding=PaddingValues(0.dp),
       section=SettingsCategory.APPEARANCE,
      )
     }
    }
   }
   compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
   compose.onNodeWithText("Theme Style",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
   compose.onNodeWithText(style.label,useUnmergedTree=true).assertIsDisplayed()
   compose.waitForIdle()
   compose.onRoot().captureRoboImage("build/outputs/roborazzi/themestyle_picker_${style.name.lowercase()}_dark${dark}.png")
  } finally {
   AppSettings.setPaletteMode(oldPalette)
   AppSettings.setThemeStyle(oldStyle)
  }
 }

 @Test fun pickerLightTonalSpot()=pickerProbe(false,ThemeStyle.TONAL_SPOT)
 @Test fun pickerLightExpressive()=pickerProbe(false,ThemeStyle.EXPRESSIVE)
 @Test fun pickerLightVibrant()=pickerProbe(false,ThemeStyle.VIBRANT)
 @Test fun pickerDarkTonalSpot()=pickerProbe(true,ThemeStyle.TONAL_SPOT)
 @Test fun pickerDarkExpressive()=pickerProbe(true,ThemeStyle.EXPRESSIVE)
 @Test fun pickerDarkVibrant()=pickerProbe(true,ThemeStyle.VIBRANT)

 @Test fun lightTonalSpot()=probe(false,ThemeStyle.TONAL_SPOT)
 @Test fun lightExpressive()=probe(false,ThemeStyle.EXPRESSIVE)
 @Test fun lightVibrant()=probe(false,ThemeStyle.VIBRANT)
 @Test fun darkTonalSpot()=probe(true,ThemeStyle.TONAL_SPOT)
 @Test fun darkExpressive()=probe(true,ThemeStyle.EXPRESSIVE)
 @Test fun darkVibrant()=probe(true,ThemeStyle.VIBRANT)
}
