package com.music.bitchord.data

import com.music.bitchord.data.model.TopPickMood
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicRegionTest {
    private val india = MusicRegion.State(auto = true, detectedCountry = "IN")

    @Test
    fun autoUsesDetectedCountryAndManualOverrides() {
        assertEquals("IN", MusicRegion.countryOf(india))
        val manual = india.copy(auto = false, manualCountry = "US")
        assertEquals("US", MusicRegion.countryOf(manual))
        // Detection off with nothing chosen falls back to the detected one.
        assertEquals("IN", MusicRegion.countryOf(india.copy(auto = false)))
    }

    @Test
    fun indiaOffersIndianLanguagesAndUsDoesNot() {
        val inIds = MusicRegion.languagesFor("IN").map { it.id }
        assertTrue("malayalam" in inIds && "tamil" in inIds && "english" in inIds)
        val usIds = MusicRegion.languagesFor("US").map { it.id }
        assertTrue("malayalam" !in usIds && "english" in usIds)
    }

    @Test
    fun selectionIsLimitedToTheCountryAndEmptyMeansDefaults() {
        val picked = india.copy(selected = setOf("malayalam", "spanish"))
        assertEquals(listOf("malayalam"), MusicRegion.activeLanguages(picked).map { it.id })
        // Nothing valid selected: India defaults to its regional languages, the US to English.
        assertTrue(MusicRegion.activeLanguages(india).size > 3)
        assertEquals(listOf("english"), MusicRegion.activeLanguages(MusicRegion.State(detectedCountry = "US")).map { it.id })
        // English names no scene, so it never steers.
        assertTrue(MusicRegion.steeringLanguages(MusicRegion.State(detectedCountry = "US")).isEmpty())
    }

    @Test
    fun languageShelfOutranksEnglishAndSleepAvoidsSoundscapes() {
        val ml = listOf(MusicRegion.languages.getValue("malayalam"))
        val shelfMl = MusicRegion.shelfBonus("Malayalam chill", TopPickMood.CHILL, ml)
        val shelfEn = MusicRegion.shelfBonus("Chill pop", TopPickMood.CHILL, ml)
        assertTrue(shelfMl > shelfEn)
        assertTrue(
            MusicRegion.shelfBonus("Ocean waves and rain", TopPickMood.SLEEP, ml) <
                MusicRegion.shelfBonus("Soft songs", TopPickMood.SLEEP, ml),
        )
        assertEquals(0, MusicRegion.shelfBonus("Malayalam chill", TopPickMood.CHILL, emptyList()))
    }

    @Test
    fun searchQueryNamesLanguageAndMood() {
        val q = MusicRegion.searchQuery(MusicRegion.languages.getValue("tamil"), TopPickMood.SLEEP)
        assertEquals("Tamil soft lullaby songs", q)
    }
}
