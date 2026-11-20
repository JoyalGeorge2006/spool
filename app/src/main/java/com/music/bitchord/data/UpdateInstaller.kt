package com.music.bitchord.data

import android.app.*
import android.content.*
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.music.bitchord.R
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

/** Only an explicit Install command enters this owner. Ordinary Android confirmation remains mandatory. */
internal object UpdateInstaller {
    private val mutex=Mutex()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var foreground=WeakReference<Activity>(null)
    @Volatile private var committedOperation:Pair<String,Long>?=null
    @Volatile private var pendingConfirmation: Pair<String,Intent>?=null
    suspend fun continueConfirmation(context: Context) = withContext(Dispatchers.IO) {
        if(!mutex.tryLock())return@withContext
        try {
            val a=UpdateDownloads.snapshot() ?: return@withContext
            if(a.phase !in setOf("confirmation","installing"))return@withContext
            // A newly committed session has not delivered its confirmation callback yet.
            // An eager second tap must not abandon it. After process loss or a bounded
            // wait, the explicit action may safely reset only this owned session.
            if(a.phase=="installing" && committedOperation?.let { it.first==a.installToken && android.os.SystemClock.elapsedRealtime()-it.second<30000L }==true)return@withContext
            val intent=pendingConfirmation?.takeIf { it.first==a.installToken }?.second
            if(intent!=null) {
                try { withContext(Dispatchers.Main) { context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                catch(_: Exception) {
                    pendingConfirmation=null
                    if(a.session>=0)runCatching { context.packageManager.packageInstaller.abandonSession(a.session) }
                    UpdateDownloads.change(a,"ready","Android confirmation could not open. Tap Install to try again.",session=-1,installToken="")
                }
            } else {
                // Intent blobs are not persisted. Explicit recovery abandons only our own old session.
                if(a.session>=0)runCatching { context.packageManager.packageInstaller.abandonSession(a.session) }
                UpdateDownloads.change(a,"ready","Android confirmation was interrupted. Tap Install to request a fresh confirmation.",session=-1,installToken="")
            }
        } finally { mutex.unlock() }
    }
    suspend fun install(context: Context)=withContext(Dispatchers.IO) {
        if(AppUpdateChecker.externallyProvided || !mutex.tryLock())return@withContext
        try {
            var a=UpdateDownloads.snapshot() ?: return@withContext
            if(a.phase !in setOf("ready","permission"))return@withContext
            AppUpdateChecker.verifyDownloadedApk(context,a.candidate(context),a.info,a.delta?.targetCode,a.delta?.minSdk,a.delta?.abi)
            if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
                a=UpdateDownloads.change(a,"permission","Allow updates from Spool in Android settings, then return and tap Install.") ?: return@withContext
                withContext(Dispatchers.Main) { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return@withContext
            }
            foreground=WeakReference(findActivity(context))
            val pi=context.packageManager.packageInstaller
            val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName);setSize(requireNotNull(a.info.sizeBytes))
                if(Build.VERSION.SDK_INT>=31)setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            val token=UUID.randomUUID().toString()
            a=UpdateDownloads.change(a,"staging","Preparing Android installer",installToken=token) ?: return@withContext
            val sessionId=pi.createSession(params)
            a=UpdateDownloads.change(a,"staging","Preparing Android installer",session=sessionId) ?: run { pi.abandonSession(sessionId);return@withContext }
            try {
                pi.openSession(sessionId).use { session ->
                    FileInputStream(a.candidate(context)).use { input -> session.openWrite("base.apk",0,requireNotNull(a.info.sizeBytes)).use { out ->
                        val buffer=ByteArray(65536)
                        while(true) {
                            check(UpdateDownloads.snapshot()?.let { it.token==a.token && it.phase=="staging" }==true) { "Installation stopped" }
                            val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)
                        }
                        session.fsync(out)
                    } }
                    // Recheck candidate immediately before giving the session install authority.
                    AppUpdateChecker.verifyDownloadedApk(context,a.candidate(context),a.info,a.delta?.targetCode,a.delta?.minSdk,a.delta?.abi)
                    a=UpdateDownloads.change(a,"installing","Waiting for Android confirmation. If it does not appear, Continue can retry after 30 seconds.") ?: run { session.abandon();return@withContext }
                    val callback=Intent(context,UpdateInstallReceiver::class.java)
                        .setAction("${context.packageName}.UPDATE_INSTALL_RESULT")
                        .setData(Uri.parse("spool-update://install/$token"))
                        .putExtra("attempt",a.token).putExtra("operation",token)
                    val flags=PendingIntent.FLAG_UPDATE_CURRENT or (if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0)
                    committedOperation=token to android.os.SystemClock.elapsedRealtime()
                    if(!UpdateDownloads.commitIfCurrent(a) { session.commit(PendingIntent.getBroadcast(context,sessionId,callback,flags).intentSender) }) session.abandon()
                }
            } catch(e: Exception) {
                runCatching { pi.abandonSession(sessionId) }
                val now=UpdateDownloads.snapshot()
                if(now?.token==a.token && now.phase!="cancelled")UpdateDownloads.change(now,"ready","Android installer could not finish. Tap Install to retry.",session=-1,installToken="")
            }
        } catch(e: Exception) {
            UpdateDownloads.snapshot()?.let { if(it.phase in setOf("ready","permission"))UpdateDownloads.change(it,"failed",e.message ?: "Update could not be verified") }
        } finally { mutex.unlock() }
    }
    private fun findActivity(context: Context): Activity? = when(context) {
        is Activity -> context
        is android.content.ContextWrapper -> if(context.baseContext!==context)findActivity(context.baseContext) else null
        else -> null
    }
    suspend fun reconcile(c: Context,a: UpdateAttempt) {
        if(mutex.isLocked)return
        val session=if(a.session>=0)c.packageManager.packageInstaller.getSessionInfo(a.session) else null
        if(session==null || a.phase=="staging") {
            // A staging crash is not a commit. A lost callback is not evidence of install success.
            if(session!=null)runCatching { c.packageManager.packageInstaller.abandonSession(a.session) }
            if(a.candidate(c).isFile)UpdateDownloads.change(a,"ready","Installer interrupted. Tap Install to try again.",session=-1,installToken="")
            else UpdateDownloads.change(a,"failed","Prepared update is missing. Retry download.",session=-1,installToken="")
        }
    }
    fun result(context: Context,intent: Intent,finished: () -> Unit) {
        scope.launch {
            try {
                var a=UpdateDownloads.snapshot() ?: run { UpdateDownloads.refresh(context);UpdateDownloads.snapshot() } ?: return@launch
                if(a.token!=intent.getStringExtra("attempt") || a.installToken!=intent.getStringExtra("operation") ||
                    intent.data?.lastPathSegment!=a.installToken || a.session!=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1) ||
                    a.phase !in setOf("confirmation","installing"))return@launch
                when(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirmation=intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return@launch
                        a=UpdateDownloads.change(a,"confirmation","Confirm installation in Android. If the prompt is hidden, open the update notification.") ?: return@launch
                        pendingConfirmation=a.installToken to confirmation
                        val activity=foreground.get()
                        if(activity!=null && !activity.isFinishing && activity.hasWindowFocus()) {
                            withContext(Dispatchers.Main) { activity.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        } else {
                            UpdateNavigation.show(context,"Spool update is ready","Tap to review and continue Android confirmation")
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        pendingConfirmation=null
                        // Only observe installed version as success, never infer it from an intent alone.
                        if(AppUpdateChecker.isInstalledTarget(context,a.info,a.delta?.targetCode)) {
                            UpdateDownloads.change(a,"installed","Update installed")
                            UpdateNavigation.clear(context)
                        } else UpdateDownloads.change(a,"ready","Android finished. Reopen Spool to confirm the installed APK.",session=-1,installToken="")
                    }
                    else -> {
                        pendingConfirmation=null
                        UpdateDownloads.change(a,"ready","Installation was cancelled or failed. Your verified download is kept. Tap Install to retry.",session=-1,installToken="")
                        UpdateNavigation.clear(context)
                    }
                }
            } finally { finished() }
        }
    }
}
class UpdateInstallReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val pending=goAsync();UpdateInstaller.result(context.applicationContext,intent) { pending.finish() }
    }
}
