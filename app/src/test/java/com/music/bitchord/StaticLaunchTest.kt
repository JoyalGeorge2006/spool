package com.music.bitchord

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.VectorDrawable
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The native splash mark must remain static in both resource configurations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35])
class StaticLaunchTest {
    private fun checkSplash(night: Boolean) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.uiMode = (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
        val context = base.createConfigurationContext(configuration)
        val theme = context.resources.newTheme().apply { applyStyle(R.style.Theme_BitChord, true) }
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, value, true))
        val icon = requireNotNull(context.resources.getDrawable(value.resourceId, theme))
        assertTrue(icon is VectorDrawable)
        assertFalse(icon is Animatable)
        assertTrue(theme.resolveAttribute(android.R.attr.windowSplashScreenBackground, value, true))
    }
    @Test fun lightSplashIsStatic() = checkSplash(false)
    @Test fun darkSplashIsStatic() = checkSplash(true)
}
