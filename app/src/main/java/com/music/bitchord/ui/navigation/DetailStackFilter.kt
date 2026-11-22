package com.music.bitchord.ui.navigation

/** Number of retained detail pages that were below this route before filtering. */
fun filteredDetailDepth(previousDepth: Int, retained: List<Boolean>): Int =
    retained.take(previousDepth.coerceIn(0, retained.size)).count { it }
