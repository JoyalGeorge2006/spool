package com.music.bitchord.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import java.lang.ref.WeakReference
import kotlinx.coroutines.*

/** A download tap authorizes one verified install handoff, never a silent install. */
internal object UpdateAutoInstall : Application.ActivityLifecycleCallbacks {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var application: Application?=null
    @Volatile private var resumed=WeakReference<Activity>(null)
    @Volatile private var permissionToken: String?=null
    @Volatile private var permissionDeparted=false

    @Synchronized fun initialize(context: Context) {
        val app=context.applicationContext as? Application ?: return
        if(application===app)return
        application?.unregisterActivityLifecycleCallbacks(this)
        application=app;resumed=WeakReference(null)
        permissionToken=null;permissionDeparted=false
        app.registerActivityLifecycleCallbacks(this)
    }
    internal fun isResumed(context: Context): Boolean {
        val activity=resumed.get() ?: return false
        return activity===context && !activity.isFinishing && !activity.isDestroyed
    }
    internal fun expectPermissionReturn(activity: Context, token: String) {
        check(isResumed(activity)) { "Spool is no longer in the foreground" }
        permissionToken=token;permissionDeparted=false
    }
    // Same-resume queued work must not misread permission before Settings opens.
    // A cold process has no token witness: its first genuine resume may recover
    // persisted permission continuation, but denied permission never opens Settings again.
    internal fun awaitingPermissionDeparture(token: String)=permissionToken==token && !permissionDeparted

    internal suspend fun awaitRequestsForTest() {
        while(true) {
            val jobs=scope.coroutineContext[Job]!!.children.toList()
            if(jobs.isEmpty())return
            jobs.forEach { it.join() }
        }
    }
    fun request() {
        scope.launch {
            val activity=withContext(Dispatchers.Main) { resumed.get()?.takeIf { isResumed(it) } } ?: return@launch
            var owned: UpdateAttempt?=null
            try {
                UpdateDownloads.restore(activity.applicationContext)
                val a=UpdateDownloads.snapshot() ?: return@launch
                owned=a
                if(!a.autoInstallPending || a.phase !in setOf("ready","permission"))return@launch
                if(a.phase=="permission") {
                    if(!a.permissionHandoffLaunched) {
                        if(permissionToken!=a.token)UpdateDownloads.change(a,"permission","Permission handoff was interrupted. Tap Install to try again.",autoInstallPending=false)
                        return@launch
                    }
                    if(awaitingPermissionDeparture(a.token))return@launch
                    if(Build.VERSION.SDK_INT>=26 && !(UpdateInstaller.backendForTests?.canInstall(activity) ?: activity.packageManager.canRequestPackageInstalls())) {
                        UpdateDownloads.change(a,"permission","Installation permission was not granted. Tap Install when you want to try again.",autoInstallPending=false)
                        return@launch
                    }
                }
                if(withContext(Dispatchers.Main) { isResumed(activity) })UpdateInstaller.install(activity,automaticAttempt=a)
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) {
                // No retry loop or inferred success. Keep the verified candidate
                // and the existing manual Install recovery when continuation fails.
                val a=UpdateDownloads.snapshot()?.takeIf { it.token==owned?.token }
                if(a!=null && a.autoInstallPending && a.phase in setOf("ready","permission"))
                    UpdateDownloads.change(a,a.phase,"Automatic installation could not continue. Tap Install to retry.",autoInstallPending=false)
            }
        }
    }
    override fun onActivityResumed(activity: Activity) { resumed=WeakReference(activity);UpdateInstaller.activityResumed(activity);request() }
    override fun onActivityPaused(activity: Activity) {
        if(resumed.get()===activity) {
            if(permissionToken!=null)permissionDeparted=true
            resumed=WeakReference(null)
        }
    }
    override fun onActivityDestroyed(activity: Activity) { if(resumed.get()===activity)resumed=WeakReference(null) }
    override fun onActivityCreated(activity: Activity,state: Bundle?)=Unit
    override fun onActivityStarted(activity: Activity)=Unit
    override fun onActivityStopped(activity: Activity)=Unit
    override fun onActivitySaveInstanceState(activity: Activity,state: Bundle)=Unit
}
