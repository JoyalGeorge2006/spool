@file:Suppress("INVISIBLE_MEMBER","INVISIBLE_REFERENCE")
package com.music.bitchord

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable emulator-only bootstrap46, never a distributed app build. */
@RunWith(AndroidJUnit4::class)
class UpdateRuntimeFixtureTest {
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 @Test fun beginExactPublished47Download()=runBlocking {
  assertEquals("Only the disposable bootstrap46 may run this fixture",46,BuildConfig.VERSION_CODE)
  UpdateDownloads.resetMemoryForTest(context);UpdateStore.clear(context)
  context.getSharedPreferences("runtime-fixture",Context.MODE_PRIVATE).edit().putString("sentinel","preserve-ota47").commit()
  File(context.filesDir,"ota-runtime-sentinel").writeText("preserve-ota47")
  UpdateDownloads.start(context,AppUpdateChecker.UpdateInfo("1.9.9","https://github.com/JoyalGeorge2006/spool/releases/tag/v1.9.9","https://github.com/JoyalGeorge2006/spool/releases/download/v1.9.9/spool-v1.9.9_arm64-v8a.apk","Exact immutable published47 fixture","4bb7387b1b498d99c67b2bfeb9f70cb7e64f675170c9d8db834e22870189f57d",140619869L))
  assertTrue(UpdateDownloads.snapshot()!!.id>=0)
 }
 @Test fun openPrepared47AndroidConfirmation()=runBlocking {
  assertEquals(46,BuildConfig.VERSION_CODE)
  UpdateDownloads.restore(context)
  assertEquals("ready",UpdateDownloads.snapshot()!!.phase)
  UpdateInstaller.install(context)
  withTimeout(20000) {
   while(UpdateDownloads.snapshot()?.phase!="confirmation")delay(100)
  }
  UpdateInstaller.continueConfirmation(context)
  assertEquals("confirmation",UpdateDownloads.snapshot()!!.phase)
 }
}
