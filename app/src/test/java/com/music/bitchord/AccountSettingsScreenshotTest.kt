@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.auth.*
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.navigation.*
import com.music.bitchord.ui.screens.*
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers=RobolectricDeviceQualifiers.Pixel7)
abstract class AccountSettingsScreenshotTestBase {
 @get:Rule val compose=createComposeRule()
 protected fun flow(dark:Boolean,large:Boolean=false,signedIn:Boolean=true) {
  V6ScreenshotTest().installUiHost()
  val sessions=listOf(GoogleAccountSession("a","fixture-only",name="Personal account",email="listener@example.test",profiles=listOf(YouTubeProfile("p","Personal","@personal"),YouTubeProfile("b","Music channel","@music"))))
  var saved by mutableStateOf(if(signedIn)sessions else emptyList())
  var active by mutableStateOf("p");var selections=0;var adds=0;var removes=0;var services=0
  val nav=AppNavigator({1},{false},{})
  compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
   BitChordTheme(darkTheme=dark) { Box(Modifier.width(if(large)320.dp else 390.dp).height(740.dp).testTag("account-flow")) {
    when(nav.visibleRoute()) {
     null -> TopBarAccountButton(null,{nav.openSettings()})
     AppRoute.Settings -> SettingsScreen(windowWidth=390.dp,signedIn=saved.isNotEmpty(),account=null,
      currentProfileName=if(active=="p")"Personal" else "Music channel",
      onProfile={nav.push(AppRoute.Profile)},onSwitchProfile={nav.push(AppRoute.SwitchProfile)},onManageAccounts={nav.push(AppRoute.ManageAccounts)},
      onSignIn={adds++},onSignOut={},onAccountScrobbling={services++;nav.push(AppRoute.AccountScrobbling)},onEqualizer={},onOpenReplay={},onLyricsSources={},
      onTranslationLanguage={},onSources={},onListenTogether={},onWearOs={},onSpotifyCanvasAuth={},onAppLanguage={},
      onEditSignatureApi={},onConfirmBitPerfect={},onConfirmFixedVolume={},contentPadding=PaddingValues(top=56.dp,bottom=20.dp))
     AppRoute.Profile -> ProfileSettingsPage(saved.isNotEmpty(),if(active=="p")"Personal" else "Music channel",null,null,
      {nav.push(AppRoute.SwitchProfile)},{adds++},{nav.push(AppRoute.ManageAccounts)},PaddingValues(top=56.dp,bottom=20.dp))
     AppRoute.AccountScrobbling -> AccountAndScrobblingScreen(signedIn=true,account=null,channelName="Personal",
      onSignIn={adds++},onSwitchChannel={nav.push(AppRoute.SwitchProfile)},onSignOut={},onOpenListenBrainzLogin={},
      onOpenLastfmLogin={},onOpenDiscord={},onOpenSpotify={},contentPadding=PaddingValues(top=56.dp,bottom=20.dp),
      onProfileClick={nav.openSettings()})
     AppRoute.SwitchProfile,AppRoute.ManageAccounts -> AccountSettingsPage(nav.visibleRoute()==AppRoute.ManageAccounts,
      saved,"a",active,{_,p->active=p.profileId;selections++;nav.pop()},{adds++},{saved=emptyList();removes++},PaddingValues(top=56.dp,bottom=20.dp))
     else -> Text("Unexpected route")
    }
    if(nav.visibleRoute()!=null) FrostedTopBar(title="",onBack={nav.pop()},scrolled={false},transparentBackdrop=true,artworkPageChrome=true,settingsHomeChrome=true,actions={})
   } }
  } }
  compose.onNodeWithContentDescription("Settings").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, -160f)) }
  assertNull("Avatar swipe cannot select a profile or navigate",nav.visibleRoute());assertEquals(0,selections)
  compose.onNodeWithContentDescription("Settings").performClick()
  assertEquals(AppRoute.Settings,nav.visibleRoute());nav.push(AppRoute.Settings);assertEquals(1,nav.entries.size)
  compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
  compose.onNodeWithTag("account-flow").captureRoboImage("build/outputs/roborazzi/accounts_${dark}_${large}_${signedIn}_settings.png")
  compose.onNodeWithText("Switch profile").assertDoesNotExist()
  compose.onNodeWithText("Add account").assertDoesNotExist()
  compose.onNodeWithText("Manage accounts").assertDoesNotExist()
  compose.onNodeWithText(if(signedIn)"Personal" else "Not signed in").performClick()
  assertEquals(AppRoute.Profile,nav.visibleRoute());assertEquals(0,selections)
  compose.onNodeWithTag("account-flow").captureRoboImage("build/outputs/roborazzi/profile_hub_${dark}_${large}_${signedIn}.png")
  if(!signedIn) {
   compose.onNodeWithText("Switch profile").performScrollTo().assertHasNoClickAction()
   compose.onNodeWithText("Manage account").performScrollTo().assertHasNoClickAction()
   compose.onNodeWithText("Add account").performScrollTo().performClick();assertEquals(1,adds)
   compose.onNodeWithContentDescription("Back").performClick();assertEquals(AppRoute.Settings,nav.visibleRoute())
   compose.onNodeWithContentDescription("Back").performClick();assertNull(nav.visibleRoute());return
  }
  compose.onNodeWithText("Switch profile").performScrollTo().performClick()
  compose.onNodeWithText("Selected").assertExists()
  compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
  compose.onNodeWithTag("account-flow").captureRoboImage("build/outputs/roborazzi/accounts_${dark}_${large}_switch.png")
  compose.onNodeWithText("Music channel").performScrollTo().performClick()
  assertEquals("b",active);assertEquals(1,selections);assertEquals(AppRoute.Profile,nav.visibleRoute())
  compose.onNodeWithContentDescription("Back").performClick();assertEquals(AppRoute.Settings,nav.visibleRoute())
  compose.onNodeWithText("Connected services").performScrollTo().performClick()
  compose.onNodeWithText("Signed in").performScrollTo().performClick()
  assertEquals(AppRoute.Settings,nav.visibleRoute());assertEquals(1,nav.entries.size)
  compose.onNodeWithText("Music channel").performScrollTo().performClick()
  compose.onNodeWithText("Manage account").performScrollTo().performClick()
  compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
  compose.onNodeWithTag("account-flow").captureRoboImage("build/outputs/roborazzi/accounts_${dark}_${large}_manage.png")
  compose.onNodeWithText("Remove account").performScrollTo().performClick()
  compose.onNodeWithText("Cancel").performClick();assertEquals(0,removes);assertEquals(1,saved.size)
  compose.onNodeWithText("Remove account").performClick()
  compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
  compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/accounts_${dark}_${large}_confirm.png")
  compose.onNodeWithText("Remove",useUnmergedTree=true).performClick();assertEquals(1,removes)
  compose.onNodeWithText("No saved accounts").assertExists()
  compose.onNodeWithContentDescription("Back").performClick();assertEquals(AppRoute.Profile,nav.visibleRoute())
  compose.onNodeWithText("Add account").performScrollTo().performClick();assertEquals(1,adds)
  compose.onNodeWithContentDescription("Back").performClick();assertEquals(AppRoute.Settings,nav.visibleRoute())
  compose.onNodeWithContentDescription("Back").performClick();assertNull(nav.visibleRoute())
  assertEquals(1,services)
 }
}
class AccountSettingsScreenshotTest_dark:AccountSettingsScreenshotTestBase() {
 @Test fun check() = flow(true)
}
class AccountSettingsScreenshotTest_light:AccountSettingsScreenshotTestBase() {
 @Test fun check() = flow(false)
}
class AccountSettingsScreenshotTest_large:AccountSettingsScreenshotTestBase() {
 @Test fun check() = flow(true,true)
}
class AccountSettingsScreenshotTest_signedOut:AccountSettingsScreenshotTestBase() {
 @Test fun check() = flow(true,signedIn=false)
}
