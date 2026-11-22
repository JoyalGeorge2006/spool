@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.layout.*
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
import com.music.bitchord.ui.screens.AppUpdatesContent
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
class OtaFlowTest {
 @get:Rule val compose=createComposeRule()
 private val info=AppUpdateChecker.UpdateInfo("99.0.0","https://github.com/JoyalGeorge2006/spool/releases/tag/v99.0.0","https://github.com/JoyalGeorge2006/spool/releases/download/v99.0.0/spool-v99.0.0.apk","Fixture release notes","a".repeat(64),140619869)
 private fun screen(state:AppUpdateChecker.DownloadState,dark:Boolean=false,large:Boolean=false,withInfo:Boolean=true,stop:()->Unit={},leave:()->Unit={},full:()->Unit={}) {
  V6ScreenshotTest().installUiHost()
  compose.setContent {
   CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
    BitChordTheme(darkTheme=dark) {
     Box(Modifier.width(if(large)320.dp else 390.dp).height(780.dp).testTag("ota-flow")) {
      AppUpdatesContent(PaddingValues(top=56.dp,bottom=36.dp),AppUpdateChecker.CheckState.Failed("Offline latest check"),if(withInfo)info else null,state,true,0,false,{},{},{},{},stop,leave,full)
     }
    }
   }
  }
 }
 @Test fun progressIsNotStopAndLeaveNeverCancels() {
  var stops=0;var leaves=0
  screen(AppUpdateChecker.DownloadState.Downloading(.5f,5000,10000),stop={stops++},leave={leaves++})
  compose.onAllNodesWithText("Downloading update").onFirst().assertExists()
  compose.onNodeWithText("Continue using Spool").performScrollTo().performClick()
  assertEquals(1,leaves);assertEquals(0,stops)
  compose.onNodeWithText("Stop update").performScrollTo().performClick()
  compose.onNodeWithText("Keep updating").performClick();assertEquals(0,stops)
  compose.onNodeWithText("Stop update").performClick()
  compose.onAllNodesWithText("Stop update",useUnmergedTree=true).onLast().performClick();assertEquals(1,stops)
 }
 @Test fun failureVisibleWithoutDiscoveryMetadata() {
  screen(AppUpdateChecker.DownloadState.Failed("Durable missing request failure"),withInfo=false)
  compose.onNodeWithText("Durable missing request failure").performScrollTo().assertIsDisplayed()
 }
 @Test fun fullFallbackRequiresSeparateConsent() {
  var count=0;screen(AppUpdateChecker.DownloadState.Fallback("Base changed"),full={count++})
  compose.onNodeWithText("Download full update").performScrollTo().performClick();assertEquals(0,count)
  compose.onNodeWithText("Not now").performClick();assertEquals(0,count)
 }
 @Test fun waitingCapture(){screen(AppUpdateChecker.DownloadState.Waiting("Waiting for a connection"),dark=true);compose.onNodeWithText("Waiting for a connection").performScrollTo();compose.onNodeWithTag("ota-flow").captureRoboImage("build/outputs/roborazzi/ota_waiting.png")}
 @Test fun checkingLargeCapture(){screen(AppUpdateChecker.DownloadState.Verifying,large=true);compose.onNodeWithTag("ota-flow").captureRoboImage("build/outputs/roborazzi/ota_checking_large.png")}
 @Test fun checkingLargeLowerScrollCapture() {
  var leaves=0;var stops=0
  screen(AppUpdateChecker.DownloadState.Verifying,large=true,leave={leaves++},stop={stops++})
  compose.onNodeWithText("Stop update").performScrollTo().assertIsDisplayed()
  compose.onNodeWithTag("ota-flow").captureRoboImage("build/outputs/roborazzi/ota_checking_large_lower.png")
  compose.onNodeWithText("Continue using Spool").performScrollTo().performClick()
  assertEquals(1,leaves);assertEquals(0,stops)
 }
 @Test fun readyDarkCapture() {
  screen(AppUpdateChecker.DownloadState.Ready(java.io.File("/fixture/candidate.apk")),dark=true)
  compose.onNodeWithText("Install update").performScrollTo().assertIsDisplayed()
  compose.onNodeWithTag("ota-flow").captureRoboImage("build/outputs/roborazzi/ota_ready_dark.png")
 }
 @Test fun stopConfirmationCapture() {
  screen(AppUpdateChecker.DownloadState.Waiting("Waiting for a connection"))
  compose.onNodeWithText("Stop update").performScrollTo().performClick()
  compose.onNodeWithText("Stop this update?").assertIsDisplayed()
  compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/ota_stop_confirmation.png")
 }

}
