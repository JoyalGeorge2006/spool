package com.music.bitchord.data

import com.music.bitchord.data.model.TopPickMood
import java.util.Locale

/**
 * Where the listener's music comes from: a country (detected, or chosen) and
 * the languages they listen to in it.
 *
 * Detection is best effort and says nothing about taste. The country comes
 * from the SIM, the network or the phone's region, in that order — never GPS
 * — and the languages are the listener's to confirm: where someone is does
 * not tell us what they play.
 *
 * Mood stations read this two ways: the country is the `gl` their shelves are
 * requested with, and the languages steer which shelf and playlists are
 * played (see [TopPicks]).
 */
object MusicRegion {

    /** A music language: [hints] are words YouTube's shelf and playlist titles use for it. */
    data class Language(val id: String, val label: String, val hints: List<String>)

    private fun lang(id: String, label: String, vararg hints: String) =
        id to Language(id, label, hints.toList())

    val languages: Map<String, Language> = linkedMapOf(
        lang("english", "English", "english"),
        lang("malayalam", "Malayalam", "malayalam", "mollywood", "kerala"),
        lang("tamil", "Tamil", "tamil", "kollywood"),
        lang("hindi", "Hindi", "hindi", "bollywood"),
        lang("telugu", "Telugu", "telugu", "tollywood"),
        lang("kannada", "Kannada", "kannada", "sandalwood"),
        lang("marathi", "Marathi", "marathi"),
        lang("bengali", "Bengali", "bengali", "bangla"),
        lang("punjabi", "Punjabi", "punjabi"),
        lang("gujarati", "Gujarati", "gujarati"),
        lang("urdu", "Urdu", "urdu"),
        lang("sinhala", "Sinhala", "sinhala"),
        lang("nepali", "Nepali", "nepali"),
        lang("spanish", "Spanish", "spanish", "latin", "latino", "reggaeton"),
        lang("portuguese", "Portuguese", "portuguese", "brazil", "sertanejo"),
        lang("french", "French", "french", "fran\u00e7ais"),
        lang("german", "German", "german", "deutsch"),
        lang("italian", "Italian", "italian", "italiano"),
        lang("dutch", "Dutch", "dutch", "nederlands"),
        lang("polish", "Polish", "polish", "polskie"),
        lang("russian", "Russian", "russian"),
        lang("turkish", "Turkish", "turkish"),
        lang("arabic", "Arabic", "arabic", "arab", "khaleeji"),
        lang("korean", "Korean", "korean", "k-pop", "kpop"),
        lang("japanese", "Japanese", "japanese", "j-pop", "jpop", "anime"),
        lang("chinese", "Chinese", "chinese", "mandarin", "c-pop", "cantopop"),
        lang("indonesian", "Indonesian", "indonesia"),
        lang("thai", "Thai", "thai"),
        lang("vietnamese", "Vietnamese", "vietnam"),
        lang("tagalog", "Filipino", "filipino", "tagalog", "opm"),
    )

    /**
     * The languages music is commonly listened to in each country, the most
     * common first. A best-effort list, not a catalogue guarantee: whether
     * YouTube Music has mood shelves in a language is checked at play time.
     */
    private val byCountry: Map<String, List<String>> = mapOf(
        "IN" to listOf("malayalam", "tamil", "hindi", "telugu", "kannada", "marathi", "bengali", "punjabi", "gujarati", "english"),
        "US" to listOf("english", "spanish", "hindi", "korean"),
        "GB" to listOf("english", "hindi", "punjabi", "urdu"),
        "CA" to listOf("english", "french", "punjabi", "hindi"),
        "AU" to listOf("english", "hindi", "punjabi"),
        "NZ" to listOf("english"),
        "IE" to listOf("english"),
        "PK" to listOf("urdu", "punjabi", "english"),
        "BD" to listOf("bengali", "hindi", "english"),
        "LK" to listOf("sinhala", "tamil", "english"),
        "NP" to listOf("nepali", "hindi", "english"),
        "AE" to listOf("arabic", "hindi", "malayalam", "english"),
        "SA" to listOf("arabic", "english"),
        "QA" to listOf("arabic", "english"),
        "KW" to listOf("arabic", "english"),
        "EG" to listOf("arabic", "english"),
        "TR" to listOf("turkish", "english"),
        "KR" to listOf("korean", "english"),
        "JP" to listOf("japanese", "english"),
        "CN" to listOf("chinese", "english"),
        "TW" to listOf("chinese", "english"),
        "HK" to listOf("chinese", "english"),
        "SG" to listOf("english", "chinese", "tamil"),
        "MY" to listOf("english", "chinese", "tamil"),
        "ID" to listOf("indonesian", "english"),
        "TH" to listOf("thai", "english"),
        "VN" to listOf("vietnamese", "english"),
        "PH" to listOf("tagalog", "english"),
        "BR" to listOf("portuguese", "english"),
        "PT" to listOf("portuguese", "english"),
        "MX" to listOf("spanish", "english"),
        "ES" to listOf("spanish", "english"),
        "AR" to listOf("spanish", "english"),
        "CO" to listOf("spanish", "english"),
        "CL" to listOf("spanish", "english"),
        "PE" to listOf("spanish", "english"),
        "FR" to listOf("french", "english"),
        "DE" to listOf("german", "english"),
        "AT" to listOf("german", "english"),
        "CH" to listOf("german", "french", "italian", "english"),
        "IT" to listOf("italian", "english"),
        "NL" to listOf("dutch", "english"),
        "BE" to listOf("dutch", "french", "english"),
        "PL" to listOf("polish", "english"),
        "RU" to listOf("russian", "english"),
    )

    /** The languages listened to in [country]; empty when this table does not cover it (no worldwide stand-in list). */
    fun languagesFor(country: String): List<Language> =
        (byCountry[country.uppercase(Locale.ROOT)] ?: emptyList()).mapNotNull(languages::get)

    fun isCovered(country: String): Boolean = country.uppercase(Locale.ROOT) in byCountry

    /** Language ids for the phone's configured languages (e.g. "ml" becomes "malayalam"). */
    fun idsForLocales(locales: List<Locale>): Set<String> {
        return locales.mapNotNull { l ->
            val name = Locale(l.language).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT)
            name.takeIf { it in languages }
        }.toSet()
    }

    /** The persisted choice, plus what detection found. Read through [country] and [activeLanguages]. */
    data class State(
        val auto: Boolean = true,
        val detectedCountry: String? = null,
        val manualCountry: String? = null,
        val selected: Set<String> = emptySet(),
        /** Language ids from the phone settings; used only until the listener confirms a choice. */
        val deviceLanguages: Set<String> = emptySet(),
    )

    @Volatile
    var state: State = State()

    val country: String
        get() = countryOf(state)

    fun countryOf(s: State): String {
        val chosen = if (s.auto) s.detectedCountry else s.manualCountry
        return (chosen?.takeIf { it.length == 2 }
            ?: s.detectedCountry?.takeIf { it.length == 2 }
            ?: Locale.getDefault().country.takeIf { it.length == 2 }
            ?: "US").uppercase(Locale.ROOT)
    }

    /**
     * The chosen languages that exist for the country. Until the listener
     * chooses, only languages the phone is already set to count; nothing is
     * assumed beyond that. Choices for other countries are kept, not shown.
     */
    fun activeLanguages(s: State = state): List<Language> {
        val country = countryOf(s)
        val offered = languagesFor(country)
        val picked = offered.filter { it.id in s.selected }
        return picked.ifEmpty { offered.filter { it.id in s.deviceLanguages } }
    }

    /** True while no language has been confirmed for this country. */
    fun needsConfirmation(s: State = state): Boolean =
        languagesFor(countryOf(s)).none { it.id in s.selected }

    /** Languages that name a scene on a shelf. English names none, so it never steers. */
    fun steeringLanguages(s: State = state): List<Language> = activeLanguages(s).filter { it.hints.isNotEmpty() }

    fun displayCountry(code: String, inLocale: Locale = Locale.getDefault()): String =
        Locale("", code).getDisplayCountry(inLocale).ifBlank { code }

    // ---- Steering ------------------------------------------------------------

    /** How many of [langs] a shelf or playlist [title] names. */
    fun languageMatches(title: String, langs: List<Language>): Int {
        val t = title.lowercase(Locale.ROOT)
        return langs.count { l -> l.hints.any { it in t } }
    }

    private val NATURE_WORDS = listOf(
        "nature", "ocean", "rain", "white noise", "waves", "forest", "thunder", "binaural", "ambient", "soundscape",
    )

    fun natureMatches(title: String): Int {
        val t = title.lowercase(Locale.ROOT)
        return NATURE_WORDS.count { it in t }
    }

    /** The weight a language match adds to a shelf's score: more than the mood's own two words. */
    const val LANGUAGE_WEIGHT = 5

    /** Sleep wants songs, not soundscapes, once the listener has languages to be served in. */
    const val NATURE_PENALTY = 6

    fun shelfBonus(title: String, mood: TopPickMood, langs: List<Language>): Int {
        if (langs.isEmpty()) return 0
        var bonus = LANGUAGE_WEIGHT * languageMatches(title, langs)
        if (mood == TopPickMood.SLEEP) bonus -= NATURE_PENALTY * natureMatches(title).coerceAtMost(1)
        return bonus
    }

    private fun moodTerm(mood: TopPickMood): String = when (mood) {
        TopPickMood.ENERGY, TopPickMood.MOTIVATION -> "energetic"
        TopPickMood.RELAX, TopPickMood.CHILL, TopPickMood.ALONE_TIME, TopPickMood.AFTER_HOURS -> "relaxing"
        TopPickMood.FEELING_BLUE -> "sad"
        TopPickMood.FEEL_GOOD, TopPickMood.MORNING, TopPickMood.SUMMERTIME -> "feel good"
        TopPickMood.FOCUS, TopPickMood.WORK -> "focus"
        TopPickMood.HEARTBREAK -> "heartbreak"
        TopPickMood.LOVE -> "romantic"
        TopPickMood.SLEEP -> "soft lullaby"
        TopPickMood.PARTY, TopPickMood.SOCIAL, TopPickMood.WEEKEND -> "party"
        TopPickMood.FITNESS, TopPickMood.OUTDOORS, TopPickMood.SPORTS -> "workout"
        TopPickMood.COMMUTING -> "travel"
        else -> mood.name.lowercase(Locale.ROOT).replace('_', ' ')
    }

    /** The search that finds a language's songs for a mood when no shelf names it. */
    fun searchQuery(language: Language, mood: TopPickMood): String =
        "${language.label} ${moodTerm(mood)} songs"
}
