package com.music.bitchord

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.music.bitchord.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** State-owner tests, not evidence of a real Android installer confirmation. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class UpdateAutoInstallTest {
    private lateinit var context: Context
    private val info=AppUpdateChecker.UpdateInfo("99.0.0","https://github.com/JoyalGeorge2006/spool/releases/tag/v99.0.0","https://github.com/JoyalGeorge2006/spool/releases/download/v99.0.0/a.apk",null,"a".repeat(64),10000L)
    @Before fun setup()=runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context=ApplicationProvider.getApplicationContext()
        UpdateAutoInstall.initialize(context)
        UpdateDownloads.monitorEnabled=false
        UpdateDownloads.resetMemoryForTest(context);UpdateStore.clear(context)
        context.getSharedPreferences("spool-update-transfer",Context.MODE_PRIVATE).edit().clear().commit()
        AppUpdateChecker.transferIdle()
    }
    @After fun cleanup()=runBlocking {
        try {
            withTimeout(5000) { UpdateAutoInstall.awaitRequestsForTest() }
            UpdateDownloads.cancelAndAwait();UpdateStore.clear(context)
            UpdateDownloads.resetMemoryForTest(context)
        } finally { UpdateDownloads.monitorEnabled=true;Dispatchers.resetMain() }
    }
    private suspend fun attempt(phase: String="ready",pending: Boolean=true): UpdateAttempt {
        val a=UpdateAttempt(UUID.randomUUID().toString(),info,phase,autoInstallPending=pending)
        UpdateStore.write(context,a);UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.restore(context)
        return requireNotNull(UpdateDownloads.snapshot())
    }
    @Test fun savedNotesNormalizationIsExplicitNotAnAttemptIdentityChange()=runBlocking {
        val original=UpdateAttempt(UUID.randomUUID().toString(),info,"ready",autoInstallPending=true)
        UpdateStore.write(context,original)
        val restored=requireNotNull(UpdateStore.read(context))
        assertNull(original.info.notes);assertEquals("",restored.info.notes)
        assertEquals(original.token,restored.token)
        assertEquals(original.copy(info=original.info.copy(notes="")),restored)
        val longNotes="n".repeat(9000)
        UpdateStore.write(context,original.copy(info=info.copy(notes=longNotes)))
        val bounded=requireNotNull(UpdateStore.read(context))
        assertEquals(longNotes.take(8192),bounded.info.notes)
        assertEquals(original.token,bounded.token)
    }
    @Test fun newDownloadTapPersistsAutomaticContinuation()=runBlocking {
        UpdateDownloads.start(context,info)
        assertTrue(UpdateDownloads.snapshot()!!.autoInstallPending)
        assertTrue(UpdateStore.read(context)!!.autoInstallPending)
    }
    @Test fun legacyReadyWithoutNewFieldNeverGainsAutomaticConsent()=runBlocking {
        val a=attempt(pending=false)
        val f=File(context.noBackupFilesDir,"spool-update-v1.json")
        val j=org.json.JSONObject(f.readText());j.remove("autoInstallPending");f.writeText(j.toString())
        UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.restore(context)
        assertFalse(UpdateDownloads.snapshot()!!.autoInstallPending)
        assertNull(UpdateDownloads.claimAutoInstall(UpdateDownloads.snapshot()!!))
        assertEquals(a.token,UpdateDownloads.snapshot()!!.token)
    }
    @Test fun pendingContinuationSurvivesColdRestore()=runBlocking {
        val a=attempt();UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.restore(context)
        assertEquals(a,UpdateDownloads.snapshot())
    }
    @Test fun claimIsPersistedBeforeAnyInstallerCanRun()=runBlocking {
        val a=attempt();val claimed=UpdateDownloads.claimAutoInstall(a)!!
        assertFalse(claimed.autoInstallPending)
        assertEquals(claimed,UpdateStore.read(context))
        assertNull(UpdateDownloads.claimAutoInstall(a))
        assertNull(UpdateDownloads.claimAutoInstall(claimed))
    }
    @Test fun concurrentResumeClaimsExactlyOnce()=runBlocking {
        val a=attempt()
        val results=List(8) { async(Dispatchers.Default) { UpdateDownloads.claimAutoInstall(a) } }.awaitAll()
        assertEquals(1,results.count { it!=null })
        assertFalse(UpdateStore.read(context)!!.autoInstallPending)
    }
    @Test fun processLossAfterClaimCannotRepeatAutomaticHandoff()=runBlocking {
        val a=attempt();UpdateDownloads.claimAutoInstall(a)
        UpdateDownloads.resetMemoryForTest(context);UpdateDownloads.restore(context)
        assertNull(UpdateDownloads.claimAutoInstall(UpdateDownloads.snapshot()!!))
    }
    @Test fun staleAttemptCannotClaimNewAttempt()=runBlocking {
        val old=attempt();val current=attempt()
        assertNull(UpdateDownloads.claimAutoInstall(old));assertEquals(current,UpdateDownloads.snapshot())
    }
    @Test fun onlyReadyOrPermissionMayClaimNeverUnverifiedOrTerminal()=runBlocking {
        for(phase in listOf("preparing","queued","downloading","waiting","verifying","applying","staging","confirmation","installing","failed","fallback","cancelled","installed")) {
            val a=attempt(phase)
            assertNull("phase=$phase",UpdateDownloads.claimAutoInstall(a))
        }
    }
    @Test fun permissionReturnContinuationCanBeConsumedOnce()=runBlocking {
        val a=attempt("permission");assertNotNull(UpdateDownloads.claimAutoInstall(a))
        assertNull(UpdateDownloads.claimAutoInstall(UpdateDownloads.snapshot()!!))
    }
    @Test fun manualInstallClaimAlsoConsumesPendingIntent()=runBlocking {
        val a=attempt();val claimed=UpdateDownloads.claimAutoInstall(a,automatic=false)!!
        assertFalse(claimed.autoInstallPending)
        val cancelled=UpdateDownloads.change(claimed,"ready","Installer cancelled")!!
        assertFalse(cancelled.autoInstallPending);assertNull(UpdateDownloads.claimAutoInstall(cancelled))
    }
    @Test fun everyRecoveryReadyClearsAutomaticIntentByDefault()=runBlocking {
        for(phase in listOf("ready","permission","failed","fallback","cancelled","installed")) {
            val a=attempt("staging")
            val recovered=UpdateDownloads.change(a,phase)!!
            assertFalse("phase=$phase",recovered.autoInstallPending)
        }
    }
    @Test fun installerCancellationReadyDoesNotRearmConsumedContinuation()=runBlocking {
        val a=attempt();val claimed=UpdateDownloads.claimAutoInstall(a)!!
        val installing=UpdateDownloads.change(claimed,"installing")!!
        val ready=UpdateDownloads.change(installing,"ready","Cancelled by user")!!
        assertFalse(ready.autoInstallPending);assertNull(UpdateDownloads.claimAutoInstall(ready))
    }
    @Test fun stopRetainsNoClaimableContinuation()=runBlocking {
        val a=attempt();UpdateDownloads.cancelAndAwait()
        assertNull(UpdateDownloads.claimAutoInstall(a))
        assertNull(UpdateDownloads.claimAutoInstall(UpdateDownloads.snapshot()!!))
    }
    @Test fun backgroundReadyRequestNeverClaimsWithoutResumedActivity()=runBlocking {
        val a=attempt();UpdateAutoInstall.request()
        withTimeout(5000) { UpdateAutoInstall.awaitRequestsForTest() }
        assertEquals(a,UpdateDownloads.snapshot())
        assertTrue(UpdateStore.read(context)!!.autoInstallPending)
    }
}
