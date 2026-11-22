package com.music.bitchord.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.music.bitchord.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage for the Malayalam-Home-English-offline-message defect:
 * a ViewModel resolving strings through the plain application context got the
 * system locale, ignoring the per-app language set with AppCompatDelegate.
 */
@RunWith(RobolectricTestRunner::class)
class LocalizedStringsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Mirrors what [localizedString] should do, built by hand from the public API. */
    private fun manualResolve(tag: String, id: Int, vararg args: Any): String {
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        val derived = context.createConfigurationContext(config)
        return if (args.isEmpty()) derived.getString(id) else derived.getString(id, *args)
    }

    @Test
    fun perAppLocaleBeatsSystemLocale() {
        val english = context.getString(R.string.no_internet_connection)
        val resolved = localizedString(context, LocaleListCompat.forLanguageTags("ml"), R.string.no_internet_connection)
        assertNotEquals(english, resolved)
        assertEquals(manualResolve("ml", R.string.no_internet_connection), resolved)
    }

    @Test
    fun emptyLocaleListMatchesPlainApplicationContext() {
        assertEquals(
            context.getString(R.string.no_internet_connection),
            localizedString(context, LocaleListCompat.getEmptyLocaleList(), R.string.no_internet_connection),
        )
    }

    @Test
    fun formatArgumentsResolveInPerAppLocale() {
        assertEquals(
            manualResolve("ml", R.string.local_playlist_subtitle, 3),
            localizedString(context, LocaleListCompat.forLanguageTags("ml"), R.string.local_playlist_subtitle, 3),
        )
    }

    @Test
    fun fallbackKeysMatchManualConfigurationContext() {
        // Whether or not a key has an ml translation, the helper must resolve
        // exactly what a configuration context for that locale resolves.
        assertEquals(
            manualResolve("ml", R.string.youtube_request_rejected),
            localizedString(context, LocaleListCompat.forLanguageTags("ml"), R.string.youtube_request_rejected),
        )
    }
}
