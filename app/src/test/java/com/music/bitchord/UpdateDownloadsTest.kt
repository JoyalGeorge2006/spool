package com.music.bitchord

import android.app.DownloadManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.music.bitchord.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDownloadManager
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class UpdateDownloadsTest {
 private lateinit var context:Context
 private val update=AppUpdateChecker.UpdateInfo("99.0.0","https://github.com/JoyalGeorge2006/spool/releases/tag/v99.0.0","https://github.com/JoyalGeorge2006/spool/releases/download/v99.0.0/spool-v99.0.0_arm64-v8a.apk",null,"a".repeat(64),10000L)
 private fun dm()=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
 private fun shadow():ShadowDownloadManager=Shadow.extract(dm())
 private fun request():ShadowDownloadManager.ShadowRequest=Shadow.extract(shadow().getRequest(UpdateDownloads.snapshot()!!.id))
 @Before fun setup()=runBlocking {
  context=ApplicationProvider.getApplicationContext();UpdateDownloads.monitorEnabled=false
  UpdateDownloads.resetMemoryForTest(context);UpdateStore.clear(context)
  context.getSharedPreferences("spool-update-transfer",Context.MODE_PRIVATE).edit().clear().commit()
  AppUpdateChecker.transferIdle()
 }
 @After fun cleanup()=runBlocking { UpdateDownloads.cancelAndAwait();UpdateStore.clear(context);UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.monitorEnabled=true }
 @Test fun downloadCompletionReceiverAcceptsOnlyPrivilegedSender() {
  val info=context.packageManager.getReceiverInfo(android.content.ComponentName(context,UpdateDownloadReceiver::class.java),0)
  assertTrue(info.exported)
  assertEquals("android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS",info.permission)
 }
 @Test fun installResultReceiverRemainsPrivate() {
  val info=context.packageManager.getReceiverInfo(android.content.ComponentName(context,UpdateInstallReceiver::class.java),0)
  assertFalse(info.exported)
 }
 @Test fun systemTransferPermitsWifiAndMeteredMobileAndPersists()=runBlocking {
  UpdateDownloads.start(context,update);val id=UpdateDownloads.snapshot()!!.id;assertTrue(id>=0)
  val request=request()
  assertEquals(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE,request.allowedNetworkTypes)
  assertTrue(request.allowedOverMetered)
  assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,request.notificationVisibility)
  assertTrue(request.destination.path!!.endsWith("/download.bin"))
  request.status=DownloadManager.STATUS_RUNNING;request.bytesSoFar=5000L
  UpdateDownloads.refresh(context)
  assertEquals(.5f,(AppUpdateChecker.download.value as AppUpdateChecker.DownloadState.Downloading).fraction)
  UpdateDownloads.resetMemoryForTest(context);AppUpdateChecker.transferIdle();UpdateDownloads.refresh(context)
  assertEquals(id,UpdateDownloads.snapshot()!!.id)
  assertEquals(.5f,(AppUpdateChecker.download.value as AppUpdateChecker.DownloadState.Downloading).fraction)
  UpdateDownloads.cancelAndAwait();assertEquals(0,shadow().requestCount)
  assertEquals("cancelled",UpdateStore.read(context)!!.phase)
 }
 @Test fun repeatedStartDoesNotDuplicateTransfer()=runBlocking {
  UpdateDownloads.start(context,update);val id=UpdateDownloads.snapshot()!!.id
  UpdateDownloads.start(context,update);assertEquals(id,UpdateDownloads.snapshot()!!.id);assertEquals(1,shadow().requestCount)
 }
 @Test fun failedTransferCanRetryAndReasonSurvivesColdRestore()=runBlocking {
  UpdateDownloads.start(context,update);request().status=DownloadManager.STATUS_FAILED
  UpdateDownloads.refresh(context);assertTrue(AppUpdateChecker.download.value is AppUpdateChecker.DownloadState.Failed)
  UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.refresh(context)
  assertTrue(AppUpdateChecker.download.value is AppUpdateChecker.DownloadState.Failed)
  UpdateDownloads.start(context,update);assertEquals("queued",UpdateDownloads.snapshot()!!.phase);assertEquals(1,shadow().requestCount)
 }
 @Test fun corruptCompletedTransferNeverBecomesReady()=runBlocking {
  UpdateDownloads.start(context,update);val req=request()
  File(req.destination.path!!).apply { parentFile?.mkdirs();writeBytes(ByteArray(10000)) };req.status=DownloadManager.STATUS_SUCCESSFUL
  UpdateDownloads.refresh(context)
  assertTrue(AppUpdateChecker.download.value is AppUpdateChecker.DownloadState.Failed)
  assertFalse(UpdateDownloads.snapshot()!!.candidate(context).exists())
 }
 @Test fun pendingAndPausedAreWaitingNotZeroPercentRunning()=runBlocking {
  UpdateDownloads.start(context,update);request().status=DownloadManager.STATUS_PENDING;UpdateDownloads.refresh(context)
  assertTrue(AppUpdateChecker.download.value is AppUpdateChecker.DownloadState.Waiting)
  request().status=DownloadManager.STATUS_PAUSED;UpdateDownloads.refresh(context)
  assertTrue(AppUpdateChecker.download.value is AppUpdateChecker.DownloadState.Waiting)
 }
 @Test fun missingSystemRowIsDurableFailureNotStuckBusy()=runBlocking {
  UpdateDownloads.start(context,update);dm().remove(UpdateDownloads.snapshot()!!.id);UpdateDownloads.refresh(context)
  assertEquals("failed",UpdateStore.read(context)!!.phase)
  UpdateDownloads.start(context,update);assertTrue(UpdateDownloads.snapshot()!!.id>=0)
 }
 @Test fun staleTransitionCannotAdoptOutputAfterStop()=runBlocking {
  UpdateDownloads.start(context,update);val old=UpdateDownloads.snapshot()!!;UpdateDownloads.cancelAndAwait()
  assertNull(UpdateDownloads.change(old,"ready"));assertEquals("cancelled",UpdateStore.read(context)!!.phase)
 }
 @Test fun recoverEnqueuePersistGapAdoptsOwnedRequestInsteadOfDuplicating()=runBlocking {
  UpdateDownloads.start(context,update);val old=UpdateDownloads.snapshot()!!;request().status=DownloadManager.STATUS_PENDING
  UpdateStore.write(context,old.copy(id=-1,phase="preparing"));UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.refresh(context)
  assertEquals(old.id,UpdateDownloads.snapshot()!!.id);assertEquals("queued",UpdateDownloads.snapshot()!!.phase);assertEquals(1,shadow().requestCount)
 }
 @Test fun stoppedAttemptCannotCommitInstaller()=runBlocking {
  UpdateDownloads.start(context,update);val a=UpdateDownloads.snapshot()!!;val confirmation=UpdateDownloads.change(a,"installing")!!
  UpdateDownloads.cancelAndAwait();var committed=false
  assertFalse(UpdateDownloads.commitIfCurrent(confirmation) { committed=true });assertFalse(committed)
 }
 @Test fun versionLabelAloneNeverProvesInstallCompletion()=runBlocking {
  UpdateDownloads.start(context,update.copy(version=BuildConfig.VERSION_NAME))
  UpdateDownloads.refresh(context)
  assertEquals("failed",UpdateStore.read(context)!!.phase)
  assertTrue(AppUpdateChecker.download.value is AppUpdateChecker.DownloadState.Failed)
 }
 @Test fun retryRetiresOnlyItsPreviousPrivateFiles()=runBlocking {
  UpdateDownloads.start(context,update);val old=UpdateDownloads.snapshot()!!
  old.directory(context).mkdirs();old.candidate(context).writeText("old output")
  val unrelated=File(context.noBackupFilesDir,"keep-user-data").apply { writeText("kept") }
  UpdateDownloads.change(old,"failed","fixture")
  UpdateDownloads.start(context,update)
  assertFalse(old.candidate(context).exists());assertTrue(unrelated.exists());assertEquals(1,shadow().requestCount)
  unrelated.delete();Unit
 }

 @Test fun onlyCurrentStagedSessionCanCommit()=runBlocking {
  UpdateDownloads.start(context,update);val a=UpdateDownloads.snapshot()!!
  var commits=0;assertFalse(UpdateDownloads.commitIfCurrent(a) { commits++ })
  val staged=UpdateDownloads.change(a,"installing")!!
  assertTrue(UpdateDownloads.commitIfCurrent(staged) { commits++ });assertEquals(1,commits)
 }

}
