package com.music.bitchord.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One durable owner. Long hashing/copying is never inside the state lock. */
internal object UpdateDownloads {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val lock=Mutex()
    private val processing=Mutex()
    private var monitor: Job?=null
    internal var monitorEnabled=true
    internal suspend fun resetMemoryForTest(context: Context) {
        monitor?.cancelAndJoin();monitor=null
        lock.withLock { current=null;loaded=false;app=context.applicationContext }
    }
    private var app: Context?=null
    private var loaded=false
    @Volatile private var generation=0L
    @Volatile private var current: UpdateAttempt?=null
    private val _offer=kotlinx.coroutines.flow.MutableStateFlow<Pair<String,Long>?>(null)
    val offer=_offer.asStateFlow()
    private val _offerChecking=kotlinx.coroutines.flow.MutableStateFlow(false)
    val offerChecking=_offerChecking.asStateFlow()
    suspend fun prepareOffer(c: Context,info: AppUpdateChecker.UpdateInfo) = withContext(Dispatchers.IO) {
        if(current?.phase in setOf("queued","downloading","waiting","verifying","applying","ready","staging","confirmation"))return@withContext
        _offerChecking.value=true
        try {
            val size=if(info.deltas.isEmpty() || !c.applicationInfo.splitSourceDirs.isNullOrEmpty())info.sizeBytes else {
                val base=File(c.applicationInfo.sourceDir)
                val hash=SpoolDelta.sha256(base) { ensureActive() }
                info.deltas.singleOrNull { it.baseSize==base.length() && it.baseSha256.equals(hash,true) }?.size ?: info.sizeBytes
            }
            if(size!=null)_offer.value=info.version to size
        } finally { _offerChecking.value=false }
    }
    private fun manager(c: Context)=c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    internal fun snapshot()=current
    private fun active(a: UpdateAttempt)=current?.token==a.token && current?.phase !in setOf("cancelled","failed","fallback","installed")
    private fun checkpoint(a: UpdateAttempt) { if(!active(a) || Thread.currentThread().isInterrupted) throw IOException("Update attempt stopped") }

    private fun publish(a: UpdateAttempt) {
        val state=when(a.phase) {
            "queued" -> AppUpdateChecker.DownloadState.Waiting("Queued by Android")
            "waiting" -> AppUpdateChecker.DownloadState.Waiting(a.message)
            "preparing" -> AppUpdateChecker.DownloadState.Preparing("Preparing download")
            "applying" -> AppUpdateChecker.DownloadState.Preparing("Preparing update")
            "verifying" -> AppUpdateChecker.DownloadState.Verifying
            "ready" -> AppUpdateChecker.DownloadState.Ready(a.candidate(requireNotNull(app)),a.message)
            "permission" -> AppUpdateChecker.DownloadState.Ready(a.candidate(requireNotNull(app)),a.message)
            "staging","confirmation","installing" -> AppUpdateChecker.DownloadState.Installing(a.message.ifBlank { "Preparing Android installer" })
            "fallback" -> AppUpdateChecker.DownloadState.Fallback(a.message)
            "failed" -> AppUpdateChecker.DownloadState.Failed(a.message)
            "cancelled" -> AppUpdateChecker.DownloadState.Cancelled
            "installed" -> AppUpdateChecker.DownloadState.Idle
            else -> AppUpdateChecker.DownloadState.Downloading(0f,0,a.transferSize)
        }
        AppUpdateChecker.transferState(a.info,state,pinned=a.phase !in setOf("cancelled","failed","installed"))
    }
    internal suspend fun change(a: UpdateAttempt, phase: String, message: String="", session: Int=a.session, installToken: String=a.installToken, autoInstallPending: Boolean=if(phase in setOf("ready","permission","failed","fallback","cancelled","installed")) false else a.autoInstallPending, permissionHandoffLaunched: Boolean=false): UpdateAttempt? {
        val next=lock.withLock {
        val old=current
        if(old!=a)return@withLock null
        val next=old.copy(phase=phase,message=message,session=session,installToken=installToken,autoInstallPending=autoInstallPending,permissionHandoffLaunched=permissionHandoffLaunched)
        UpdateStore.write(requireNotNull(app),next);current=next;publish(next)
        if(phase in setOf("ready","failed","fallback","waiting","applying","verifying"))
            UpdateNavigation.show(requireNotNull(app),when(phase) { "ready" -> "Spool update is ready";"failed","fallback" -> "Spool update needs attention";else -> "Spool update" },when(phase) { "ready" -> if(next.autoInstallPending) "Open Spool to continue Android installation" else "Tap to review and install";"applying" -> "Preparing update";"verifying" -> "Checking update";else -> message })
        next
        }
        if(next?.autoInstallPending==true && (phase=="ready" || (phase=="permission" && next.permissionHandoffLaunched)))UpdateAutoInstall.request()
        return next
    }
    /** Claim the exact user-authorized attempt once, never adopt a later attempt. */
    internal suspend fun claimAutoInstall(a: UpdateAttempt, automatic: Boolean=true): UpdateAttempt? = lock.withLock {
        if(current!=a || (automatic && !a.autoInstallPending) || a.phase !in setOf("ready","permission"))return@withLock null
        val next=a.copy(autoInstallPending=false,permissionHandoffLaunched=false)
        UpdateStore.write(requireNotNull(app),next);current=next;publish(next);next
    }
    internal suspend fun commitIfCurrent(a: UpdateAttempt, commit: () -> Unit): Boolean = lock.withLock {
        if(current!=a || a.phase!="installing")return@withLock false
        commit();true
    }
    private suspend fun load(c: Context) = lock.withLock {
        app=c.applicationContext
        if(loaded)return@withLock
        try {
            current=UpdateStore.read(c)
            // Migration preserves the existing system request, but never trusts a legacy Ready flag.
            if(current==null) {
                val p=c.getSharedPreferences("spool-update-transfer",Context.MODE_PRIVATE)
                if(p.getLong("id",-1)>=0) {
                    val i=AppUpdateChecker.UpdateInfo(requireNotNull(p.getString("version",null)),requireNotNull(p.getString("release",null)),p.getString("apk",null),p.getString("notes",null),p.getString("sha",null),p.getLong("size",0))
                    require(UpdateSources.asset(requireNotNull(i.apkUrl)) && UpdateSources.release(i.releaseUrl) && UpdateSources.hash(i.sha256) && requireNotNull(i.sizeBytes) in 1..SpoolDelta.MAX_APK)
                    val a=UpdateAttempt(UUID.randomUUID().toString(),i,"queued",p.getLong("id",-1),legacy=true)
                    UpdateStore.write(c,a);current=a;p.edit().clear().commit()
                }
            }
            loaded=true
            current?.let(::publish)
        } catch(e: Exception) {
            // Preserve corrupt evidence; do not enqueue or install using an unreadable record.
            AppUpdateChecker.transferFailed("Saved update state is unavailable. Stop this attempt before retrying.")
            throw e
        }
    }
    fun initialize(context: Context) {
        app=context.applicationContext
        UpdateAutoInstall.initialize(context.applicationContext)
        if(!monitorEnabled || monitor?.isActive==true)return
        monitor=scope.launch {
            try {
                load(context.applicationContext)
                while(isActive) {
                    refresh(context.applicationContext)
                    if(current==null || current?.phase in setOf("ready","permission","failed","fallback","cancelled","installed","confirmation","installing","staging")) break
                    delay(1000)
                }
            } catch(_: CancellationException) { }
            catch(_: Exception) { AppUpdateChecker.transferFailed("Couldn't restore the update. Stop and retry.") }
        }
    }
    suspend fun start(context: Context, update: AppUpdateChecker.UpdateInfo, forceFull: Boolean=false) = withContext(Dispatchers.IO) {
        if(AppUpdateChecker.externallyProvided)return@withContext
        val acceptedGeneration=generation
        load(context)
        val previous=current
        if(previous!=null && previous.phase !in setOf("failed","fallback","cancelled","installed")) { initialize(context);return@withContext }
        require(UpdateSources.asset(requireNotNull(update.apkUrl)) && UpdateSources.hash(update.sha256) && requireNotNull(update.sizeBytes) in 1..SpoolDelta.MAX_APK)
        // Selection hashes the actual standalone installed base, not an old downloaded APK.
        var delta: UpdateCatalog.Delta?=null
        if(!forceFull && update.deltas.isNotEmpty() && context.applicationInfo.splitSourceDirs.isNullOrEmpty()) {
            AppUpdateChecker.transferState(update,AppUpdateChecker.DownloadState.Preparing("Checking installed version"))
            val base=File(context.applicationInfo.sourceDir)
            val hash=SpoolDelta.sha256(base) { if(generation!=acceptedGeneration)throw IOException("Update selection stopped") }
            delta=update.deltas.singleOrNull { it.baseSize==base.length() && it.baseSha256.equals(hash,true) }
        }
        val a=lock.withLock {
            if(generation!=acceptedGeneration || current?.token!=previous?.token || current?.phase!=previous?.phase)return@withLock null
            val offered=_offer.value?.takeIf { it.first==update.version }?.second
            val offerChanged=!forceFull && offered!=null && offered<requireNotNull(update.sizeBytes) && delta?.size!=offered
            UpdateAttempt(UUID.randomUUID().toString(),update,phase=if(offerChanged)"fallback" else "preparing",delta=delta,
                message=if(offerChanged)"The installed version changed. Review the full download size before continuing." else "", autoInstallPending=!offerChanged).also {
                require(context.noBackupFilesDir.usableSpace>requireNotNull(update.sizeBytes)*2+16*1024*1024) { "Not enough free space to prepare this update" }
                UpdateStore.write(context,it);current=it;publish(it)
            }
        } ?: return@withContext
        try {
            if(previous!=null)processing.withLock {
                if(previous.id>=0)manager(context).remove(previous.id)
                transferFile(context,previous).delete();previous.temporary(context).delete();previous.candidate(context).delete()
                previous.directory(context).delete()
            }
            if(a.phase!="fallback")enqueue(context,a)
        } finally { initialize(context) }
    }
    private suspend fun enqueue(c: Context,a: UpdateAttempt) {
        val file=a.transfer(c);require(file.parentFile?.mkdirs()==true || file.parentFile?.isDirectory==true)
        require(!file.exists()) { "Download destination already exists" }
        val request=DownloadManager.Request(Uri.parse(a.transferUrl)).setTitle("Spool update ${a.info.version}")
            .setDescription("After verification, Android installation opens when Spool is in use.")
            .setMimeType("application/octet-stream")
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
        val id=try { manager(c).enqueue(request) } catch(e: Exception) {
            change(a,"failed","Android could not start the download. Retry download.")
            throw e
        }
        val adopted=lock.withLock {
            if(current?.token!=a.token || current?.phase!="preparing")false else {
                val n=a.copy(id=id,phase="queued");UpdateStore.write(c,n);current=n;publish(n);true
            }
        }
        if(!adopted)manager(c).remove(id)
    }
    suspend fun acceptFull(c: Context) {
        val a=current ?: return
        if(a.phase!="fallback")return
        start(c,a.info.copy(deltas=emptyList()),true)
    }
    fun cancel() {
        generation++
        // UI dispatch is nonblocking, including while verification is reading a large APK.
        scope.launch { cancelAndAwait() }
    }
    internal suspend fun cancelAndAwait() {
        generation++
        val c=app ?: return
        if(!loaded)runCatching { load(c) }
        val a=lock.withLock {
            val old=current
            if(old!=null) {
                val next=old.copy(phase="cancelled",message="Update stopped",autoInstallPending=false,permissionHandoffLaunched=false)
                UpdateStore.write(c,next);current=next;publish(next)
            } else {
                // Explicit Stop recovers a corrupt record without trusting its paths or IDs.
                UpdateStore.clear(c);c.getSharedPreferences("spool-update-transfer",Context.MODE_PRIVATE).edit().clear().commit();loaded=true;AppUpdateChecker.transferIdle()
            }
            old
        } ?: return
        UpdateNavigation.clear(c)
        if(a.id>=0)manager(c).remove(a.id)
        else manager(c).query(DownloadManager.Query()).use { q ->
            while(q.moveToNext())if(q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))==Uri.fromFile(a.transfer(c)).toString())
                manager(c).remove(q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)))
        }
        if(a.session>=0)runCatching { c.packageManager.packageInstaller.abandonSession(a.session) }
        // A worker may still hold a file open. It sees the invalidated token before the next chunk.
        transferFile(c,a).delete();a.temporary(c).delete();a.candidate(c).delete()
    }
    private fun transferFile(c: Context,a: UpdateAttempt): File = if(a.legacy) File(requireNotNull(c.getExternalFilesDir(null)),"updates/spool-update.apk") else a.transfer(c)
    internal suspend fun restore(context: Context) { load(context) }
    suspend fun refresh(context: Context, waitForProcessing: Boolean=false) = withContext(Dispatchers.IO) {
        load(context)
        if(waitForProcessing)processing.lock() else if(!processing.tryLock())return@withContext
        try {
            var a=current ?: return@withContext
            if(a.phase in setOf("cancelled","failed","fallback","installed"))return@withContext
            if(!AppUpdateChecker.isNewerVersion(a.info.version)) {
                if(AppUpdateChecker.isInstalledTarget(context,a.info,a.delta?.targetCode))change(a,"installed","Update installed")
                else change(a,"failed","A different or newer app version is installed. Check for a new update.")
                UpdateNavigation.clear(context)
                return@withContext
            }
            if(a.phase in setOf("ready","permission")) {
                if(!a.candidate(context).isFile)change(a,"failed","Prepared update is missing. Retry download.")
                return@withContext
            }
            if(a.phase in setOf("staging","confirmation","installing")) { UpdateInstaller.reconcile(context,a);return@withContext }
            if(a.id<0) {
                // Recover the enqueue/persist gap using only this exact app-owned destination.
                val matches=mutableListOf<Long>()
                manager(context).query(DownloadManager.Query()).use { q -> while(q.moveToNext()) {
                    val uri=q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    if(uri==Uri.fromFile(a.transfer(context)).toString())matches+=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                } }
                if(matches.size>1) { matches.forEach { manager(context).remove(it) };change(a,"failed","Duplicate transfer recovered. Retry download.");return@withContext }
                if(matches.isEmpty()) { a.transfer(context).delete();enqueue(context,a);return@withContext }
                a=lock.withLock {
                    if(current?.token!=a.token || current?.phase!=a.phase)return@withLock null
                    a.copy(id=matches.single(),phase="queued").also { UpdateStore.write(context,it);current=it;publish(it) }
                } ?: return@withContext
            }
            var successful=false
            manager(context).query(DownloadManager.Query().setFilterById(a.id)).use { q ->
                if(!q.moveToFirst()) { change(a,"failed","Download is no longer available. Retry download.");return@use }
                val bytes=q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val status=q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason=q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                if(bytes>a.transferSize) { fail(context,a,"Download exceeds its declared size");return@use }
                when(status) {
                    DownloadManager.STATUS_SUCCESSFUL -> successful=true
                    DownloadManager.STATUS_FAILED -> fail(context,a,"Download failed ($reason). Retry download.")
                    DownloadManager.STATUS_PAUSED -> change(a,"waiting",when(reason) {
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for a connection"
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
                        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Android will retry the connection"
                        else -> "Paused by Android ($reason)"
                    })
                    DownloadManager.STATUS_PENDING -> if(a.phase!="queued")change(a,"queued")
                    DownloadManager.STATUS_RUNNING -> {
                        val next=if(a.phase!="downloading")change(a,"downloading") else a
                        if(next!=null && active(next))AppUpdateChecker.transferProgress(a.info,(bytes.toFloat()/a.transferSize).coerceIn(0f,1f),bytes,a.transferSize)
                    }
                    else -> fail(context,a,"Unknown download status ($status)")
                }
            }
            if(!successful || !active(a))return@withContext
            a=change(a,"verifying") ?: return@withContext
            val deadline=android.os.SystemClock.elapsedRealtime()+8L*60*1000
            fun boundedCheckpoint() {
                checkpoint(a)
                if(android.os.SystemClock.elapsedRealtime()>deadline)throw IOException("Update preparation timed out. Retry or use the full download.")
            }
            try {
                val input=transferFile(context,a)
                require(input.length()==a.transferSize) { "Download size mismatch" }
                require(SpoolDelta.sha256(input) { boundedCheckpoint() }.equals(a.transferHash,true)) { "Download checksum mismatch" }
                val dir=a.directory(context);require(dir.mkdirs() || dir.isDirectory)
                require(dir.usableSpace>requireNotNull(a.info.sizeBytes)+16*1024*1024) { "Not enough space to prepare update" }
                val temporary=a.temporary(context);temporary.delete()
                val d=a.delta
                if(d!=null) {
                    a=change(a,"applying") ?: return@withContext
                    require(context.applicationInfo.splitSourceDirs.isNullOrEmpty()) { "Split APK base is unsupported" }
                    SpoolDelta.apply(File(context.applicationInfo.sourceDir),input,temporary,d.baseSize,requireNotNull(a.info.sizeBytes),d.baseSha256,requireNotNull(a.info.sha256)) { boundedCheckpoint() }
                } else {
                    input.inputStream().use { source -> java.io.FileOutputStream(temporary).use { out ->
                        val b=ByteArray(65536);var copied=0L;while(true) { boundedCheckpoint();val n=source.read(b);if(n<0)break;copied+=n;require(copied<=requireNotNull(a.info.sizeBytes)) { "Source size changed" };out.write(b,0,n) };require(copied==a.info.sizeBytes);out.fd.sync()
                    } }
                }
                checkpoint(a)
                AppUpdateChecker.verifyDownloadedApk(context,temporary,a.info,a.delta?.targetCode,a.delta?.minSdk,a.delta?.abi)
                checkpoint(a)
                require(temporary.renameTo(a.candidate(context))) { "Could not save verified update" }
                if(change(a,"ready",autoInstallPending=a.autoInstallPending) == null)a.candidate(context).delete()
                // Candidate is now private; no installer reads DownloadManager's external file.
                manager(context).remove(a.id);input.delete()
            } catch(e: Exception) {
                if(active(a))fail(context,a,e.message ?: "Update preparation failed")
                a.temporary(context).delete()
            }
        } finally { processing.unlock() }
    }
    private suspend fun fail(c: Context,a: UpdateAttempt,message: String) {
        val phase=if(a.delta!=null)"fallback" else "failed"
        if(change(a,phase,message)!=null) {
            if(a.id>=0)manager(c).remove(a.id)
            transferFile(c,a).delete();a.temporary(c).delete()
        }
    }
    fun scheduleVerification(context: Context,id: Long) {
        if(id<0)return
        val token=current?.takeIf { it.id==id }?.token
        WorkManager.getInstance(context).enqueueUniqueWork("spool-update-verify-$id",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateVerificationWorker>().setInputData(workDataOf("token" to (token ?: ""),"id" to id)).build())
    }
}
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action==DownloadManager.ACTION_DOWNLOAD_COMPLETE)UpdateDownloads.scheduleVerification(context,intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1))
    }
}
class UpdateVerificationWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try {
        if(runAttemptCount>2)Result.failure() else {
            UpdateDownloads.restore(applicationContext)
            val a=UpdateDownloads.snapshot()
            val id=inputData.getLong("id",-1)
            val token=inputData.getString("token").orEmpty()
            if((id>=0 && a?.id!=id) || (token.isNotBlank() && a?.token!=token))Result.success()
            else { UpdateDownloads.refresh(applicationContext,true);Result.success() }
        }
    } catch(_: Exception) { if(runAttemptCount>=2)Result.failure() else Result.retry() }
}
