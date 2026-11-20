package com.music.bitchord

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.music.bitchord.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** Regression: a durable older transfer must never emit even a transient popup offer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class UpdateOfferRegressionTest {
 private lateinit var context:Context
 private fun info(version:String)=AppUpdateChecker.UpdateInfo(version,
  "https://github.com/JoyalGeorge2006/spool/releases/tag/v$version",
  "https://github.com/JoyalGeorge2006/spool/releases/download/v$version/spool.apk",null,"a".repeat(64),10000L)
 @Before fun setup()=runBlocking {
  context=ApplicationProvider.getApplicationContext();UpdateDownloads.monitorEnabled=false
  UpdateDownloads.resetMemoryForTest(context);UpdateStore.clear(context)
  context.getSharedPreferences("spool-update-transfer",Context.MODE_PRIVATE).edit().clear().commit()
  AppUpdateChecker.transferIdle()
 }
 @After fun cleanup()=runBlocking {
  UpdateStore.clear(context);UpdateDownloads.resetMemoryForTest(context)
  AppUpdateChecker.transferIdle();UpdateDownloads.monitorEnabled=true
 }
 @Test fun stableOlderThanInstalledPreviewIsNotAnUpdate() {
  assertNull(AppUpdateChecker.newerOffer(info("2.0.0"),"2.1.0"))
  assertNull(AppUpdateChecker.newerOffer(info("2.0.0"),"2.1.1"))
  assertNull(AppUpdateChecker.newerOffer(info("2.1.1"),"2.1.1"))
  assertNotNull(AppUpdateChecker.newerOffer(info("2.1.2"),"2.1.1"))
  assertNotNull(AppUpdateChecker.newerOffer(info("2.1.1"),"2.1.1-beta1"))
  assertNull(AppUpdateChecker.newerOffer(info("2.1.1-beta1"),"2.1.1"))
  assertNotNull(AppUpdateChecker.newerOffer(info("2.10.0"),"2.9.0"))
 }
 @Test fun everyRestoredTransferPhaseRejectsOlderOfferBeforeRefresh()=runBlocking {
  val emissions=mutableListOf<String?>()
  val collector=launch(start=CoroutineStart.UNDISPATCHED) { AppUpdateChecker.available.collect { emissions+=it?.version } }
  try {
   for(phase in listOf("queued","waiting","ready","failed","cancelled","installed","confirmation","installing")) {
    UpdateStore.write(context,UpdateAttempt(UUID.randomUUID().toString(),info("2.0.0"),phase))
    UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.restore(context);yield()
    assertNull("old saved $phase exposed before any refresh",AppUpdateChecker.available.value)
   }
   assertTrue("no false non-null emission, not merely cleared later: $emissions",emissions.all { it==null })
  } finally {collector.cancelAndJoin()}
 }
 @Test fun newerSavedTransferRemainsAvailableAndSameTargetDoesNot()=runBlocking {
  UpdateStore.write(context,UpdateAttempt(UUID.randomUUID().toString(),info("99.0.0"),"queued"))
  UpdateDownloads.restore(context);assertEquals("99.0.0",AppUpdateChecker.available.value?.version)
  AppUpdateChecker.transferState(info(BuildConfig.VERSION_NAME),AppUpdateChecker.DownloadState.Ready(File(context.cacheDir,"unused.apk")))
  assertNull(AppUpdateChecker.available.value)
 }
 @Test fun oldProgressReadyAndTerminalCannotReintroduceFalseOffer() {
  val old=info("2.0.0")
  AppUpdateChecker.transferProgress(old,.5f);assertNull(AppUpdateChecker.available.value)
  AppUpdateChecker.transferReady(old,File(context.cacheDir,"unused.apk"));assertNull(AppUpdateChecker.available.value)
  AppUpdateChecker.transferState(old,AppUpdateChecker.DownloadState.Idle,pinned=false);assertNull(AppUpdateChecker.available.value)
 }
 @Test fun idleClearsOfferButDoesNotDisableRealNewerUpdates() {
  val newer=info("99.0.0")
  AppUpdateChecker.transferProgress(newer,.5f);assertEquals(newer,AppUpdateChecker.available.value)
  AppUpdateChecker.transferIdle();assertNull(AppUpdateChecker.available.value)
  AppUpdateChecker.transferReady(newer,File(context.cacheDir,"unused.apk"));assertEquals(newer,AppUpdateChecker.available.value)
 }
}
