package com.music.bitchord.data.stats

import java.io.File
import java.io.FileOutputStream

/** A private, same-filesystem restore journal. Kept until both stores commit.
 * If the process stops before COMMITTED, startup rolls both stores back.
 * No credentials are added: settingsBefore contains only exportable preferences.
 */
internal class RestoreJournal(private val live: File) {
    private val root = File(live.parentFile, "${live.name}-restore")
    private val staged get() = File(root, "staged")
    private val previous get() = File(root, "previous")
    private val before get() = File(root, "settings-before.json")
    private val committed get() = File(root, "COMMITTED")

    fun recover(restoreSettings: (String) -> Unit) {
        if (!root.exists()) return
        if (committed.exists()) {
            root.deleteRecursively()
            return
        }
        if (before.exists()) {
            // Restore settings first. If this fails, preserve both journal and
            // previous history for the next attempt rather than losing either.
            restoreSettings(before.readText())
            if (previous.exists()) {
                check(!live.exists() || live.deleteRecursively()) { "Could not roll back restored history" }
                check(previous.renameTo(live)) { "Could not restore previous history" }
            }
        }
        check(root.deleteRecursively()) { "Could not clean restore journal" }
    }

    fun replace(files: Map<String, String>, settingsBefore: String, applySettings: () -> Unit,
                restoreSettings: (String) -> Unit) {
        recover(restoreSettings)
        check(live.isDirectory || live.mkdirs()) { "Could not open listening storage" }
        check(staged.mkdirs()) { "Could not stage listening restore" }
        try {
            files.forEach { (name, text) ->
                require(name.matches(Regex("[0-9]{4}-[0-9]{2}\\.json")))
                durableWrite(File(staged, name), text)
            }
            // A durable settings snapshot precedes the first live-store mutation.
            val beforeTemp = File(root, "settings-before.tmp")
            durableWrite(beforeTemp, settingsBefore)
            check(beforeTemp.renameTo(before)) { "Could not save restore recovery settings" }
            check(live.renameTo(previous)) { "Could not preserve previous history" }
            check(staged.renameTo(live)) { "Could not install restored history" }
            applySettings()
            val commitTemp = File(root, "COMMITTED.tmp")
            durableWrite(commitTemp, "1")
            check(commitTemp.renameTo(committed)) { "Could not commit restore journal" }
        } catch (failure: Throwable) {
            try { recover(restoreSettings) } catch (rollback: Throwable) { failure.addSuppressed(rollback) }
            throw failure
        }
        // Cleanup failure is not a failed restore. COMMITTED allows safe retry
        // on startup without undoing a successful import.
        root.deleteRecursively()
    }

    private fun durableWrite(file: File, text: String) {
        FileOutputStream(file).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
    }
}
