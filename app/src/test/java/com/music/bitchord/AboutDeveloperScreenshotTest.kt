@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.ui.screens.*
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.navigation.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers=RobolectricDeviceQualifiers.Pixel7)
abstract class AboutDeveloperScreenshotTestBase {
 @get:Rule val compose=createComposeRule()
 protected fun verify(dark:Boolean,width:Int=390,scale:Float=1f) {
  V6ScreenshotTest().installUiHost()
  val links=mutableListOf<String>();var credits=0
  compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
   BitChordTheme(darkTheme=dark) {
    Box(Modifier.width(width.dp).height(740.dp).testTag("about-root")) {
     AboutDeveloperPage(PaddingValues(top=56.dp,bottom=40.dp),{credits++},{links+=it})
    }
   }
  } }
  compose.onAllNodesWithText("Joyal George").onFirst().assertExists()
  compose.onNodeWithContentDescription("Joyal George profile photo").assertExists()
  compose.onNodeWithText("GitHub",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("X",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("LinkedIn",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("Contact",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("About the developer",useUnmergedTree=true).performScrollTo()
  compose.mainClock.advanceTimeBy(1000L);compose.waitForIdle()
  compose.onNodeWithTag("about-root").captureRoboImage("build/outputs/roborazzi/about_${width}_${scale}_${if(dark)"dark" else "light"}_top.png")
  compose.onNodeWithText("Website",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("Credits and licenses",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("Source code",useUnmergedTree=true).performScrollTo().performClick()
  compose.mainClock.advanceTimeBy(1000L);compose.waitForIdle()
  compose.onNodeWithTag("about-root").captureRoboImage("build/outputs/roborazzi/about_${width}_${scale}_${if(dark)"dark" else "light"}_bottom.png")
  assertEquals(listOf(DEVELOPER_GITHUB,DEVELOPER_X,DEVELOPER_LINKEDIN,DEVELOPER_CONTACT,DISCOVERY_LOOP_SITE,SPOOL_SOURCE),links)
  assertEquals(1,credits)
  val nav=AppNavigator({0},{false},{})
  nav.push(AppRoute.Settings);nav.push(AppRoute.AboutDeveloper);nav.push(AppRoute.CreditsLicenses);nav.push(AppRoute.LicenseNotice("GPL-3.0.txt"))
  nav.pop();assertEquals(AppRoute.CreditsLicenses,nav.visibleRoute())
  nav.pop();assertEquals(AppRoute.AboutDeveloper,nav.visibleRoute())
  nav.pop();assertEquals(AppRoute.Settings,nav.visibleRoute())
 }
 protected fun credits(dark:Boolean) {
  V6ScreenshotTest().installUiHost()
  var notice=""
  compose.setContent { BitChordTheme(darkTheme=dark) {
   Box(Modifier.width(390.dp).height(740.dp).testTag("credits-root")) {
    CreditsLicensesPage(PaddingValues(top=56.dp,bottom=40.dp),{notice=it})
   }
  } }
  compose.onNodeWithText("Original project",useUnmergedTree=true).assertExists()
  compose.onNodeWithText("GNU GPL v3",useUnmergedTree=true).performScrollTo().performClick()
  assertEquals("GPL-3.0.txt",notice)
  compose.mainClock.advanceTimeBy(1000L);compose.waitForIdle()
  compose.onNodeWithTag("credits-root").captureRoboImage("build/outputs/roborazzi/credits_${if(dark)"dark" else "light"}_top.png")
  compose.onNodeWithText("Independent app",useUnmergedTree=true).performScrollTo()
  compose.mainClock.advanceTimeBy(1000L);compose.waitForIdle()
  compose.onNodeWithTag("credits-root").captureRoboImage("build/outputs/roborazzi/credits_${if(dark)"dark" else "light"}_bottom.png")
  val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  (listOf("GPL-3.0.txt")+BUNDLED_NOTICES.map{it.first}).forEach {
   assertTrue("Bundled notice readable: $it",context.assets.open("licenses/$it").bufferedReader().use{it.readText()}.length>100)
  }
 }
}
class AboutDeveloperScreenshotTest_dark:AboutDeveloperScreenshotTestBase() {
 @Test fun check() = verify(true)
}
class AboutDeveloperScreenshotTest_light:AboutDeveloperScreenshotTestBase() {
 @Test fun check() = verify(false)
}
class AboutDeveloperScreenshotTest_large:AboutDeveloperScreenshotTestBase() {
 @Test fun check() = verify(true,320,2f)
}
class AboutDeveloperScreenshotTest_creditsDark:AboutDeveloperScreenshotTestBase() {
 @Test fun check() = credits(true)
}
class AboutDeveloperScreenshotTest_creditsLight:AboutDeveloperScreenshotTestBase() {
 @Test fun check() = credits(false)
}

class AboutDeveloperScreenshotTest_settingsFlow:AboutDeveloperScreenshotTestBase(){
 @Test fun settingsEntrySearchAndBack() {
  V6ScreenshotTest().installUiHost()
  val nav=AppNavigator({0},{false},{})
  nav.push(AppRoute.Settings)
  compose.setContent { BitChordTheme(darkTheme=true) {
   Box(Modifier.width(390.dp).height(740.dp).testTag("settings-flow")) {
    when(val route=nav.visibleRoute()) {
     AppRoute.Settings -> SettingsScreen(windowWidth=390.dp,signedIn=false,account=null,
      onSignIn={},onSignOut={},onAccountScrobbling={},onEqualizer={},onOpenReplay={},onLyricsSources={},
      onTranslationLanguage={},onSources={},onListenTogether={},onWearOs={},onSpotifyCanvasAuth={},onAppLanguage={},
      onEditSignatureApi={},onConfirmBitPerfect={},onConfirmFixedVolume={},contentPadding=PaddingValues(top=56.dp,bottom=40.dp),
      onAboutDeveloper={nav.push(AppRoute.AboutDeveloper)},onCreditsLicenses={nav.push(AppRoute.CreditsLicenses)})
     AppRoute.AboutDeveloper -> AboutDeveloperPage(PaddingValues(top=56.dp,bottom=40.dp),{nav.push(AppRoute.CreditsLicenses)},{})
     AppRoute.CreditsLicenses -> CreditsLicensesPage(PaddingValues(top=56.dp,bottom=40.dp),{nav.push(AppRoute.LicenseNotice(it))})
     is AppRoute.LicenseNotice -> LicenseNoticePage(route.fileName,PaddingValues(top=56.dp,bottom=40.dp))
     else -> Unit
    }
    com.music.bitchord.ui.components.FrostedTopBar(title="",onBack={nav.pop()},scrolled={false},
     transparentBackdrop=true,artworkPageChrome=true,settingsHomeChrome=true,actions={})
   }
  } }
  compose.onNodeWithText("About the developer",useUnmergedTree=true).performScrollTo().performClick()
  compose.onAllNodesWithText("Joyal George").onFirst().assertExists()
  compose.onNodeWithText("Credits and licenses",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("GNU GPL v3",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNodeWithText("GNU GENERAL PUBLIC LICENSE",substring=true,useUnmergedTree=true).assertExists()
  repeat(3){compose.onNodeWithContentDescription("Back").performClick()}
  compose.onNode(hasSetTextAction(),useUnmergedTree=true).performTextInput("Discovery Loop")
  compose.onNodeWithText("About the developer",useUnmergedTree=true).assertExists().performClick()
  compose.onAllNodesWithText("Joyal George").onFirst().assertExists()
  compose.mainClock.advanceTimeBy(1000L);compose.waitForIdle()
  compose.onNodeWithTag("settings-flow").captureRoboImage("build/outputs/roborazzi/settings_about_back_flow.png")
 }
}
