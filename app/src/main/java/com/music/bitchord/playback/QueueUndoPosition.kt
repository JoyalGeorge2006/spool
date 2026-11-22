package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier

/** Pure placement policy. Stale numeric indices are never restoration anchors. */
internal object QueueUndoPosition {
    data class Entry(val id: String?, val tier: QueueTier)

    fun choose(items: List<Entry>, currentIndex: Int, tier: QueueTier,
        beforeId: String?, afterId: String?, originalCurrentId: String?, wasHistory: Boolean = false, wasCurrent: Boolean = false): Int? {
        fun anchor(id: String?): Int? {
            if (id == null) return null
            val matches = items.indices.filter { items[it].id == id && items[it].tier == tier }
            return matches.singleOrNull()
        }
        val before = anchor(beforeId)
        val after = anchor(afterId)
        if (wasHistory) return when {
            after != null && after <= currentIndex -> after
            before != null && before < currentIndex -> before + 1
            originalCurrentId != null && items.getOrNull(currentIndex)?.id == originalCurrentId -> currentIndex
            else -> null
        }
        if (wasCurrent) return when {
            items.isEmpty() -> 0
            originalCurrentId != null && items.getOrNull(currentIndex)?.id == originalCurrentId -> currentIndex + 1
            else -> null
        }
        // The neighbors recorded at removal are the restoration anchor, even
        // when the playhead has drifted onto the next neighbor by the time the
        // undo lands (after == currentIndex: anchor is the playing row, insert
        // directly before it). An anchor that has fallen BEHIND the playhead
        // is no anchor for an ahead-of-playhead removal: refuse rather than
        // insert into played history (neverInsertsBeforePlayhead).
        return when {
            after != null && after >= currentIndex -> after
            before != null && before >= currentIndex -> before + 1
            items.isEmpty() -> 0
            originalCurrentId != null && items.getOrNull(currentIndex)?.id == originalCurrentId -> {
                val following = currentIndex + 1 until items.size
                following.lastOrNull { items[it].tier == tier }?.plus(1)
                    ?: following.firstOrNull { items[it].tier.ordinal > tier.ordinal }
                    ?: items.size
            }
            else -> null
        }
    }
}
