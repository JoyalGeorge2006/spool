@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.data.model.*
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.ProfileSettingsPage
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
class SignInBannerDismissTest {
 @get:Rule val compose=createComposeRule()
 private val label="Dismiss sign-in banner"
 @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
 private fun probe(dark:Boolean,large:Boolean,hideFirst:Boolean=false,google:Boolean=false,toggle:Boolean=false) {
  V6ScreenshotTest().installUiHost()
  var hidden by mutableStateOf(hideFirst);var signed by mutableStateOf(false);var signIns=0;var dismisses=0
  compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
   BitChordTheme(darkTheme=dark) { Box(Modifier.width(if(large)320.dp else 390.dp).height(900.dp).background(MaterialTheme.colorScheme.background).testTag("banner-root")) {
    HomeScreen(state=UiState.Success(listOf(HomeShelf("Quick picks",listOf(ShelfItem("Guest song","Fixture artist",null,"fixture-song",null)),layout=ShelfLayout.LIST))),listState=rememberLazyListState(),onItemClick={_,_->},onRetry={},refreshing=false,onRefresh={},pullState=rememberPullToRefreshState(),contentPadding=PaddingValues(),title="Listen",signedIn=signed,onSignIn={signIns++},signInBannerDismissed=hidden,onDismissSignInBanner={dismisses++;hidden=true},topPicks=emptyList())
   } }
  } }
  if(hideFirst) {compose.onNodeWithContentDescription(label).assertDoesNotExist();return}
  val close=compose.onNodeWithContentDescription(label)
  close.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
  compose.onNodeWithText("Sign in to YouTube Music").assertIsDisplayed()
  compose.onNodeWithTag("banner-root").captureRoboImage("build/outputs/roborazzi/banner_${dark}_${large}_visible.png")
  if(google) {compose.onNodeWithText("Sign in with Google").performClick();assertEquals(1,signIns);assertEquals(0,dismisses);return}
  val oldTop=compose.onNodeWithText("Quick picks").fetchSemanticsNode().boundsInRoot.top
  close.performTouchInput { click() }
  assertEquals(1,dismisses);assertEquals(0,signIns)
  close.assertDoesNotExist();compose.onNodeWithText("Sign in with Google").assertDoesNotExist()
  assertTrue(compose.onNodeWithText("Quick picks").fetchSemanticsNode().boundsInRoot.top<oldTop)
  compose.onNodeWithTag("banner-root").captureRoboImage("build/outputs/roborazzi/banner_${dark}_${large}_dismissed.png")
  if(toggle) {
   compose.runOnIdle {signed=true};compose.runOnIdle{signed=false}
   close.assertDoesNotExist();assertEquals(0,signIns)
  }
 }
 @Test fun darkDismissTouchDoesNotSignIn()=probe(true,false)
 @Test fun lightDismissTouchDoesNotSignIn()=probe(false,false)
 @Test fun darkLargeTextHasSeparateTarget()=probe(true,true)
 @Test fun lightLargeTextHasSeparateTarget()=probe(false,true)
 @Test fun savedHiddenChoiceSkipsCard()=probe(true,false,hideFirst=true)
 @Test fun googleButtonStillSignsIn()=probe(false,false,google=true)
 @Test fun signInOutNeverReshowsDismissedCard()=probe(true,false,toggle=true)
 @Test fun profileAddAccountRemainsAvailable() {
  V6ScreenshotTest().installUiHost();var adds=0
  compose.setContent {BitChordTheme(darkTheme=true){ProfileSettingsPage(false,null,null,null,{},{adds++},{},PaddingValues())}}
  compose.onNodeWithText("Add account").assertIsDisplayed().performClick();assertEquals(1,adds)
 }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class SignInBannerPersistenceTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private val prefs get()=context.getSharedPreferences("bitchord_settings",Context.MODE_PRIVATE)
 private fun boot() {AppSettings.init(context,AuthStore(context))}
 @Before fun setup(){prefs.edit().remove("spool_home_signin_dismissed").commit();boot()}
 @After fun cleanup(){prefs.edit().remove("spool_home_signin_dismissed").commit();AppSettings.reload()}
 @Test fun firstInstallShowsAndDismissalSurvivesReload() {
  assertFalse(AppSettings.homeSignInBannerDismissed.value)
  AppSettings.dismissHomeSignInBanner();assertTrue(prefs.getBoolean("spool_home_signin_dismissed",false))
  AppSettings.homeSignInBannerDismissed.value=false;AppSettings.reload();assertTrue(AppSettings.homeSignInBannerDismissed.value)
 }
 @Test fun freshSettingsInitializationReadsDisk() {
  AppSettings.dismissHomeSignInBanner();AppSettings.homeSignInBannerDismissed.value=false;boot();assertTrue(AppSettings.homeSignInBannerDismissed.value)
 }
 @Test fun accountSignOutDoesNotResetChoiceOrOtherSettings() {
  prefs.edit().putString("banner_test_unrelated","preserve").commit()
  val auth=AuthStore(context);auth.cookie="fixture-only";AppSettings.dismissHomeSignInBanner();auth.signOut();AppSettings.reload()
  assertTrue(AppSettings.homeSignInBannerDismissed.value);assertEquals("preserve",prefs.getString("banner_test_unrelated",null));prefs.edit().remove("banner_test_unrelated").commit()
 }
}
