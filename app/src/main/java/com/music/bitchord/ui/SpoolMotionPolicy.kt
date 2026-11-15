package com.music.bitchord.ui

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.WindowRecomposerFactory
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import com.music.bitchord.data.settings.AppSettings

/** Application-wide Compose motion policy, including new sheets and dialogs. */
internal object SpoolMotionPolicy {
    @Volatile private var systemScale = 1f
    private var installed = false

    internal fun effectiveScale(reduceMotion: Boolean, system: Float): Float =
        if (reduceMotion) 0f else system.takeIf { it.isFinite() && it >= 0f } ?: 1f

    @OptIn(InternalComposeUiApi::class)
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        fun updateScale() {
            systemScale = Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
        updateScale()
        // Process-lifetime observer, holding only the application context. There
        // is no settings-provider lookup on animation frames and no activity leak.
        app.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = updateScale()
            },
        )
        val motion = object : MotionDurationScale {
            override val scaleFactor: Float
                get() = effectiveScale(AppSettings.reduceAnimation.value, systemScale)
        }
        WindowRecomposerPolicy.setFactory(WindowRecomposerFactory { root ->
            root.createLifecycleAwareWindowRecomposer(coroutineContext = motion)
        })
    }
}
