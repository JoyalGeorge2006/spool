package com.music.bitchord

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Repairs a stored disabled Default even before the user next opens the app. */
class SpoolPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching { AppIcon.ensureSingleLogo(context) }
            .onFailure { android.util.Log.w("spool", "Launcher upgrade repair will retry on app start", it) }
    }
}
