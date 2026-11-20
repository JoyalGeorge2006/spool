package com.music.bitchord

import com.music.bitchord.data.AppUpdateChecker
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class UpdateAssetSelectionTest {
 private fun release(vararg names:String)=buildJsonObject {
  put("assets",buildJsonArray { names.forEach { name -> add(buildJsonObject {
   put("name",name);put("state","uploaded");put("browser_download_url","https://github.com/JoyalGeorge2006/spool/releases/download/v99.0.0/$name")
  }) } })
 }
 private fun select(vararg names:String)=AppUpdateChecker.apkAssetUrl(release(*names),"spool.apk","spool-",listOf("arm64-v8a","armeabi-v7a"))
 @Test fun exactPhoneNameWinsOverAliasAndOtherPlatforms() {
  assertTrue(select("spool-tv.apk","spool-wear.apk","spool-v99.0.0.apk","spool.apk")!!.endsWith("/spool.apk"))
 }
 @Test fun oldUniversalAliasStillWorks() { assertTrue(select("spool-v99.0.0.apk")!!.endsWith("/spool-v99.0.0.apk")) }
 @Test fun legacyArchitectureFallbackIsOrdered() {
  assertTrue(select("spool-v99.0.0_armeabi-v7a.apk","spool-v99.0.0_arm64-v8a.apk")!!.endsWith("_arm64-v8a.apk"))
 }
 @Test fun noOtherDeviceApkIsEverSelected() { assertNull(select("spool-tv.apk","spool-wear.apk","spool-tv-v99.0.0.apk")) }
 @Test fun tvExactNameIsSeparate() {
  assertTrue(AppUpdateChecker.apkAssetUrl(release("spool.apk","spool-tv.apk"),"spool-tv.apk","BitChordTV-",listOf("arm64-v8a"))!!.endsWith("/spool-tv.apk"))
 }
 @Test fun duplicatePreferredAssetFailsClosed() { assertNull(select("spool.apk","spool.apk","spool-v99.0.0.apk")) }
}
