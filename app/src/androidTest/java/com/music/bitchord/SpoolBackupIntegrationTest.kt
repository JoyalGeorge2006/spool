package com.music.bitchord

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.Backup
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.StoredBucket
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses only emulator test data. Proves the limited JSON contract, not full-account/data migration. */
@RunWith(AndroidJUnit4::class)
class SpoolBackupIntegrationTest {
    @Test fun settingsHistoryRoundTripAndExcludedDataStayLocal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("bitchord_settings", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        val historyBefore = ListeningStats.exportAll()
        val file = File(context.cacheDir, "fixture-backup.json")
        try {
            prefs.edit().putString("fixture_roundtrip", "source")
                .putString("lastfm_session_key", "fixture-secret-never-export")
                .putString("downloaded_tracks", "fixture-download-source")
                .putString("local_music_folder_uri", "content://fixture/source")
                .putString("mp3_export_folder", "content://fixture/export").commit()
            ListeningStats.importAll(listOf(StoredBucket(month = "2026-01", days = mapOf(2 to 12000L))))
            assertTrue(Backup.exportTo(context, Uri.fromFile(file)).isSuccess)
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val settings = root.getValue("settings").jsonObject
            assertTrue(settings.containsKey("fixture_roundtrip"))
            for (excluded in listOf("lastfm_session_key", "downloaded_tracks", "local_music_folder_uri", "mp3_export_folder")) {
                assertFalse("Excluded $excluded", settings.containsKey(excluded))
            }
            assertFalse(file.readText().contains("fixture-secret-never-export"))
            prefs.edit().putString("fixture_roundtrip", "destination")
                .putString("lastfm_session_key", "destination-secret")
                .putString("downloaded_tracks", "destination-downloads")
                .putString("local_music_folder_uri", "content://fixture/destination").commit()
            ListeningStats.importAll(emptyList())
            assertTrue(Backup.importFrom(context, Uri.fromFile(file)).isSuccess)
            assertEquals("source", prefs.getString("fixture_roundtrip", null))
            assertEquals("destination-secret", prefs.getString("lastfm_session_key", null))
            assertEquals("destination-downloads", prefs.getString("downloaded_tracks", null))
            assertEquals("content://fixture/destination", prefs.getString("local_music_folder_uri", null))
            assertEquals(12000L, ListeningStats.exportAll().first { it.month == "2026-01" }.days[2])
            file.writeText("{\"app\":\"wrong-app\"}")
            assertTrue(Backup.importFrom(context, Uri.fromFile(file)).isFailure)
            assertEquals("source", prefs.getString("fixture_roundtrip", null))
        } finally {
            prefs.edit().apply {
                clear()
                before.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                } }
            }.commit()
            ListeningStats.importAll(historyBefore)
            AppSettings.reload()
            file.delete()
        }
    }
}
