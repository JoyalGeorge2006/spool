package com.music.bitchord.data

import java.util.concurrent.ConcurrentHashMap

/** Successful version pairs only: misses and transient failures never poison a later lookup. */
internal class VersionLookupCache<T : Any> {
    private val pairs = ConcurrentHashMap<String, T>()
    operator fun get(sourceId: String): T? = pairs[sourceId]
    operator fun set(sourceId: String, counterpart: T?) {
        if (counterpart != null) pairs[sourceId] = counterpart
    }
}
