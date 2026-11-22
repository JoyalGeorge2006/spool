@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.music.bitchord.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.YouTubeProfile
import com.music.bitchord.data.model.*
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.settings.*
import com.music.bitchord.playback.*
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.tv.dialogs.*
import com.music.bitchord.ui.tv.onboarding.TvSetupScreen
import com.music.bitchord.ui.tv.player.TvNowPlayingScreen
import com.music.bitchord.ui.tv.screens.*
import com.music.bitchord.ui.tv.theme.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TV-sized previews of real production composables, NOT TV/device acceptance.
 * The mocked ViewModel is only a seeded flow boundary; it never constructs the
 * network-backed ViewModel or calls services. Artwork deliberately uses the
 * app's real missing-artwork fallback. No login, playback or write action occurs.
 * Run :tv:recordRoborazziProdDebug --tests '*TvPageScreenshotTest'.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-mdpi-television")
class TvPageScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val vm = mockk<MainViewModel>(relaxed = true)
    private val tracks = List(12) { i -> Song("fixture:$i", listOf("Midnight Rain", "Golden Hour", "Neon Skyline")[i % 3] + " ${i + 1}",
        "Fixture artist", null, durationText = "3:24", artistId = "UCfixture", albumId = "MPREfixture",
        albumName = "After Hours", queueEntryId = "entry:$i") }
    private val albums = List(8) { i -> ShelfItem("After Hours ${i + 1}", "Album · Fixture artist", null, null, "MPREfixture$i") }
    private val playlists = List(8) { i -> ShelfItem("Evening Mix ${i + 1}", "Playlist · 12 songs", null, null, "VLfixture$i") }
    private val artists = List(8) { i -> ShelfItem("Fixture artist ${i + 1}", "Artist", null, null, "UCfixture$i") }
    private val shelves = listOf(HomeShelf("Made for You", playlists), HomeShelf("New Releases", albums), HomeShelf("Artists", artists))
    private val library = LibraryPage(tracks.take(5), tracks, listOf(HomeShelf("Playlists", playlists), HomeShelf("Albums", albums), HomeShelf("Artists", artists)))
    private val moods = listOf(MoodGenreSection("Moods & Genres", listOf("Focus", "Chill", "Feel Good", "Commute", "Party", "Jazz", "Pop", "Sleep").mapIndexed { i, title ->
        MoodGenre(title, "mood:$i", null, stripeColor = listOf(0xFFD9A441, 0xFF4E7C59, 0xFF5B4E8C)[i % 3]) }))
    private val signedIn = MutableStateFlow(true)
    private val home = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Success(shelves))
    private val explore = MutableStateFlow<UiState<List<MoodGenreSection>>>(UiState.Success(moods))
    private val selectedMood = MutableStateFlow<MoodGenre?>(null)
    private val moodShelves = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Success(shelves))
    private val query = MutableStateFlow("")
    private val results = MutableStateFlow<UiState<List<SearchResult>>?>(null)
    private val filter = MutableStateFlow(SearchFilter.ALL)
    private val suggestions = MutableStateFlow(emptyList<String>())
    private val history = MutableStateFlow<UiState<List<Song>>>(UiState.Success(tracks))
    private val libraryFlow = MutableStateFlow<UiState<LibraryPage>>(UiState.Success(library))
    private val detail = MutableStateFlow(emptyList<DetailPage>())
    private val lyricFlow = MutableStateFlow<List<LyricLine>?>(null)
    private val profile = YouTubeProfile("fixture-profile", "Preview Listener", handle = "@preview")
    private val accounts = MutableStateFlow(listOf(GoogleAccountSession("fixture-account", "", name = "Preview Listener", email = "preview@example.invalid", profiles = listOf(profile), activeProfileId = profile.profileId)))
    private val player = PlayerState(song = tracks.first(), durationMs = 204000L, queue = tracks, queueIndex = 0, hasNext = true,
        position = PlaybackPosition().apply { report(42000L) })
    private val openDetail: TvOpenDetail = { _, _, _, _, _ -> }

    @Before fun seed() {
        TvSettings.init(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>())
        TvSettings.tvSetupVersionCompleted.value = 1
        TvSettings.tvUiScale.value = 1f
        TvSettings.tvTheme.value = "dynamic_artwork"
        every { vm.signedIn } returns signedIn
        every { vm.home } returns home
        every { vm.homeLoadingMore } returns MutableStateFlow(false)
        every { vm.explore } returns explore
        every { vm.selectedMoodGenre } returns selectedMood
        every { vm.moodGenreShelves } returns moodShelves
        every { vm.query } returns query
        every { vm.results } returns results
        every { vm.filter } returns filter
        every { vm.suggestions } returns suggestions
        every { vm.searchLoadingMore } returns MutableStateFlow(false)
        every { vm.searchHistory } returns MutableStateFlow(tracks.take(4).map { SearchHistoryEntity(it.videoId, it.title, it.artist, null, EntityType.TRACK) })
        every { vm.history } returns history
        every { vm.library } returns libraryFlow
        every { vm.detailStack } returns detail
        every { vm.lyrics } returns lyricFlow
        every { vm.lyricsChecked } returns MutableStateFlow(true)
        every { vm.account } returns MutableStateFlow(Account("Preview Listener", "preview@example.invalid", null))
        every { vm.googleAccounts } returns accounts
        every { vm.activeAccountId } returns MutableStateFlow<String?>("fixture-account")
        every { vm.activeProfileId } returns MutableStateFlow<String?>(profile.profileId)
        every { vm.updateAvailable } returns MutableStateFlow(null)
        every { vm.playlists } returns MutableStateFlow(listOf(UserPlaylist("fixture", "Evening Mix", "Private · 12 songs", null)))
        every { vm.playlistsLoading } returns MutableStateFlow(false)
    }
    private fun host(content: @Composable () -> Unit) {
        compose.setContent { BitChordTvTheme { BitChordTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize().background(TvThemeColors.current.background)) { content() }
        } } }
        compose.waitForIdle()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/tv_${name}_TEST_PREVIEW.png")
    }
    private fun click(text: String) { compose.onNodeWithText(text).performClick(); compose.waitForIdle() }
    private fun clickDescription(text: String) { compose.onNodeWithContentDescription(text).performClick(); compose.waitForIdle() }
    private fun allVerticalPositions(name: String, lastList: Boolean = false) {
        capture(name + "_top")
        val nodes = compose.onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        if (nodes.fetchSemanticsNodes().isEmpty()) return
        val list = if (lastList) nodes.onLast() else nodes.onFirst()
        var step = 0
        while (true) {
            val node = list.fetchSemanticsNode()
            val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
            val before = range.value(); val maximum = range.maxValue()
            println("TV_SCROLL $name step=$step value=$before max=$maximum")
            if (before >= maximum - 1f) break
            check(step < 50) { "TV scroll did not reach end: $name" }
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, node.boundsInRoot.height * 0.65f) }
            compose.waitForIdle()
            val after = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            check(after > before) { "No scroll progress: $name $before -> $after" }
            capture(name + "_position_" + step.toString().padStart(2, '0')); step++
        }
    }
    private fun shell(tab: String? = null) {
        host { TvApp(vm, null, player) }
        when(tab) {
            "Search", "Settings" -> clickDescription(tab)
            null, "Home" -> Unit
            else -> click(tab)
        }
    }
    private fun libraryTab(tab: String) {
        host { TvLibraryScreen(vm, null, player, openDetail, {}, {}) }
        if (tab != "Recently Played") click(tab)
        allVerticalPositions("library_" + tab.replace(' ', '_'), lastList = true)
    }
    private fun detailPage(name: String, type: BrowseType, state: UiState<List<Song>> = UiState.Success(tracks), id: String = "fixture:$name") {
        detail.value = listOf(DetailPage(id, if(type == BrowseType.ARTIST) "Fixture artist" else "After Hours", "Fixture artist · 2026", null,
            state, type, sections = shelves, description = "Seeded preview description for this real TV detail layout.", monthlyListenerCount = "1.2M monthly listeners"))
        host { TvDetailScreen(id, detail.value.first().title, "Fixture artist", null, type, vm, null, player, openDetail, {}, {}) }
        allVerticalPositions(name)
    }
    private fun search(state: UiState<List<SearchResult>>?, name: String, selectedFilter: SearchFilter = SearchFilter.ALL) {
        query.value = if (state == null) "" else "After Hours"; results.value = state; filter.value = selectedFilter
        shell("Search"); allVerticalPositions(name)
    }
    private fun dialog(name: String, content: @Composable () -> Unit) { host(content); allVerticalPositions(name) }

    @Test fun home() { shell(); allVerticalPositions("home") }
    @Test fun home_empty() { home.value = UiState.Success(emptyList()); shell(); capture("home_empty") }
    @Test fun home_error() { home.value = UiState.Error("Couldn't load recommendations. Check your connection."); shell(); capture("home_error") }
    @Test fun explore() { shell("Explore"); allVerticalPositions("explore") }
    @Test fun explore_mood() { selectedMood.value = moods.first().items.first(); shell("Explore"); allVerticalPositions("explore_mood") }
    @Test fun explore_error() { explore.value = UiState.Error("Couldn't load moods and genres."); shell("Explore"); capture("explore_error") }
    @Test fun library_recent() = libraryTab("Recently Played")
    @Test fun library_liked() = libraryTab("Liked Songs")
    @Test fun library_songs() = libraryTab("Songs")
    @Test fun library_playlists() = libraryTab("Playlists")
    @Test fun library_albums() = libraryTab("Albums")
    @Test fun library_artists() = libraryTab("Artists")
    @Test fun library_signed_out() { signedIn.value = false; history.value = UiState.Error("Sign in to see your history."); libraryTab("Recently Played") }
    @Test fun search_idle() = search(null, "search_idle")
    @Test fun search_results() = search(UiState.Success(tracks.map { SearchResult.Track(it) } + listOf(SearchResult.Browse(BrowseItem("MPREfixture", "After Hours", "Album", null, BrowseType.ALBUM)), SearchResult.Browse(BrowseItem("UCfixture", "Fixture artist", "Artist", null, BrowseType.ARTIST)), SearchResult.Browse(BrowseItem("VLfixture", "Evening Mix", "Playlist", null, BrowseType.PLAYLIST)))), "search_results")
    @Test fun search_songs() = search(UiState.Success(tracks.map { SearchResult.Track(it) }), "search_songs", SearchFilter.SONGS)
    @Test fun search_empty() = search(UiState.Success(emptyList()), "search_empty")
    @Test fun search_error() = search(UiState.Error("Couldn't search. Try again."), "search_error")
    @Test fun search_suggestions() { query.value = "after"; suggestions.value = listOf("after", "after hours", "after dark"); shell("Search"); capture("search_suggestions") }
    @Test fun album() = detailPage("album", BrowseType.ALBUM)
    @Test fun playlist() = detailPage("playlist", BrowseType.PLAYLIST)
    @Test fun artist() = detailPage("artist", BrowseType.ARTIST)
    @Test fun downloads() = detailPage("downloads", BrowseType.PLAYLIST, id = "local:downloads")
    @Test fun local_music() = detailPage("local_music", BrowseType.PLAYLIST, id = "local:all")
    @Test fun detail_error() = detailPage("detail_error", BrowseType.ALBUM, UiState.Error("Couldn't load this album."))
    @Test fun settings() { shell("Settings"); allVerticalPositions("settings") }
    @Test fun setup_steps() {
        signedIn.value = false
        host { TvSetupScreen(vm, {}) }
        capture("setup_1_welcome"); click("Continue")
        capture("setup_2_sign_in"); click("Not Now")
        capture("setup_3_appearance"); click("Continue")
        capture("setup_4_listening"); click("Continue")
        capture("setup_5_done")
    }
    @Test fun now_playing() { host { TvNowPlayingScreen(vm, null, player, {}) }; capture("now_playing") }
    @Test fun now_playing_lyrics() {
        lyricFlow.value = List(6) { LyricLine(it * 20000L, "Preview lyric line ${it + 1}") }
        host { TvNowPlayingScreen(vm, null, player, {}) }; capture("now_playing_lyrics")
    }
    @Test fun now_playing_empty() { host { TvNowPlayingScreen(vm, null, PlayerState(), {}) }; capture("now_playing_empty") }
    @Test fun up_next() { host { TvNowPlayingScreen(vm, null, player, {}) }; clickDescription("Up Next"); capture("up_next") }
    @Test fun player_more() { host { TvNowPlayingScreen(vm, null, player, {}) }; clickDescription("More"); allVerticalPositions("player_more") }
    @Test fun song_menu() = dialog("song_menu") { TvSongActionMenuDialog(tracks.first(), false, vm, null, {}, onDismiss = {}) }
    @Test fun add_to_playlist() = dialog("add_to_playlist") { TvAddToPlaylistDialog(tracks.first(), vm, {}) }
    @Test fun new_playlist() {
        host { TvAddToPlaylistDialog(tracks.first(), vm, {}) }; click("New Playlist…"); capture("new_playlist_no_system_keyboard")
    }
    @Test fun sleep_timer() = dialog("sleep_timer") { TvSleepTimerDialog({}) }
    @Test fun equalizer() = dialog("equalizer") { TvEqualizerDialog({}) }
    @Test fun equalizer_presets() { host { TvEqualizerDialog({}) }; click("Presets"); allVerticalPositions("equalizer_presets") }
    @Test fun appearance() = dialog("appearance") { TvThemeDialog({}) }
    @Test fun refresh_rate() = dialog("refresh_rate_simulated_display") { TvRefreshRateDialog({}) }
    @Test fun scrobbling() = dialog("scrobbling") { TvScrobbleDialog({}) }
    @Test fun lyrics_sources() = dialog("lyrics_sources") { TvLyricsSourcesDialog({}) }
    @Test fun about() = dialog("about") { TvAboutDialog({}) }
    @Test fun account_profiles() = dialog("account_profiles") { TvAccountDialog(vm, {}) }
    @Test fun account_settings() { host { TvAccountDialog(vm, {}) }; clickDescription("Account Settings"); capture("account_settings") }
    @Test fun account_set_pin() { host { TvAccountDialog(vm, {}) }; clickDescription("Account Settings"); click("App Lock (4-Digit PIN)"); capture("account_set_pin") }
    @Test fun sign_in_choice_optional_route() { signedIn.value = false; accounts.value = emptyList(); dialog("sign_in_choice_optional_route") { TvAccountDialog(vm, {}, allowPhoneSignIn = true) } }
}
