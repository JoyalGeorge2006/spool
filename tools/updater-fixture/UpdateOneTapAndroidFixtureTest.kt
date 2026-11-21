@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")
package com.music.bitchord

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/** Disposable dev58 -> dev59 fixture only. No test backend and no remote download.
 * Host must push exact fixture59.apk (+ fixture.spdelta for delta case),
 * reset REQUEST_INSTALL_PACKAGES to deny, and run one method per instrumentation.
 * Host verifies actual installed59 identity after install terminates the old process.
 */
@RunWith(AndroidJUnit4::class)
class UpdateOneTapAndroidFixtureTest {
 private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
 private val context get()=instrumentation.targetContext
 private val ui get()=instrumentation.uiAutomation
 private fun shell(command: String) { ui.executeShellCommand(command).use { fd -> FileInputStream(fd.fileDescriptor).use { it.readBytes() } } }
 private suspend fun until(label: String, timeout: Long=45000, condition: () -> Boolean) {
  withTimeout(timeout) { while(!condition())delay(100) };println("FIXTURE-OK $label")
 }
 private fun shot(name: String) {
  val bitmap=requireNotNull(ui.takeScreenshot());val f=File(context.getExternalFilesDir(null),"$name.png")
  f.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) };bitmap.recycle()
  println("SHOT-SAVED ${f.absolutePath} bytes=${f.length()}")
 }
 private val fixtureLabel get()=requireNotNull(InstrumentationRegistry.getArguments().getString("fixtureLabel"))
 private fun nodes(): List<AccessibilityNodeInfo> {
  val out=mutableListOf<AccessibilityNodeInfo>()
  fun walk(n: AccessibilityNodeInfo?) { if(n==null)return;out.add(n);for(i in 0 until n.childCount)walk(n.getChild(i)) }
  walk(ui.rootInActiveWindow);return out
 }
 private fun bounds(n: AccessibilityNodeInfo)=android.graphics.Rect().also { n.getBoundsInScreen(it) }
 private fun assertWindow(pkg: String, label: String) {
  val root=requireNotNull(ui.rootInActiveWindow);assertEquals(pkg,root.packageName?.toString())
  assertTrue("Expected exact app label in the active system window",nodes().any { it.packageName?.toString()==pkg && it.isVisibleToUser && it.text?.toString()==label })
 }
 private fun exactClick(pkg: String, predicate: (AccessibilityNodeInfo) -> Boolean, unchecked: Boolean=false) {
  val matches=nodes().filter { it.packageName?.toString()==pkg && it.isVisibleToUser && it.isEnabled && it.isClickable && !bounds(it).isEmpty && predicate(it) }
  assertEquals("Refuse missing/ambiguous system control",1,matches.size)
  val node=matches.single();val before=bounds(node)
  if(unchecked) { assertTrue(node.isCheckable);assertFalse(node.isChecked) }
  assertTrue("Retained control changed before click",node.refresh());assertEquals(before,bounds(node))
  assertTrue(node.isVisibleToUser && node.isEnabled && node.isClickable)
  if(unchecked)assertFalse(node.isChecked)
  assertTrue("System rejected exact retained control action",node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
 }
 private fun assertSettings() {
  assertWindow("com.android.settings",fixtureLabel)
  assertEquals("permission",UpdateDownloads.snapshot()!!.phase)
  assertTrue(UpdateDownloads.snapshot()!!.permissionHandoffLaunched)
  assertTrue(nodes().any { it.isVisibleToUser && it.text?.toString()?.equals("Allow from this source",true)==true })
 }
 private fun installerPackage(): String {
  val pkg=requireNotNull(ui.rootInActiveWindow?.packageName?.toString())
  assertTrue("Unexpected installer package",pkg in setOf("com.google.android.packageinstaller","com.android.packageinstaller"))
  return pkg
 }
 private fun confirmationAction(positive: Boolean) {
  assertEquals("confirmation",UpdateDownloads.snapshot()!!.phase)
  val pkg=installerPackage();assertWindow(pkg,fixtureLabel)
  val id=if(positive)"android:id/button1" else "android:id/button2"
  exactClick(pkg,{ it.viewIdResourceName==id && if(positive)it.text?.toString()?.lowercase() in setOf("install","update") else it.text?.toString()?.equals("Cancel",true)==true })
 }
 private suspend fun stableSpool(a: UpdateAttempt, millis: Long=5000) {
  until("Spool returns to foreground") { ui.rootInActiveWindow?.packageName?.toString()==context.packageName }
  val deadline=android.os.SystemClock.elapsedRealtime()+millis;var samples=0
  while(android.os.SystemClock.elapsedRealtime()<deadline) {
   assertEquals("Unexpected external window after cancel/denial",context.packageName,ui.rootInActiveWindow?.packageName?.toString())
   assertEquals(a,UpdateDownloads.snapshot());assertFalse(UpdateDownloads.snapshot()!!.autoInstallPending)
   samples++;delay(100)
  }
  assertTrue(samples>=20);println("FIXTURE-STABLE-SPOOL samples=$samples")
 }
 private suspend fun seedCompleted(delta: Boolean=false): UpdateAttempt {
  val service=ui.serviceInfo;service.flags=service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;ui.serviceInfo=service
  assertEquals("Disposable current fixture only",58,BuildConfig.VERSION_CODE)
  assertTrue(BuildConfig.DEBUG);assertFalse(AppUpdateChecker.externallyProvided)
  assertNull("Production policy must execute without hooks",UpdateInstaller.testBackend)
  UpdateDownloads.monitorEnabled=false;UpdateDownloads.resetMemoryForTest(context);UpdateStore.clear(context)
  val apk=File(context.getExternalFilesDir(null),"fixture59.apk");assertTrue(apk.isFile && apk.length()>0)
  val targetHash=SpoolDelta.sha256(apk) {};val base=File(context.applicationInfo.sourceDir)
  val patch=File(context.getExternalFilesDir(null),"fixture.spdelta")
  if(delta)assertTrue(patch.isFile && patch.length()>0)
  val d=if(delta)UpdateCatalog.Delta(58,base.length(),SpoolDelta.sha256(base) {},"https://github.com/JoyalGeorge2006/spool/releases/download/local-fixture/fixture.spdelta",patch.length(),SpoolDelta.sha256(patch) {},59,26,"x86_64") else null
  val info=AppUpdateChecker.UpdateInfo("99.0.0","https://github.com/JoyalGeorge2006/spool/releases/tag/local-fixture","https://github.com/JoyalGeorge2006/spool/releases/download/local-fixture/fixture59.apk","Local fixture metadata, no request is sent",targetHash,apk.length())
  var a=UpdateAttempt(UUID.randomUUID().toString(),info,"queued",delta=d,autoInstallPending=true)
  val transfer=a.transfer(context);require(transfer.parentFile!!.mkdirs() || transfer.parentFile!!.isDirectory)
  (if(delta)patch else apk).copyTo(transfer,overwrite=false)
  val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  val id=try { manager.addCompletedDownload("Spool local fixture","Disposable local completion fixture",false,"application/octet-stream",transfer.path,transfer.length(),false) } catch(e: Exception) { throw AssertionError("Completed local transfer is unsupported on this Android build; stop fixture, do not bypass verifier",e) }
  assertTrue(id>=0)
  a=a.copy(id=id);UpdateStore.write(context,a);UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.restore(context)
  UpdateDownloads.refresh(context,true)
  assertEquals("ready",UpdateDownloads.snapshot()!!.phase);assertTrue(UpdateDownloads.snapshot()!!.autoInstallPending)
  assertEquals(targetHash,SpoolDelta.sha256(UpdateDownloads.snapshot()!!.candidate(context)) {})
  assertEquals(-1,UpdateDownloads.snapshot()!!.session)
  return UpdateDownloads.snapshot()!!
 }
 private suspend fun awaitPermission() {
  until("real Settings launch recorded") { UpdateDownloads.snapshot()?.let { it.phase=="permission" && it.permissionHandoffLaunched }==true }
  assertFalse(context.packageManager.canRequestPackageInstalls());assertSettings();shot("updater-real-settings")
 }
 private suspend fun grantAndReturn() {
  assertSettings();exactClick("com.android.settings",{ it.viewIdResourceName=="android:id/switch_widget" || it.viewIdResourceName=="com.android.settings:id/switch_widget" },unchecked=true)
  until("real package-install permission granted") { context.packageManager.canRequestPackageInstalls() }
  shot("updater-real-settings-granted");shell("input keyevent KEYCODE_BACK")
  until("real PackageInstaller pending user action") { UpdateDownloads.snapshot()?.phase=="confirmation" }
  until("Android installer active") { ui.rootInActiveWindow?.packageName?.toString() in setOf("com.google.android.packageinstaller","com.android.packageinstaller") }
  assertWindow(installerPackage(),fixtureLabel);shot("updater-real-confirmation")
 }
 @Test fun fullCompletionPermissionDenialNeverReopens()=runBlocking {
  seedCompleted();val scenario=ActivityScenario.launch(MainActivity::class.java)
  try {
   awaitPermission();shell("input keyevent KEYCODE_BACK")
   until("denial consumed automatic continuation") { UpdateDownloads.snapshot()?.let { it.phase=="permission" && !it.autoInstallPending }==true }
   scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
   stableSpool(UpdateDownloads.snapshot()!!);assertFalse(context.packageManager.canRequestPackageInstalls());assertEquals(-1,UpdateDownloads.snapshot()!!.session)
   shot("updater-denial-no-reopen")
  } finally { scenario.close() }
 }
 @Test fun fullCompletionPermissionGrantThenRealCancelDoesNotLoop()=runBlocking {
  seedCompleted();val scenario=ActivityScenario.launch(MainActivity::class.java)
  try {
   awaitPermission();grantAndReturn();confirmationAction(positive=false)
   until("real cancellation returns Ready without continuation") { UpdateDownloads.snapshot()?.let { it.phase=="ready" && !it.autoInstallPending }==true }
   val a=UpdateDownloads.snapshot()!!;scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
   stableSpool(a);shot("updater-real-cancel-no-loop")
  } finally { scenario.close() }
 }
 @Test fun deltaCompletionPermissionGrantThenRealInstall()=runBlocking {
  seedCompleted(delta=true);File(context.filesDir,"one-tap-fixture-sentinel").writeText("preserved-local-fixture")
  ActivityScenario.launch(MainActivity::class.java)
  awaitPermission();grantAndReturn();println("FIXTURE-INSTALL-REQUESTED target=59");confirmationAction(positive=true)
  // Process death is expected during self-update, not treated as proof of success.
  delay(20000);error("Host must independently verify installed59; old instrumentation remained alive")
 }
 @Test fun verifyInstalled59AndSentinel()=runBlocking {
  val installed=context.packageManager.getPackageInfo(context.packageName,0)
  assertEquals(59L,installed.longVersionCode);assertEquals("99.0.0",installed.versionName)
  assertEquals("preserved-local-fixture",File(context.filesDir,"one-tap-fixture-sentinel").readText())
  UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.refresh(context,true)
  assertEquals("installed",UpdateDownloads.snapshot()!!.phase);println("FIXTURE-INSTALLED59-VERIFIED")
 }
}
