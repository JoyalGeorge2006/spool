package com.music.bitchord.data.canvas

import java.text.Normalizer
import java.util.Locale

/** Legacy name-normalization helper retained for artist matching regression coverage. */
fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * One credit string into its individual artists. Every service picks its own
 * separator — commas, ampersands, "feat.", a bare "x" between collaborators —
 * so comparing the joined strings would fail on formatting alone.
 */
fun splitArtists(raw: String): List<String> =
    raw.split(ARTIST_SEPARATORS)
        .map { it.normalizeForMatch() }
        .filter { it.isNotBlank() }

private val ARTIST_SEPARATORS = Regex(
    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
    RegexOption.IGNORE_CASE,
)
