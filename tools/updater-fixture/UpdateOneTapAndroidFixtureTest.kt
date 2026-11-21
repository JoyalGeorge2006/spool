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
 private fun nodes(root: AccessibilityNodeInfo?=ui.rootInActiveWindow): List<AccessibilityNodeInfo> {
  val out=mutableListOf<AccessibilityNodeInfo>()
  fun walk(n: AccessibilityNodeInfo?) { if(n==null)return;out.add(n);for(i in 0 until n.childCount)walk(n.getChild(i)) }
  walk(root);return out
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
 private fun installerButtons(): Pair<AccessibilityNodeInfo,AccessibilityNodeInfo>? {
  val root=ui.rootInActiveWindow ?: return null
  val pkg="com.android.packageinstaller"
  if(root.packageName?.toString()!=pkg)return null
  val tree=nodes(root)
  fun exactText(n: AccessibilityNodeInfo, text: String)=n.packageName?.toString()==pkg && n.isVisibleToUser && n.text?.toString()==text
  if(tree.count { exactText(it,fixtureLabel) }!=1 || tree.count { exactText(it,"Do you want to update this app?") }!=1)return null
  fun button(id: String, text: String): AccessibilityNodeInfo? = tree.filter {
   it.packageName?.toString()==pkg && it.viewIdResourceName=="$pkg:id/$id" && it.text?.toString()?.equals(text,true)==true &&
    it.className?.toString()=="android.widget.Button" && it.isVisibleToUser && it.isEnabled && it.isClickable && !bounds(it).isEmpty
  }.singleOrNull()
  val positive=button("button1","Update") ?: return null
  val negative=button("button2","Cancel") ?: return null
  if(positive==negative)return null
  return positive to negative
 }
 private fun confirmationAction(positive: Boolean) {
  try {
   assertEquals("confirmation",UpdateDownloads.snapshot()!!.phase)
   assertEquals("com.android.packageinstaller",installerPackage());assertWindow("com.android.packageinstaller",fixtureLabel)
   val pair=requireNotNull(installerButtons()) { "Missing or ambiguous exact Android14 update confirmation controls" }
   val node=if(positive)pair.first else pair.second
   val before=bounds(node)
   recordPermissionWindowEvidence(if(positive)"updater-install-before-action" else "updater-cancel-before-action")
   assertTrue("Retained installer control changed before click",node.refresh());assertEquals(before,bounds(node))
   val current=requireNotNull(installerButtons()) { "Installer confirmation changed before click" }
   assertEquals(pair,current);assertTrue(node.isVisibleToUser && node.isEnabled && node.isClickable)
   assertEquals("confirmation",UpdateDownloads.snapshot()!!.phase)
   assertTrue("System rejected exact retained installer action",node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
  } catch(error: Throwable) {
   recordPermissionWindowEvidence(if(positive)"updater-install-action-failure" else "updater-cancel-action-failure")
   throw error
  }
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
  val id=try { manager.addCompletedDownload("Spool local fixture","Disposable local completion fixture",false,"application/octet-stream",transfer.path,transfer.length(),true) } catch(e: Exception) { throw AssertionError("Completed local transfer is unsupported on this Android build; stop fixture, do not bypass verifier",e) }
  assertTrue(id>=0)
  a=a.copy(id=id);UpdateStore.write(context,a);UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.restore(context)
  UpdateDownloads.refresh(context,true)
  assertEquals("ready",UpdateDownloads.snapshot()!!.phase);assertTrue(UpdateDownloads.snapshot()!!.autoInstallPending)
  assertEquals(targetHash,SpoolDelta.sha256(UpdateDownloads.snapshot()!!.candidate(context)) {})
  assertEquals(-1,UpdateDownloads.snapshot()!!.session)
  return UpdateDownloads.snapshot()!!
 }
 private fun recordPermissionWindowEvidence(name: String) {
  // Evidence only. Never let screenshot/tree collection replace the assertion.
  runCatching { shot(name) }.onFailure { println("FIXTURE-EVIDENCE-ERROR screenshot ${it.javaClass.name}") }
  runCatching {
   val state=nodes().joinToString("\n") { n ->
    "package=${n.packageName} id=${n.viewIdResourceName} class=${n.className} checkable=${n.isCheckable} checked=${n.isChecked} text=${n.text} visible=${n.isVisibleToUser} enabled=${n.isEnabled} clickable=${n.isClickable} bounds=${bounds(n)}"
   }
   File(context.getExternalFilesDir(null),"$name.txt").writeText(state)
   println("FIXTURE-WINDOW-EVIDENCE $name root=${ui.rootInActiveWindow?.packageName}")
  }.onFailure { println("FIXTURE-EVIDENCE-ERROR window ${it.javaClass.name}") }
 }
 private fun settingsPermissionToggle(): AccessibilityNodeInfo? {
  val root=ui.rootInActiveWindow ?: return null
  if(root.packageName?.toString()!="com.android.settings")return null
  val tree=nodes(root)
  fun visibleText(n: AccessibilityNodeInfo, text: String)=n.packageName?.toString()=="com.android.settings" && n.isVisibleToUser && n.text?.toString()==text
  if(tree.count { visibleText(it,fixtureLabel) }!=1 || tree.count { visibleText(it,"Install unknown apps") }!=1 ||
     tree.count { visibleText(it,"Allow from this source") }!=1)return null
  fun descendantTitleCount(n: AccessibilityNodeInfo): Int {
   var count=if(visibleText(n,"Allow from this source"))1 else 0
   for(i in 0 until n.childCount)n.getChild(i)?.let { count+=descendantTitleCount(it) }
   return count
  }
  // Android14 Spa SwitchPreference puts Role.Switch/toggleable semantics and
  // ACTION_CLICK on the entire labeled row. The visual switch has no own click
  // handler or stable resource ID. Require that exact labeled checkable control.
  val matches=tree.filter { it.packageName?.toString()=="com.android.settings" && it.isVisibleToUser && it.isEnabled &&
   it.isClickable && it.isCheckable && !bounds(it).isEmpty && descendantTitleCount(it)==1 }
  return matches.singleOrNull()
 }
 private suspend fun awaitPermission() {
  try {
   // The durable handoff flag precedes asynchronous system activity/window
   // rendering. Share one original45s deadline across both readiness checks.
   withTimeout(45000) {
    until("real Settings launch recorded") { UpdateDownloads.snapshot()?.let { it.phase=="permission" && it.permissionHandoffLaunched }==true }
    until("exact Settings permission window ready") {
     settingsPermissionToggle()!=null
    }
   }
   assertFalse(context.packageManager.canRequestPackageInstalls());assertSettings();shot("updater-real-settings")
  } catch(error: Throwable) {
   recordPermissionWindowEvidence("updater-permission-window-failure")
   throw error
  }
 }
 private suspend fun grantAndReturn() {
  assertSettings()
  val toggle=requireNotNull(settingsPermissionToggle()) { "Missing or ambiguous labeled Settings permission toggle" }
  val before=bounds(toggle)
  assertTrue(toggle.isCheckable);assertFalse(toggle.isChecked)
  assertTrue("Retained Settings toggle changed before click",toggle.refresh());assertEquals(before,bounds(toggle))
  assertEquals("com.android.settings",ui.rootInActiveWindow?.packageName?.toString())
  assertTrue(toggle.packageName?.toString()=="com.android.settings" && toggle.isVisibleToUser && toggle.isEnabled && toggle.isClickable && toggle.isCheckable)
  assertEquals("Exact labeled Settings toggle changed before click",toggle,settingsPermissionToggle());assertFalse(toggle.isChecked)
  assertTrue("System rejected exact retained Settings toggle action",toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK))
  until("real package-install permission granted") { context.packageManager.canRequestPackageInstalls() && settingsPermissionToggle()?.isChecked==true }
  shot("updater-real-settings-granted");shell("input keyevent KEYCODE_BACK")
  until("real PackageInstaller pending user action") { UpdateDownloads.snapshot()?.phase=="confirmation" }
  try {
   until("exact Android14 update confirmation controls ready") { installerButtons()!=null }
  } catch(error: Throwable) {
   recordPermissionWindowEvidence("updater-installer-readiness-failure")
   throw error
  }
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
 @Test fun deltaCompletionPermissionGrantThenRealInstall()=runBlocking<Unit> {
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
