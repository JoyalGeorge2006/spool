package com.music.bitchord.data

/** Complete means no dropped continuation. Failures must not look like the end of a list. */
internal data class ContinuationPage<T>(val items: List<T>, val continuation: String?)

internal suspend fun <T, K> completePages(
    first: ContinuationPage<T>,
    maxPages: Int,
    key: (T) -> K,
    next: suspend (String) -> ContinuationPage<T>,
): List<T> {
    require(maxPages > 0)
    val out = LinkedHashMap<K, T>()
    val tokens = HashSet<String>()
    var page = first
    var count = 0
    while (true) {
        page.items.forEach { out[key(it)] = it }
        count++
        val token = page.continuation ?: return out.values.toList()
        check(count < maxPages) { "List exceeds the supported page limit" }
        check(tokens.add(token)) { "Repeated list continuation" }
        page = next(token)
    }
}
