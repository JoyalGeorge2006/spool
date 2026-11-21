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
 * The shared UI layer reads its strings from Compose Multiplatform resources at
 * sharedUi/src/commonMain/composeResources/values*/, a SECOND bundle independent of
 * app/src/main/res (audited by LocaleStringsTest). A missing catalogue or key there
 * silently falls back to English mid-screen - this is why Malayalam showed English
 * Home/Settings chrome even while app/src/main/res/values-ml was complete: sharedUi
 * had no values-ml at all. Both bundles must hold parity for every offered language.
 */
@RunWith(Parameterized::class)
class SharedUiLocaleStringsTest(private val tag: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<String> = SUPPORTED_LANGUAGES.map { it.tag }.filter { it != "en" }

        private val resDir: File by lazy {
            val projectRoot = File(".").canonicalFile
            if (File(projectRoot, "sharedUi/src/commonMain/composeResources").exists()) {
                File(projectRoot, "sharedUi/src/commonMain/composeResources")
            } else {
                File(projectRoot, "../sharedUi/src/commonMain/composeResources")
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
    }

    @Test
    fun strings_containsAllBaseKeys() {
        val qualifier = LocaleStringsTest.tagToQualifier(tag)
        val localeFile = File(resDir, "values-$qualifier/strings.xml")
        assertTrue(
            "sharedUi composeResources values-$qualifier/strings.xml must exist (language $tag); " +
                "call sites importing com.music.bitchord.sharedui.resources.* fall back to English without it",
            localeFile.exists(),
        )

        val missingKeys = baseKeys - translatableKeys(parse(localeFile))
        assertTrue(
            "sharedUi composeResources values-$qualifier/strings.xml (language $tag) is missing ${missingKeys.size} keys from values/strings.xml: $missingKeys",
            missingKeys.isEmpty(),
        )
    }
}
