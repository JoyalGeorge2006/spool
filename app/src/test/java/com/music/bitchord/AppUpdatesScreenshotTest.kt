@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="w390dp-h844dp-mdpi")
abstract class AppUpdatesScreenshotBase {
 @get:Rule val compose=createComposeRule()
 protected fun capture(dark:Boolean,guarded:Boolean,available:Boolean=false,large:Boolean=false) {
  V6ScreenshotTest().installUiHost()
  var checks=0;var downloads=0;var auto=true
  val info=if(available) AppUpdateChecker.UpdateInfo("9.9.9", "https://github.com/JoyalGeorge2006/spool/releases/fixture", null,
   "Offline test fixture: Related songs and Profile fixes.", "0".repeat(64),139816943L) else null
  compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
   BitChordTheme(darkTheme=dark) {
    Box(Modifier.width(if(large)320.dp else 390.dp).height(780.dp).testTag("updates-root")) {
     AppUpdatesContent(PaddingValues(top=56.dp,bottom=36.dp),
      if(available)AppUpdateChecker.CheckState.Available else AppUpdateChecker.CheckState.UpToDate,
      info,AppUpdateChecker.DownloadState.Idle,auto,0,guarded,
      {checks++},{auto=it},{downloads++},{},{})
    }
   }
  } }
  if(guarded) compose.onNodeWithText("Separate APK updates").assertExists()
  else if(available) {
   compose.onNodeWithText("Download update").performScrollTo().performClick();assertEquals(1,downloads)
  } else {
   compose.onNodeWithText("Check for updates").performScrollTo().performClick();assertEquals(1,checks)
  }
  compose.onNodeWithText("Status",useUnmergedTree=true).performScrollTo()
  compose.waitForIdle()
  compose.onNodeWithTag("updates-root").captureRoboImage("build/outputs/roborazzi/updates_${dark}_${guarded}_${available}_${large}_top.png")
  compose.onNodeWithText("Update source",useUnmergedTree=true).performScrollTo()
  compose.waitForIdle()
  compose.onNodeWithTag("updates-root").captureRoboImage("build/outputs/roborazzi/updates_${dark}_${guarded}_${available}_${large}_bottom.png")
 }
}
class AppUpdatesScreenshot_light:AppUpdatesScreenshotBase(){@Test fun check()=capture(false,false)}
class AppUpdatesScreenshot_dark:AppUpdatesScreenshotBase(){@Test fun check()=capture(true,false)}
class AppUpdatesScreenshot_available:AppUpdatesScreenshotBase(){@Test fun check()=capture(true,false,true)}
class AppUpdatesScreenshot_large:AppUpdatesScreenshotBase(){@Test fun check()=capture(false,false,large=true)}
class AppUpdatesScreenshot_current:AppUpdatesScreenshotBase(){@Test fun check()=capture(false,false)}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class AppUpdateSafetyTest {
 @Test fun numericVersionsDoNotDowngrade() {
  val method=AppUpdateChecker::class.java.getDeclaredMethod("isNewer",String::class.java,String::class.java).apply{isAccessible=true}
  fun newer(a:String,b:String)=method.invoke(AppUpdateChecker,a,b) as Boolean
  assertTrue(newer("1.10.0","1.9.9"));assertFalse(newer("1.9.2","1.9.3"))
  assertFalse(newer("1.9.3","1.9.3"));assertTrue(newer("1.9.3","1.9.3-beta1"))
 }
 @Test fun archiveIdentityRejectsWrongPackageSignerVersionAndDowngrade() {
  fun validate(pkg:String="app.spool.music",code:Long=42,version:String="1.9.4",signers:Set<String> = setOf("same-key")) {
   AppUpdateChecker.validateArchiveIdentity(pkg,code,version,signers,
    "app.spool.music",41,setOf("same-key"),"1.9.4")
  }
  validate()
  assertTrue(runCatching{validate(pkg="app.spool.music.dev")}.isFailure)
  assertTrue(runCatching{validate(code=41)}.isFailure)
  assertTrue(runCatching{validate(code=40)}.isFailure)
  assertTrue(runCatching{validate(version="1.9.5")}.isFailure)
  assertTrue(runCatching{validate(signers=setOf("different-key"))}.isFailure)
  assertTrue(runCatching{validate(signers=emptySet())}.isFailure)
 }
 @Test fun rejectsSizeMismatchBeforeArchiveParsing() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val file=File(context.cacheDir,"mismatch.apk").apply{writeBytes(byteArrayOf(1,2,3))}
  val info=AppUpdateChecker.UpdateInfo("1.9.4","",null,null,"0".repeat(64),4)
  val failure=runCatching{AppUpdateChecker.verifyDownloadedApk(context,file,info)}.exceptionOrNull()
  assertNotNull(failure);assertTrue(failure!!.message!!.contains("size"));file.delete()
 }
 @Test fun rejectsChecksumMismatchBeforeArchiveParsing() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val file=File(context.cacheDir,"checksum.apk").apply{writeBytes(byteArrayOf(1,2,3))}
  val info=AppUpdateChecker.UpdateInfo("1.9.4","",null,null,"0".repeat(64),3)
  val failure=runCatching{AppUpdateChecker.verifyDownloadedApk(context,file,info)}.exceptionOrNull()
  assertNotNull(failure);assertTrue(failure!!.message!!.contains("checksum"));file.delete()
 }
}
