package com.music.bitchord.data

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.music.bitchord.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Navigation only. Incoming intents cannot authorize a download or installation. */
internal object UpdateNavigation {
    private val _requests=MutableStateFlow(0L)
    val requests=_requests.asStateFlow()
    const val ACTION="app.spool.music.OPEN_UPDATES"
    fun consume(intent: Intent?) { if(intent?.action==ACTION)_requests.value+=1 }
    fun show(c: Context,title: String,message: String) {
        val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("spool-update-status","App updates",NotificationManager.IMPORTANCE_LOW))
        val open=Intent(c,MainActivity::class.java).setAction(ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(c,8165,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { nm.notify(8165,NotificationCompat.Builder(c,"spool-update-status")
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(title).setContentText(message)
            .setContentIntent(pending).setAutoCancel(true).build()) }
    }
    fun clear(c: Context) { (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(8165) }
}
