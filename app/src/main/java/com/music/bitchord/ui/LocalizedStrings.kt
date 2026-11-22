package com.music.bitchord.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The application context's configuration keeps the system locale: per-app
 * languages set with [AppCompatDelegate.setApplicationLocales] reach activity
 * and composition contexts, never `getApplication()`. ViewModels hold only the
 * application context, so a plain `context.getString` there answers in the
 * system language even after the user picks another app language. This is why
 * the offline Home message stayed English on a Malayalam Home while the
 * composition-resolved Retry action beside it was translated. Resolving
 * through a configuration context carrying the per-app locales fixes every
 * ViewModel string without touching activity code.
 */
internal fun localizedString(context: Context, id: Int, vararg formatArgs: Any): String =
    localizedString(context, AppCompatDelegate.getApplicationLocales(), id, *formatArgs)

internal fun localizedString(
    context: Context,
    locales: LocaleListCompat,
    id: Int,
    vararg formatArgs: Any,
): String {
    val resolved = if (locales.isEmpty) {
        context
    } else {
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        context.createConfigurationContext(config)
    }
    return if (formatArgs.isEmpty()) resolved.getString(id) else resolved.getString(id, *formatArgs)
}

/** The tag under which [localizedString] currently resolves; "" tracks the system locale. */
internal fun currentAppLocaleTag(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()
