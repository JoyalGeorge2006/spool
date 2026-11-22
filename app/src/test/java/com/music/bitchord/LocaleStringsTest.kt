package com.music.bitchord

import com.music.bitchord.ui.components.SUPPORTED_LANGUAGES
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language the app offers must translate every key in values/strings.xml. A missing key
 * doesn't crash, it silently falls back to English mid-screen, so this catches it at test time.
 */
@RunWith(Parameterized::class)
class LocaleStringsTest(private val tag: String) {

    companion object {
        /** Languages the owner requires fully translated for this release. English is the base. */
        private val COMPLETE_LANGUAGES = setOf("ml")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<String> = SUPPORTED_LANGUAGES.map { it.tag }.filter { it != "en" }

        /** Tab-separated `<bcp47-tag>\t<key>` lines; `#` comments. Key set per language, not a count. */
        internal fun loadBaseline(file: File): Map<String, Set<String>> {
            val map = mutableMapOf<String, MutableSet<String>>()
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val tab = trimmed.indexOf('\t')
                require(tab > 0) { "Malformed baseline line: $trimmed" }
                map.getOrPut(trimmed.substring(0, tab)) { mutableSetOf() }.add(trimmed.substring(tab + 1))
            }
            return map
        }

        private val resDir: File by lazy {
            val projectRoot = File(".").canonicalFile
            if (File(projectRoot, "app/src/main/res").exists()) {
                File(projectRoot, "app/src/main/res")
            } else {
                File(projectRoot, "src/main/res")
            }
        }

        private fun parse(file: File): Document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

        /** Keys that need translating: `translatable="false"` entries are excluded. */
        private fun translatableKeys(doc: Document): Set<String> {
            val keys = mutableSetOf<String>()
            for (tagName in listOf("string", "plurals", "string-array")) {
                val nodes = doc.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    if (element.getAttribute("translatable") == "false") continue
                    val name = element.getAttribute("name")
                    if (name.isNotEmpty()) keys.add(name)
                }
            }
            return keys
        }

        private val baseKeys: Set<String> by lazy {
            translatableKeys(parse(File(resDir, "values/strings.xml")))
        }

        private val baselineFile: File by lazy {
            val projectRoot = File(".").canonicalFile
            if (File(projectRoot, "app/src/test/resources").exists()) {
                File(projectRoot, "app/src/test/resources/locale-parity-baseline-app.txt")
            } else {
                File(projectRoot, "src/test/resources/locale-parity-baseline-app.txt")
            }
        }

        private val baselineMissing: Map<String, Set<String>> by lazy { loadBaseline(baselineFile) }

        /**
         * App language tags are BCP-47 (`zh-CN` / `zh-TW`) while Android
         * resource directories use `-r` regions (`values-zh`, `values-zh-rTW`).
         * Mirrors what Android resolves at runtime: Simplified is the bare
         * `zh` catalogue, Traditional is `zh-rTW`.
         */
        internal fun tagToQualifier(tag: String): String = when (tag) {
            "zh-CN" -> "zh"
            "zh-TW" -> "zh-rTW"
            else -> tag
        }
    }

    @Test
    fun strings_containsAllBaseKeys() {
        val qualifier = tagToQualifier(tag)
        val localeFile = File(resDir, "values-$qualifier/strings.xml")
        assertTrue("values-$qualifier/strings.xml must exist (for language tag $tag)", localeFile.exists())

        val missingKeys = baseKeys - translatableKeys(parse(localeFile))
        if (tag in COMPLETE_LANGUAGES) {
            assertTrue(
                "values-$qualifier/strings.xml (language $tag) is missing ${missingKeys.size} keys from values/strings.xml: $missingKeys",
                missingKeys.isEmpty(),
            )
        } else {
            // Approved release scope (owner decision 2026-11-21): English + Malayalam complete;
            // every other offered language stays available with platform fallback to English
            // for the keys it does not yet translate. The baseline file records exactly which
            // keys each such language currently lacks, so a NEWLY missing key (or a removed
            // translation, which surfaces as a newly missing key) fails, while completed
            // translations pass. Extend the baseline only with release-scope review.
            val baseline = baselineMissing[tag] ?: emptySet()
            val newMissing = missingKeys - baseline
            assertTrue(
                "values-$qualifier/strings.xml (language $tag) has ${newMissing.size} keys newly missing outside the approved fallback baseline: $newMissing",
                newMissing.isEmpty(),
            )
        }
    }
}
