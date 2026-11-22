package com.music.bitchord.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionLookupCacheTest {
    @Test fun `empty video lookup does not throw or prevent a successful retry`() {
        val cache = VersionLookupCache<String>()
        cache["song"] = null
        assertNull(cache["song"])
        cache["song"] = "video"
        assertEquals("video", cache["song"])
    }
    @Test fun `transient empty answer cannot erase an established counterpart`() {
        val cache = VersionLookupCache<String>()
        cache["song"] = "video"
        cache["song"] = null
        assertEquals("video", cache["song"])
        assertNull(cache["other song"])
    }
}
