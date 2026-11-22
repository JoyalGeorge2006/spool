package com.music.bitchord.data.settings

import org.junit.Test
import org.junit.Assert.*

class BackupPreferenceTypesTest {
    @Test fun validatesFreshInstallKeysRatherThanTrustingFileTypes() {
        try { BackupPreferenceTypes.checked(mapOf("spool_updates_auto" to "bad"), emptyMap<String, Any>()); fail("must reject") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun acceptsTypedKnownAndExistingSettings() {
        val values = mapOf("spool_updates_auto" to false, "fixture" to "a")
        assertEquals(values, BackupPreferenceTypes.checked(values, mapOf("fixture" to "b")))
    }
    @Test fun unknownAbsentKeysAreNotInstalled() {
        assertTrue(BackupPreferenceTypes.checked(mapOf("future-key" to "x"), emptyMap<String, Any>()).isEmpty())
    }
    @Test fun paletteAndBlurKeysAreAcceptedWithTheirDeclaredTypes() {
        val values = mapOf("palette_mode" to "MATERIAL_YOU", "blur_effects" to true)
        assertEquals(values, BackupPreferenceTypes.checked(values, emptyMap<String, Any>()))
    }
    @Test fun paletteAndBlurKeysRejectWrongTypes() {
        for (bad in listOf<Pair<String, Any>>("palette_mode" to 1, "blur_effects" to "true")) {
            try { BackupPreferenceTypes.checked(mapOf(bad), emptyMap<String, Any>()); fail("must reject $bad") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun nonFiniteFloatRejected() {
        try { BackupPreferenceTypes.checked(mapOf("fixture" to Float.NaN), mapOf("fixture" to 1f)); fail("must reject") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun allWrongPrimitiveTypesAreRejected() {
        for (value in listOf<Any>(1, 1L, 1f, "true", setOf("true"))) {
            try { BackupPreferenceTypes.checked(mapOf("spool_updates_auto" to value), emptyMap<String, Any>()); fail("must reject $value") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
