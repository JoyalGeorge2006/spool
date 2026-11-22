package com.music.bitchord.data.stats

import android.content.Context
import android.net.Uri
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.SearchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The whole of what this app knows about you, as one JSON file you own.
 *
 * Two errands, and they are the same errand: moving to a new phone, and being
 * able to see what is being kept. Everything Replay counts lives only on this
 * device, which is the point of it — and the flip side of that is that a factory
 * reset takes it with no way back, so there has to be a way out.
 *
 * ## What is in it
 *
 * Every preference except credentials (see [AppSettings.exportPrefs]), and every
 * month of listening. Not the audio cache, the downloads or the artwork: those
 * are megabytes that re-fetch themselves, and a backup that is mostly cache is
 * one nobody keeps.
 *
 * ## Typed values rather than a JSON object
 *
 * Preferences come off Android as `Map<String, Any?>` and go back the same way,
 * where the *type* decides which `put` is called. Written as plain JSON, `0.5`
 * comes back a Double and lands in a Float preference as a class-cast crash the
 * first time it is read — weeks later, in a settings screen, with nothing
 * pointing at the import. So each value carries its type and is parsed back into
 * exactly the type it left as, and anything unrecognised is skipped rather than
 * guessed at.
 */
object Backup {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** A suggested filename, dated so successive exports don't collide. */
    fun suggestedName(): String =
        "bitchord-backup-${DateTimeFormatter.ofPattern("yyyy-MM-dd").format(
            Instant.now().atZone(ZoneId.systemDefault()),
        )}.json"

    /**
     * Writes a backup to [target], a document the user picked.
     *
     * Through the content resolver rather than a [java.io.File] because the
     * destination is wherever they chose — Drive, a USB stick, a folder this app
     * has no path to and no permission for. The picker grants access to that one
     * document and nothing else, which is the correct amount.
     */
    suspend fun exportTo(context: Context, target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val buckets = ListeningStats.exportAll()
            val file = BackupFile(
                app = APP_TAG,
                version = SCHEMA_VERSION,
                versionName = BuildConfig.VERSION_NAME,
                exportedAt = Instant.now().toString(),
                settings = AppSettings.exportPrefs().mapNotNull { (key, value) ->
                    PrefValue.of(value)?.let { key to it }
                }.toMap(),
                listening = buckets,
            )
            val text = json.encodeToString(BackupFile.serializer(), file)
            context.contentResolver.openOutputStream(target, "wt")
                ?.use { it.write(text.toByteArray()) }
                ?: error("Couldn't open that file for writing")
            buckets.size
        }
    }

    /**
     * Reads [source] and replaces this device's settings and listening with it.
     *
     * Validated before anything is written: a file that isn't one of ours, or is
     * from a schema this build can't read, is refused whole. A half-applied
     * import is worse than a refused one — it leaves settings from two devices
     * mixed together with nothing to say which came from where.
     */
    suspend fun importFrom(context: Context, source: Uri): Result<Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)
                ?.use { it.readBytes().decodeToString() }
                ?: error("Couldn't open that file")
            val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
                .getOrElse { error("That doesn't look like a BitChord backup") }
            require(file.app == APP_TAG) { "That backup is from another app" }
            require(file.version in 1..SCHEMA_VERSION) { "Unsupported backup version" }
            validateListening(file.listening)
            val incoming = AppSettings.checkedImportPrefs(file.settings.mapValues { (key, value) ->
                requireNotNull(value.decoded()) { "Invalid value for setting $key" }
            })
            val before = encodeSettings(AppSettings.exportPrefs())
            ListeningStats.restoreAll(file.listening, before,
                applySettings = { AppSettings.importPrefs(incoming) },
                restoreSettings = { AppSettings.restorePrefsSnapshot(decodeSettings(it)) },
            )
            // Shares AppSettings' preference file, so it has already been
            // overwritten by the line above — it just doesn't know yet.
            SearchHistory.reload()
            Summary(
                months = file.listening.size,
                settings = incoming.size,
                from = file.versionName,
                at = file.exportedAt,
            )
        }
    }

    internal fun recoverInterruptedRestore(context: Context) {
        RestoreJournal(File(context.filesDir, "listening")).recover {
            AppSettings.restorePrefsSnapshot(decodeSettings(it), reloadFlows = false)
        }
    }

    internal fun encodeSettings(values: Map<String, Any?>): String = json.encodeToString(
        kotlinx.serialization.builtins.MapSerializer(String.serializer(), PrefValue.serializer()),
        values.mapValues { requireNotNull(PrefValue.of(it.value)) },
    )

    private fun decodeSettings(text: String): Map<String, Any?> = json.decodeFromString(
        kotlinx.serialization.builtins.MapSerializer(String.serializer(), PrefValue.serializer()), text,
    ).mapValues { requireNotNull(it.value.decoded()) }

    internal fun validateListening(buckets: List<StoredBucket>) {
        require(buckets.map { it.month }.distinct().size == buckets.size) { "Duplicate listening month" }
        buckets.forEach { bucket ->
            val month = YearMonth.parse(bucket.month)
            require(month.toString() == bucket.month && month.year in 1..9999) { "Invalid listening month" }
            require(bucket.version == 1) { "Unsupported listening schema" }
            require(bucket.hours.size == 24 && bucket.hours.all { it >= 0L }) { "Invalid hourly listening" }
            require(bucket.days.all { (day, ms) -> day in 1..month.lengthOfMonth() && ms >= 0L }) { "Invalid daily listening" }
            fun checkDays(days: Map<String, DayStats>) {
                require(days.all { (day, stats) ->
                    val date = java.time.LocalDate.parse(day)
                    YearMonth.from(date) == month && stats.ms >= 0L && stats.plays >= 0
                }) { "Invalid listening day" }
            }
            require(bucket.tracks.all { it.id.isNotBlank() && it.ms >= 0 && it.plays >= 0 && it.last >= 0 }) { "Invalid track totals" }
            require((bucket.artists + bucket.albums).all { it.ms >= 0 && it.plays >= 0 }) { "Invalid listening totals" }
            bucket.tracks.forEach { checkDays(it.days) }
            (bucket.artists + bucket.albums).forEach { checkDays(it.days) }
            listOfNotNull(bucket.trackDays, bucket.artistDays, bucket.albumDays).forEach(::checkDays)
        }
    }

    /** What an import turned out to contain, for the line shown afterwards. */
    data class Summary(
        val months: Int,
        val settings: Int,
        val from: String,
        val at: String,
    )

    private const val APP_TAG = "bitchord"

    /**
     * Bump when the shape below stops being readable by an older build. A file
     * from a *newer* schema is refused rather than partially read; one from an
     * older schema is read as-is, since every field has a default.
     */
    private const val SCHEMA_VERSION = 1

    @Serializable
    data class BackupFile(
        val app: String,
        val version: Int,
        /** Which build wrote it — for the reader, not for any logic here. */
        val versionName: String = "",
        val exportedAt: String = "",
        val settings: Map<String, PrefValue>,
        val listening: List<StoredBucket>,
    )

    /** One preference, with the type it has to be restored as. */
    @Serializable
    data class PrefValue(
        val type: String,
        val value: String? = null,
        val values: List<String> = emptyList(),
    ) {
        fun decoded(): Any? = when (type) {
            BOOLEAN -> value?.toBooleanStrictOrNull()
            INT -> value?.toIntOrNull()
            LONG -> value?.toLongOrNull()
            FLOAT -> value?.toFloatOrNull()?.takeIf { it.isFinite() }
            STRING -> value
            STRING_SET -> values.toSet()
            else -> null
        }

        companion object {
            /**
             * Numbers go out as strings on purpose. JSON has one number type and
             * every parser picks its own Kotlin type back off it, which is
             * exactly the ambiguity this whole class exists to remove.
             */
            fun of(value: Any?): PrefValue? = when (value) {
                is Boolean -> PrefValue(BOOLEAN, value.toString())
                is Int -> PrefValue(INT, value.toString())
                is Long -> PrefValue(LONG, value.toString())
                is Float -> PrefValue(FLOAT, value.toString())
                is String -> PrefValue(STRING, value)
                is Set<*> -> PrefValue(STRING_SET, values = value.filterIsInstance<String>())
                else -> null
            }

            private const val BOOLEAN = "bool"
            private const val INT = "int"
            private const val LONG = "long"
            private const val FLOAT = "float"
            private const val STRING = "string"
            private const val STRING_SET = "stringSet"
        }
    }
}
