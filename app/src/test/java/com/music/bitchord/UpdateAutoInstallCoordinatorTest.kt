package com.music.bitchord

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.music.bitchord.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Executes actual coordinator + installer policy branches with a controlled
 * verifier and external handoff. Does not claim Android installer UI proof. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class UpdateAutoInstallCoordinatorTest {
    private lateinit var context: Context
    private lateinit var activity: Activity
    private var granted=true
    private var opens=0
    private var stages=0
    private var verifications=0
    private var verify: suspend () -> Unit = {}
    private var openPermission: () -> Unit = {}
    private var beforePermissionPersist: suspend () -> Unit = {}
    private val info=AppUpdateChecker.UpdateInfo("99.0.0","https://github.com/JoyalGeorge2006/spool/releases/tag/v99.0.0","https://github.com/JoyalGeorge2006/spool/releases/download/v99.0.0/a.apk",null,"a".repeat(64),10000L)
    @Before fun setup()=runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context=ApplicationProvider.getApplicationContext()
        UpdateDownloads.monitorEnabled=false;UpdateDownloads.resetMemoryForTest(context);UpdateStore.clear(context)
        UpdateAutoInstall.initialize(context)
        activity=Robolectric.buildActivity(Activity::class.java).create().get()
        UpdateInstaller.testBackend=object:UpdateInstaller.TestBackend {
            override suspend fun verify(context: Context,attempt: UpdateAttempt) { verifications++;verify() }
            override fun canInstall(context: Context)=granted
            override fun openPermission(context: Context) { opens++;openPermission() }
            override suspend fun permissionLaunchedBeforePersist() { beforePermissionPersist() }
            override suspend fun stage(context: Context,attempt: UpdateAttempt) { stages++;checkNotNull(UpdateDownloads.change(attempt,"installing")) }
        }
    }
    @After fun cleanup()=runBlocking {
        UpdateAutoInstall.onActivityPaused(activity)
        withTimeout(5000) { UpdateAutoInstall.awaitRequestsForTest() }
        UpdateInstaller.testBackend=null
        UpdateDownloads.cancelAndAwait();UpdateStore.clear(context);UpdateDownloads.resetMemoryForTest(context)
        UpdateDownloads.monitorEnabled=true;Dispatchers.resetMain()
    }
    private suspend fun seed(phase: String="ready",pending: Boolean=true): UpdateAttempt {
        val a=UpdateAttempt(UUID.randomUUID().toString(),info,phase,autoInstallPending=pending)
        UpdateStore.write(context,a);UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.restore(context);return requireNotNull(UpdateDownloads.snapshot())
    }
    private suspend fun settled()=withTimeout(5000) { UpdateAutoInstall.awaitRequestsForTest() }
    @Test fun readyResumedHandsOffOnceAcrossDuplicateRequests()=runBlocking {
        seed();UpdateAutoInstall.onActivityResumed(activity)
        repeat(8) { UpdateAutoInstall.request() };settled()
        assertEquals(1,stages);assertEquals("installing",UpdateDownloads.snapshot()!!.phase)
        assertFalse(UpdateStore.read(context)!!.autoInstallPending)
    }
    @Test fun backgroundCompletionWaitsForResumeThenHandsOffOnce()=runBlocking {
        seed();UpdateAutoInstall.request();settled();assertEquals(0,stages)
        UpdateAutoInstall.onActivityResumed(activity);settled();assertEquals(1,stages)
    }
    @Test fun pauseRotationAndFastResumeDuringVerificationEventuallyHandOffOnce()=runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        verify={ if(verifications==1) { entered.complete(Unit);release.await() } }
        seed();UpdateAutoInstall.onActivityResumed(activity);withTimeout(5000){entered.await()}
        val old=activity;UpdateAutoInstall.onActivityPaused(old)
        activity=Robolectric.buildActivity(Activity::class.java).create().get()
        UpdateAutoInstall.onActivityResumed(activity);release.complete(Unit);settled()
        assertEquals(1,stages);assertEquals("installing",UpdateDownloads.snapshot()!!.phase)
    }
    @Test fun settingsGrantReturnContinuesWithoutSecondAppTap()=runBlocking {
        granted=false;openPermission={UpdateAutoInstall.onActivityPaused(activity)}
        seed();UpdateAutoInstall.onActivityResumed(activity);settled()
        assertEquals(1,opens);assertTrue(UpdateDownloads.snapshot()!!.permissionHandoffLaunched);assertEquals(0,stages)
        granted=true;UpdateAutoInstall.onActivityResumed(activity);settled()
        assertEquals(1,opens);assertEquals(1,stages)
    }
    @Test fun settingsDenialClearsContinuationAndNeverReopens()=runBlocking {
        granted=false;openPermission={UpdateAutoInstall.onActivityPaused(activity)}
        seed();UpdateAutoInstall.onActivityResumed(activity);settled()
        UpdateAutoInstall.onActivityResumed(activity);settled()
        repeat(3){UpdateAutoInstall.onActivityResumed(activity)};settled()
        assertEquals(1,opens);assertEquals(0,stages);assertFalse(UpdateDownloads.snapshot()!!.autoInstallPending)
    }
    @Test fun sameResumeCannotBeMistakenForPermissionDenial()=runBlocking {
        granted=false;seed();UpdateAutoInstall.onActivityResumed(activity);settled()
        repeat(3){UpdateAutoInstall.onActivityResumed(activity)};settled()
        assertEquals(1,opens);assertTrue(UpdateDownloads.snapshot()!!.autoInstallPending)
        UpdateAutoInstall.onActivityPaused(activity);UpdateAutoInstall.onActivityResumed(activity);settled()
        assertFalse(UpdateDownloads.snapshot()!!.autoInstallPending);assertEquals(1,opens)
    }
    @Test fun fastGrantedReturnBeforeLaunchedRecordWriteIsReevaluated()=runBlocking {
        val launched=CompletableDeferred<Unit>();val persist=CompletableDeferred<Unit>()
        granted=false;openPermission={UpdateAutoInstall.onActivityPaused(activity)}
        beforePermissionPersist={launched.complete(Unit);persist.await()}
        seed();UpdateAutoInstall.onActivityResumed(activity);withTimeout(5000){launched.await()}
        granted=true;UpdateAutoInstall.onActivityResumed(activity)
        persist.complete(Unit);settled()
        assertEquals(1,opens);assertEquals(1,stages)
    }
    @Test fun foregroundLossBeforeGuardedLaunchRearmsReady()=runBlocking {
        verify={UpdateAutoInstall.onActivityPaused(activity)}
        seed();UpdateAutoInstall.onActivityResumed(activity);settled()
        assertEquals("ready",UpdateDownloads.snapshot()!!.phase);assertTrue(UpdateDownloads.snapshot()!!.autoInstallPending)
        assertEquals(0,opens);assertEquals(0,stages)
        verify={};UpdateAutoInstall.onActivityResumed(activity);settled();assertEquals(1,stages)
    }
    @Test fun actualManualEntryConsumesPendingThenCancelledReadyDoesNotLoop()=runBlocking {
        seed();UpdateInstaller.install(context)
        assertEquals(1,stages);val a=UpdateDownloads.snapshot()!!
        assertFalse(a.autoInstallPending);UpdateDownloads.change(a,"ready","User cancelled installer")
        UpdateAutoInstall.onActivityResumed(activity);settled();assertEquals(1,stages)
    }
    @Test fun staleVerificationFailureCannotFailNewDownload()=runBlocking {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        verify={entered.complete(Unit);release.await();error("old verifier failure")}
        seed();UpdateAutoInstall.onActivityResumed(activity);withTimeout(5000){entered.await()}
        val newer=seed("queued");release.complete(Unit);settled()
        assertEquals(newer,UpdateDownloads.snapshot());assertEquals(0,stages)
    }
    @Test fun unlaunchedColdPermissionRecordFailsClosedWithoutOpeningSettings()=runBlocking {
        seed("permission");UpdateAutoInstall.onActivityResumed(activity);settled()
        assertEquals(0,opens);assertEquals(0,stages);assertFalse(UpdateDownloads.snapshot()!!.autoInstallPending)
    }
    @Test fun legacyReadyNeverAutoPromptsEvenWhenResumed()=runBlocking {
        seed(pending=false);UpdateAutoInstall.onActivityResumed(activity);settled()
        assertEquals(0,verifications);assertEquals(0,opens);assertEquals(0,stages)
    }
}
