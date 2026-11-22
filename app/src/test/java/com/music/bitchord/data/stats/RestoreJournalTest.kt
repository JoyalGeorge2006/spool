package com.music.bitchord.data.stats

import java.io.File
import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.*

class RestoreJournalTest {
    private fun fixture(test: (File, File) -> Unit) {
        val dir = Files.createTempDirectory("restore-test").toFile()
        try { val live = File(dir, "listening").apply { mkdirs() }; File(live, "old.json").writeText("old"); test(dir, live) }
        finally { dir.deleteRecursively() }
    }
    @Test fun successReplacesBothStores() = fixture { dir, live ->
        var settings = "old"
        RestoreJournal(live).replace(mapOf("2026-01.json" to "new"), settings, { settings = "new" }, { settings = it })
        assertEquals("new", settings); assertFalse(File(live, "old.json").exists())
        assertEquals("new", File(live, "2026-01.json").readText()); assertFalse(File(dir, "listening-restore").exists())
    }
    @Test fun failedSettingsCommitRollsBackBothStores() = fixture { _, live ->
        var settings = "old"
        try {
            RestoreJournal(live).replace(mapOf("2026-01.json" to "new"), settings,
                { settings = "partial"; error("disk write failed") }, { settings = it })
            fail("expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals("old", settings); assertEquals("old", File(live, "old.json").readText())
        assertFalse(File(live, "2026-01.json").exists())
    }
    @Test fun invalidStagedNameNeverTouchesLiveData() = fixture { _, live ->
        var changed = false
        try { RestoreJournal(live).replace(mapOf("../bad" to "bad"), "old", { changed = true }, {})
            fail("expected rejection") } catch (_: IllegalArgumentException) { }
        assertFalse(changed); assertEquals("old", File(live, "old.json").readText())
    }
    @Test fun processStopAfterHistorySwapRecoversBeforeCommit() = fixture { dir, live ->
        val root = File(dir, "listening-restore").apply { mkdirs() }
        File(root, "settings-before.json").writeText("old-settings")
        assertTrue(live.renameTo(File(root, "previous")))
        live.mkdirs(); File(live, "new.json").writeText("new")
        var settings = "new-settings"
        RestoreJournal(live).recover { settings = it }
        assertEquals("old-settings", settings); assertEquals("old", File(live, "old.json").readText())
        assertFalse(File(live, "new.json").exists()); assertFalse(root.exists())
    }
    @Test fun processStopBeforeHistorySwapDoesNotDeleteOriginal() = fixture { dir, live ->
        val root = File(dir, "listening-restore").apply { mkdirs() }
        File(root, "settings-before.json").writeText("old")
        RestoreJournal(live).recover {}
        assertEquals("old", File(live, "old.json").readText())
    }
    @Test fun committedRestoreIsNeverRolledBack() = fixture { dir, live ->
        val root = File(dir, "listening-restore").apply { mkdirs() }
        File(root, "settings-before.json").writeText("old-settings")
        File(root, "COMMITTED").writeText("1")
        var rolledBack = false
        RestoreJournal(live).recover { rolledBack = true }
        assertFalse(rolledBack); assertTrue(File(live, "old.json").exists()); assertFalse(root.exists())
    }
    @Test fun failedRollbackKeepsRecoveryMaterial() = fixture { dir, live ->
        val root = File(dir, "listening-restore").apply { mkdirs() }
        File(root, "settings-before.json").writeText("old-settings")
        assertTrue(live.renameTo(File(root, "previous")))
        try { RestoreJournal(live).recover { error("settings still unwritable") }; fail("must fail") }
        catch (_: IllegalStateException) { }
        assertTrue(File(root, "previous/old.json").exists()); assertTrue(File(root, "settings-before.json").exists())
        RestoreJournal(live).recover {}; assertEquals("old", File(live, "old.json").readText())
    }
    @Test fun incompleteRecoverySnapshotCannotBlockStartupOrTouchLiveData() = fixture { dir, live ->
        val root = File(dir, "listening-restore").apply { mkdirs() }
        File(root, "settings-before.tmp").writeText("truncated-json")
        var restored = false
        RestoreJournal(live).recover { restored = true }
        assertFalse(restored); assertFalse(root.exists())
        assertEquals("old", File(live, "old.json").readText())
    }

}
