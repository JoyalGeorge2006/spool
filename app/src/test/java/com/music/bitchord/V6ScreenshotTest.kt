package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.ui.components.V6Badge
import com.music.bitchord.ui.components.V6Card
import com.music.bitchord.ui.components.V6Chip
import com.music.bitchord.ui.components.V6Hairline
import com.music.bitchord.ui.components.V6PageTitle
import com.music.bitchord.ui.components.V6PillButton
import com.music.bitchord.ui.components.V6SearchEntry
import com.music.bitchord.ui.components.V6SectionHeader
import com.music.bitchord.ui.onboarding.OnboardingScreen
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.data.model.TopPick
import com.music.bitchord.data.model.TopPickKind
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.ui.screens.ExploreScreen
import com.music.bitchord.ui.screens.LibraryLink
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.SettingsGroup
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v6 visual proof: records the rebuilt screens and shared components in both
 * themes, one PNG each under app/build/outputs/roborazzi, which CI publishes
 * as the v6-screenshots artifact. Run with :app:recordRoborazziDevDebug.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class V6ScreenshotTest {

    @org.junit.Before
    fun installUiHost() {
        // The shared screens read settings through AppUi.host; a minimal stub
        // keeps the renders free of the app's real settings store.
        runCatching { com.music.bitchord.ui.AppUi.host }.onFailure {
            com.music.bitchord.ui.AppUi.install(object : com.music.bitchord.ui.AppUiHost {
                override val swipeToPlayNext = kotlinx.coroutines.flow.MutableStateFlow(false)
                override val pinnedPlaylists = kotlinx.coroutines.flow.MutableStateFlow(emptyList<String>())
                override val librarySort = kotlinx.coroutines.flow.MutableStateFlow(com.music.bitchord.data.settings.LibrarySort.DEFAULT)
                @Composable
                override fun downloadedIds(): Set<String> = emptySet()
            })
        }
    }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        val storedTheme = com.music.bitchord.data.settings.AppSettings.themeMode.value
        com.music.bitchord.data.settings.AppSettings.setThemeMode(
            if (dark) com.music.bitchord.data.settings.ThemeMode.DARK else com.music.bitchord.data.settings.ThemeMode.LIGHT)
        try { captureRoboImage("build/outputs/roborazzi/$name.png") {
            BitChordTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    content()
                }
            }
        } } finally { com.music.bitchord.data.settings.AppSettings.setThemeMode(storedTheme) }
    }

    @Test
    fun bare_text() {
        captureRoboImage("build/outputs/roborazzi/bare_text.png") {
            Text("spool")
        }
    }

    @Test
    fun theme_only_light() {
        captureRoboImage("build/outputs/roborazzi/theme_only_light.png") {
            BitChordTheme(darkTheme = false) {
                Text("spool", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }

    @Test
    fun onboarding_light() = shot("onboarding_light", dark = false) {
        OnboardingScreen(
            signedIn = false,
            initialName = "Joyal",
            onNameChosen = {},
            onSignIn = {},
            onFinish = {},
        )
    }

    @Test
    fun onboarding_dark() = shot("onboarding_dark", dark = true) {
        OnboardingScreen(
            signedIn = false,
            initialName = "Joyal",
            onNameChosen = {},
            onSignIn = {},
            onFinish = {},
        )
    }

    @Test
    fun home_screen_light() = shot("home_screen_light", dark = false) {
        HomeWithChrome()
    }

    @Test
    fun home_screen_dark() = shot("home_screen_dark", dark = true) {
        HomeWithChrome()
    }

    @Test
    fun search_screen_light() = shot("search_screen_light", dark = false) {
        SearchWithChrome()
    }

    @Test
    fun search_screen_dark() = shot("search_screen_dark", dark = true) {
        SearchWithChrome()
    }

    @Test
    fun explore_screen_light() = shot("explore_screen_light", dark = false) {
        ExploreWithChrome()
    }

    @Test
    fun explore_screen_dark() = shot("explore_screen_dark", dark = true) {
        ExploreWithChrome()
    }

    @Test
    fun library_screen_light() = shot("library_screen_light", dark = false) {
        LibraryWithChrome()
    }

    @Test
    fun library_screen_dark() = shot("library_screen_dark", dark = true) {
        LibraryWithChrome()
    }

    @Test
    fun settings_group_light() = shot("settings_group_light", dark = false) {
        SettingsGroup(header = "Playback") {
            SettingRow("Audio quality", "Lossless")
            V6Hairline()
            SettingRow("Download over", "Wi-Fi only")
            V6Hairline()
            SettingRow("Normalize volume", "On")
        }
    }

    @Test
    fun settings_group_dark() = shot("settings_group_dark", dark = true) {
        SettingsGroup(header = "Playback") {
            SettingRow("Audio quality", "Lossless")
            V6Hairline()
            SettingRow("Download over", "Wi-Fi only")
            V6Hairline()
            SettingRow("Normalize volume", "On")
        }
    }

    /**
     * The REAL HomeScreen composable with stand-in feed data, so the render
     * checks the screen component with fixture data: title + history button, search,
     * mood chips, Jump back in (MP3 badge on t1), Made for tonight, New
     * releases ("New" badges), then the remaining card shelves.
     */
    /**
     * The real Home viewport: the actual HomeScreen with the app's actual
     * chrome (MiniPlayer + FloatingBottomBar with the real four tabs), for component comparison, mini player included. This fixture does not
     * invoke the full root or reproduce its contentInsets/header routing.
     * Feed data and the playing song are fixtures; the composables are not.
     */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    internal fun HomeWithChrome(rootPadding: PaddingValues = PaddingValues(0.dp), chrome: Boolean = true, floatingHeader: Boolean = false) {
        val haze = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }
        val demoSong = com.music.bitchord.data.model.Song(
            videoId = "t1",
            title = "Midnight Rain 1",
            artist = "Nova Rae",
            thumbnailUrl = null,
        )
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            HomeFeed(rootPadding, floatingHeader)
            if (chrome) Column(
                Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                com.music.bitchord.ui.components.MiniPlayer(
                    song = demoSong,
                    isPlaying = true,
                    isLoading = false,
                    // Production passes the live position; the ring only draws
                    // when it has one, so the fixture needs real numbers too.
                    position = androidx.compose.runtime.remember {
                        com.music.bitchord.playback.PlaybackPosition().apply { report(45_000L) }
                    },
                    durationMs = 208_000L,
                    hazeState = haze,
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onExpand = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                com.music.bitchord.ui.components.FloatingBottomBar(
                    tabs = listOf(
                        com.music.bitchord.ui.components.BottomTab("Listen", com.music.bitchord.ui.icons.BitChordIcons.HomeOutline),
                        com.music.bitchord.ui.components.BottomTab("Explore", com.music.bitchord.ui.icons.BitChordIcons.Explore),
                        com.music.bitchord.ui.components.BottomTab("Library", com.music.bitchord.ui.icons.BitChordIcons.Library),
                        com.music.bitchord.ui.components.BottomTab("Search", com.music.bitchord.ui.icons.BitChordIcons.Search),
                    ),
                    selectedIndex = 0,
                    onTabSelected = {},
                    hazeState = haze,
                )
            }
        }
    }

    /**
     * The REAL SearchScreen in its idle state with stand-in recents and
     * mood/genre categories, plus the app's actual chrome (MiniPlayer +
     * FloatingBottomBar with Search selected). Fixture data, real composables:
     * search field, Recent searches, and the artwork-led Browse moods &
     * genres grid (covers null in the fixture, so cards draw their category
     * colour placeholders).
     */
    @Composable
    internal fun SearchWithChrome(rootPadding: PaddingValues = PaddingValues(0.dp), chrome: Boolean = true, floatingHeader: Boolean = false) {
        val haze = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }
        val demoSong = com.music.bitchord.data.model.Song(
            videoId = "t1",
            title = "Midnight Rain",
            artist = "Nova Rae",
            thumbnailUrl = null,
        )
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            SearchIdle(rootPadding, rootHeader = floatingHeader || !chrome)
            if (chrome) Column(
                Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                com.music.bitchord.ui.components.MiniPlayer(
                    song = demoSong,
                    isPlaying = true,
                    isLoading = false,
                    // Production passes the live position; the ring only draws
                    // when it has one, so the fixture needs real numbers too.
                    position = androidx.compose.runtime.remember {
                        com.music.bitchord.playback.PlaybackPosition().apply { report(45_000L) }
                    },
                    durationMs = 208_000L,
                    hazeState = haze,
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onExpand = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                com.music.bitchord.ui.components.FloatingBottomBar(
                    tabs = listOf(
                        com.music.bitchord.ui.components.BottomTab("Listen", com.music.bitchord.ui.icons.BitChordIcons.HomeOutline),
                        com.music.bitchord.ui.components.BottomTab("Explore", com.music.bitchord.ui.icons.BitChordIcons.Explore),
                        com.music.bitchord.ui.components.BottomTab("Library", com.music.bitchord.ui.icons.BitChordIcons.Library),
                        com.music.bitchord.ui.components.BottomTab("Search", com.music.bitchord.ui.icons.BitChordIcons.Search),
                    ),
                    selectedIndex = 3,
                    onTabSelected = {},
                    hazeState = haze,
                )
            }
        }
    }

    /**
     * The REAL ExploreScreen with stand-in server sections (fixture titles
     * and categories; covers null so cards draw their category-colour
     * loading state) plus the app's actual chrome, Explore tab selected.
     * Proves the sectioned, artwork-led grid layout.
     */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    internal fun ExploreWithChrome(rootPadding: PaddingValues = PaddingValues(0.dp), chrome: Boolean = true, floatingHeader: Boolean = false) {
        val haze = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }
        val demoSong = com.music.bitchord.data.model.Song(
            videoId = "t1",
            title = "Midnight Rain",
            artist = "Nova Rae",
            thumbnailUrl = null,
        )
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            ExploreScreen(
                state = UiState.Success(fixtureMoodSections()),
                listState = rememberLazyListState(),
                onCategoryClick = {},
                onRetry = {},
                refreshing = false,
                onRefresh = {},
                pullState = rememberPullToRefreshState(),
                contentPadding = rootPadding,
                floatingTitle = floatingHeader,
            )
            if (chrome) Column(
                Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                com.music.bitchord.ui.components.MiniPlayer(
                    song = demoSong,
                    isPlaying = true,
                    isLoading = false,
                    // Production passes the live position; the ring only draws
                    // when it has one, so the fixture needs real numbers too.
                    position = androidx.compose.runtime.remember {
                        com.music.bitchord.playback.PlaybackPosition().apply { report(45_000L) }
                    },
                    durationMs = 208_000L,
                    hazeState = haze,
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onExpand = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                com.music.bitchord.ui.components.FloatingBottomBar(
                    tabs = listOf(
                        com.music.bitchord.ui.components.BottomTab("Listen", com.music.bitchord.ui.icons.BitChordIcons.HomeOutline),
                        com.music.bitchord.ui.components.BottomTab("Explore", com.music.bitchord.ui.icons.BitChordIcons.Explore),
                        com.music.bitchord.ui.components.BottomTab("Library", com.music.bitchord.ui.icons.BitChordIcons.Library),
                        com.music.bitchord.ui.components.BottomTab("Search", com.music.bitchord.ui.icons.BitChordIcons.Search),
                    ),
                    selectedIndex = 1,
                    onTabSelected = {},
                    hazeState = haze,
                )
            }
        }
    }

    /**
     * The REAL LibraryScreen with stand-in data: the folder card (Downloads +
     * Local Music rows with subtitles), an On device shelf, Playlists with
     * the New playlist / Import Spotify leading tiles, and an Albums shelf -
     * plus the app's actual chrome, Library tab selected. Replay row left
     * empty, its real no-history state. Fixture data, real composables.
     */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    internal fun LibraryWithChrome(rootPadding: PaddingValues = PaddingValues(0.dp), chrome: Boolean = true, floatingHeader: Boolean = false) {
        val haze = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }
        val demoSong = com.music.bitchord.data.model.Song(
            videoId = "t1",
            title = "Midnight Rain",
            artist = "Nova Rae",
            thumbnailUrl = null,
        )
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            LibraryScreen(
                signedIn = true,
                state = UiState.Success(
                    LibraryPage(
                        likedSongs = emptyList(),
                        librarySongs = emptyList(),
                        shelves = listOf(
                            HomeShelf(
                                title = com.music.bitchord.data.YtMusicRepository.PLAYLISTS_SHELF,
                                items = listOf(
                                    ShelfItem("Rainy days", "Playlist", null, null, "pl1"),
                                    ShelfItem("Morning mix", "Playlist", null, null, "pl2"),
                                ),
                            ),
                            HomeShelf(
                                title = "Albums",
                                items = listOf(
                                    ShelfItem("Golden Hour Drive", "Album", null, null, "al1"),
                                    ShelfItem("After Hours", "Album", null, null, "al2"),
                                ),
                            ),
                        ),
                        likedContinuation = null,
                    ),
                ),
                listState = rememberLazyListState(),
                onShelfItemClick = {},
                onShelfItemLongPress = {},
                onNewPlaylist = {},
                onImportSpotifyPlaylist = {},
                onShowAll = {},
                replay = {},
                onSignIn = {},
                onRetry = {},
                refreshing = false,
                onRefresh = {},
                pullState = rememberPullToRefreshState(),
                contentPadding = rootPadding,
                floatingTitle = floatingHeader,
                links = listOf(
                    LibraryLink(ShelfItem("Downloads", "Downloaded songs", null, null, "local:downloads"), androidx.compose.material.icons.Icons.Rounded.Download),
                    LibraryLink(ShelfItem("Local Music", "Audio files on device", null, null, "local:all"), androidx.compose.material.icons.Icons.Rounded.Folder),
                ),
                deviceItems = listOf(
                    ShelfItem("Midnight Rain", "Downloaded playlist", null, null, "dl1"),
                    ShelfItem("Lofi Loft", "Downloaded album", null, null, "dl2"),
                ),
            )
            if (chrome) Column(
                Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                com.music.bitchord.ui.components.MiniPlayer(
                    song = demoSong,
                    isPlaying = true,
                    isLoading = false,
                    // Production passes the live position; the ring only draws
                    // when it has one, so the fixture needs real numbers too.
                    position = androidx.compose.runtime.remember {
                        com.music.bitchord.playback.PlaybackPosition().apply { report(45_000L) }
                    },
                    durationMs = 208_000L,
                    hazeState = haze,
                    onPlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onExpand = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                com.music.bitchord.ui.components.FloatingBottomBar(
                    tabs = listOf(
                        com.music.bitchord.ui.components.BottomTab("Listen", com.music.bitchord.ui.icons.BitChordIcons.HomeOutline),
                        com.music.bitchord.ui.components.BottomTab("Explore", com.music.bitchord.ui.icons.BitChordIcons.Explore),
                        com.music.bitchord.ui.components.BottomTab("Library", com.music.bitchord.ui.icons.BitChordIcons.Library),
                        com.music.bitchord.ui.components.BottomTab("Search", com.music.bitchord.ui.icons.BitChordIcons.Search),
                    ),
                    selectedIndex = 2,
                    onTabSelected = {},
                    hazeState = haze,
                )
            }
        }
    }

    private fun fixtureMoodSections() = listOf(
        MoodGenreSection(
            title = "Moods",
            items = listOf(
                MoodGenre("Commute", "mg1", null, thumbnailUrl = null, stripeColor = 0xFFD9A441),
                MoodGenre("Diwali", "mg2", null, thumbnailUrl = null, stripeColor = 0xFF8A93A6),
                MoodGenre("Feel good", "mg3", null, thumbnailUrl = null, stripeColor = 0xFF4E7C59),
                MoodGenre("Focus", "mg4", null, thumbnailUrl = null, stripeColor = 0xFF6E6E78),
                MoodGenre("Party", "mg5", null, thumbnailUrl = null, stripeColor = 0xFF5B4E8C),
                MoodGenre("Romance", "mg6", null, thumbnailUrl = null, stripeColor = 0xFF9C4A3C),
            ),
        ),
        MoodGenreSection(
            title = "Genres",
            items = listOf(
                MoodGenre("Jazz", "mg7", null, thumbnailUrl = null, stripeColor = 0xFF7A6A4A),
                MoodGenre("Hip-hop", "mg8", null, thumbnailUrl = null, stripeColor = 0xFF4A5A7A),
            ),
        ),
    )

    @Composable
    private fun SearchIdle(rootPadding: PaddingValues = PaddingValues(0.dp), rootHeader: Boolean = false) {
        val history = listOf(
            SearchHistoryEntity("t1", "Midnight Rain", "Nova Rae", null, EntityType.TRACK),
            SearchHistoryEntity("t2", "lo-fi beats to study", "Chill Corner", null, EntityType.TRACK),
            SearchHistoryEntity("t3", "Neon Skyline", "Vega Noir", null, EntityType.TRACK),
        )
        val moods = listOf(
            MoodGenreSection(
                title = "Moods & genres",
                items = listOf(
                    MoodGenre("Commute", "mg1", null, thumbnailUrl = null, stripeColor = 0xFFD9A441),
                    MoodGenre("Diwali", "mg2", null, thumbnailUrl = null, stripeColor = 0xFF8A93A6),
                    MoodGenre("Feel good", "mg3", null, thumbnailUrl = null, stripeColor = 0xFF4E7C59),
                    MoodGenre("Focus", "mg4", null, thumbnailUrl = null, stripeColor = 0xFF6E6E78),
                    MoodGenre("Party", "mg5", null, thumbnailUrl = null, stripeColor = 0xFF5B4E8C),
                    MoodGenre("Romance", "mg6", null, thumbnailUrl = null, stripeColor = 0xFF9C4A3C),
                ),
            ),
        )
        SearchScreen(
            query = "",
            onQueryChange = {},
            filter = SearchFilter.ALL,
            currentSong = null,
            isPlaying = false,
            onFilterChange = {},
            results = null,
            loadingMore = false,
            onLoadMore = {},
            listState = rememberLazyListState(),
            scrollResetTrigger = 0,
            focusRequested = false,
            onFocusHandled = {},
            onSongClick = { _, _ -> },
            onSongLongPress = {},
            onSongSwipe = {},
            onTopResultPlay = {},
            onTopResultPlaylist = {},
            onBrowseClick = {},
            history = history,
            suggestions = emptyList(),
            typeaheadResults = emptyList(),
            onSubmit = {},
            onSuggestionClick = {},
            onHistoryClick = {},
            onHistoryRemove = {},
            onHistoryClear = {},
            moodGenres = UiState.Success(moods),
            onMoodGenreClick = {},
            contentPadding = rootPadding,
            topPadding = 0.dp,
            showField = true,
            scrollingHeader = rootHeader,
            pageTitle = if (rootHeader) "Search" else null,
            sourceSwitcher = if (rootHeader) { { com.music.bitchord.ui.screens.SegmentedControl(options = listOf("YouTube", "Library", "Addons"), selectedIndex = 0, onSelect = {}) } } else null,
        )
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeFeed(rootPadding: PaddingValues = PaddingValues(0.dp), floatingHeader: Boolean = false) {
        val tracks = (1..4).map { i ->
            ShelfItem(title = "Midnight Rain $i", subtitle = "Nova Rae · Neon Skyline", thumbnailUrl = null, videoId = "t$i", browseId = null)
        }
        val albums = (1..4).map { i ->
            ShelfItem(title = "Crimson Sky $i", subtitle = "Vega Noir · Single", thumbnailUrl = null, videoId = null, browseId = "b$i")
        }
        val shelves = listOf(
            HomeShelf(title = "Recents", items = tracks, layout = ShelfLayout.LIST),
            HomeShelf(title = "New releases", items = albums, layout = ShelfLayout.CARDS),
        )
        val picks = listOf(
            TopPick(kind = TopPickKind.PERSONAL_STATION),
            TopPick(kind = TopPickKind.NEW_RELEASE_MIX, title = "Daily Mix 01", subtitle = "Nova Rae, Cassia & more"),
        )
        HomeScreen(
            state = UiState.Success(shelves),
            listState = rememberLazyListState(),
            onItemClick = { _, _ -> },
            onRetry = {},
            refreshing = false,
            onRefresh = {},
            pullState = rememberPullToRefreshState(),
            contentPadding = rootPadding,
            title = "Listen",
            floatingTitle = floatingHeader,
            onSearchClick = {},
            onHistoryClick = {},
            onMoodClick = { _, _ -> },
            topPicks = picks,
            onTopPickClick = { _, _ -> },
            exportedMp3Ids = setOf("t1"),
        )
    }

    @Composable
    private fun SettingRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // ---- Detail pages: the REAL DetailScreen as an album, a playlist, an
    // artist and the Local Music folder. Fixture data; real composables. ----

    private fun detailSongs(n: Int, artist: String = "Nova Rae") = (1..n).map {
        com.music.bitchord.data.model.Song(
            videoId = "d$artist$it",
            title = "Track $it",
            artist = artist,
            thumbnailUrl = null,
            durationText = "3:2$it",
        )
    }

    @Composable
    internal fun DetailShot(page: com.music.bitchord.data.model.DetailPage, padding: PaddingValues = PaddingValues(0.dp), shelf: HomeShelf? = null) {
        com.music.bitchord.ui.screens.DetailScreen(
            page = page,
            currentSong = null,
            isPlaying = false,
            onSongClick = { _, _ -> },
            onSongLongPress = {},
            onSongSwipe = {},
            onShuffle = {},
            onSectionItemClick = {},
            onArtistClick = { _, _ -> },
            onAddSuggested = {},
            contentPadding = padding,
            activeShelf = shelf,
        )
    }

    internal fun albumPage() = com.music.bitchord.data.model.DetailPage(
        browseId = "album1",
        title = "Afterglow",
        subtitle = "Album · Nova Rae · 2024",
        thumbnailUrl = null,
        songs = UiState.Success(detailSongs(6)),
        type = com.music.bitchord.data.model.BrowseType.ALBUM,
        library = com.music.bitchord.data.model.LibraryState("PL1", saved = false),
        description = "The debut album.",
    )

    internal fun playlistPage() = com.music.bitchord.data.model.DetailPage(
        browseId = "pl1",
        title = "Rainy Day Drive",
        subtitle = "Playlist · 2023",
        thumbnailUrl = null,
        songs = UiState.Success(detailSongs(5)),
        type = com.music.bitchord.data.model.BrowseType.PLAYLIST,
        library = com.music.bitchord.data.model.LibraryState("PL2", saved = false),
        suggestedSongs = detailSongs(3, "June Harbor"),
    )

    internal fun artistPage() = com.music.bitchord.data.model.DetailPage(
        browseId = "UC1",
        title = "Nova Rae",
        subtitle = "Artist",
        thumbnailUrl = null,
        songs = UiState.Success(detailSongs(5)),
        type = com.music.bitchord.data.model.BrowseType.ARTIST,
        subscriberCountText = "1.2M subscribers",
        monthlyListenerCount = "3.4M monthly listeners",
        description = "Nova Rae is a stand-in artist for the render.",
        subscription = com.music.bitchord.data.model.SubscriptionState("UC1", subscribed = false),
    )

    @Test
    fun detail_album_light() = shot("detail_album_light", dark = false) { DetailShot(albumPage()) }

    @Test
    fun detail_album_dark() = shot("detail_album_dark", dark = true) { DetailShot(albumPage()) }

    @Test
    fun detail_playlist_light() = shot("detail_playlist_light", dark = false) { DetailShot(playlistPage()) }

    @Test
    fun detail_playlist_dark() = shot("detail_playlist_dark", dark = true) { DetailShot(playlistPage()) }

    @Test
    fun detail_artist_light() = shot("detail_artist_light", dark = false) { DetailShot(artistPage()) }

    @Test
    fun detail_artist_dark() = shot("detail_artist_dark", dark = true) { DetailShot(artistPage()) }

    @Composable
    internal fun LocalMusicShot(padding: PaddingValues = PaddingValues(0.dp)) {
        val folderState = androidx.compose.runtime.remember { com.music.bitchord.ui.screens.LocalFolderState() }
        com.music.bitchord.ui.screens.DetailScreen(
            page = com.music.bitchord.data.model.DetailPage(
                browseId = "local:all",
                title = "Local Music",
                subtitle = "",
                thumbnailUrl = null,
                songs = UiState.Success(detailSongs(6)),
            ),
            currentSong = null,
            isPlaying = false,
            onSongClick = { _, _ -> },
            onSongLongPress = {},
            onSongSwipe = {},
            onShuffle = {},
            onSectionItemClick = {},
            onArtistClick = { _, _ -> },
            onAddSuggested = {},
            contentPadding = padding,
            folder = com.music.bitchord.ui.screens.LocalFolder(
                state = folderState,
                sort = com.music.bitchord.data.settings.LocalMusicSort.TITLE_ASC,
                viewType = com.music.bitchord.data.settings.LibraryViewType.LIST,
                onOpenGroup = {},
                onGroupLongPress = {},
                tabs = com.music.bitchord.ui.screens.LocalFolderTab.DEVICE,
            ),
        )
    }

    @Test
    fun detail_local_music_light() = shot("detail_local_music_light", dark = false) { LocalMusicShot() }

    @Test
    fun detail_local_music_dark() = shot("detail_local_music_dark", dark = true) { LocalMusicShot() }

    // ---- The REAL Material player on its shipped default theme (FLAT: the
    // M3 lavender FAB and tonal chips approved for v6). The fixture song has
    // no cover, so the artwork box shows the placeholder. Mid-song. ----

    @Composable
    internal fun PlayerShot(video: Boolean = false, emptyLyrics: Boolean = false, party: Boolean = false, landscape: Boolean = false,
        titleText: String = "Midnight Rain", alignQueueSeed: Boolean = false,
        nextRequest: () -> Unit = {}, previousRequest: () -> Unit = {},
        removeRequest: (Int) -> Unit = {}, collapseRequest: () -> Unit = {}, playing: Boolean = true,
        jumpRequest: (Int) -> Unit = {}, moveRequest: (Int,Int) -> Unit = {_,_->},
        playRequest: () -> Unit = {}, seekRequest: (Long) -> Unit = {}, fractionRequest: (Float) -> Unit = {},
        fixturePosition: com.music.bitchord.playback.PlaybackPosition? = null,
        // Real artwork through the production Coil path; null keeps every
        // existing capture byte-identical (placeholder tile).
        artworkUrl: String? = null,
    ) {
        val position = androidx.compose.runtime.remember { com.music.bitchord.playback.PlaybackPosition() }
        com.music.bitchord.ui.player.NowPlayingScreen(
            song = com.music.bitchord.data.model.Song(
                videoId = "t1",
                title = titleText,
                artist = "Nova Rae",
                thumbnailUrl = artworkUrl,
                durationText = "3:48",
                isVideo = video,
            ),
            isPlaying = playing,
            isLoading = false,
            position = fixturePosition ?: position,
            durationMs = 228_000,
            audioVersionSwitching = false,
            qualityUpgraded = false,
            queue = detailSongs(6).mapIndexed { i, queued ->
                queued.copy(
                    title = if (alignQueueSeed && i == 0) titleText else queued.title,
                    artist = if (alignQueueSeed && i == 0) "Nova Rae" else queued.artist,
                    queueEntryId = "fixture-entry-$i",
                    queueTier = when {
                        i >= 4 -> com.music.bitchord.data.model.QueueTier.AUTOPLAY
                        i >= 2 -> com.music.bitchord.data.model.QueueTier.CONTEXT
                        else -> com.music.bitchord.data.model.QueueTier.USER_QUEUE
                    },
                )
            },
            queueIndex = 0,
            hasPrevious = false,
            hasNext = true,
            repeatMode = com.music.bitchord.ui.player.RepeatModes.OFF,
            shuffleEnabled = false,
            autoplayEnabled = true,
            signedIn = false,
            accountName = null,
            likeStatus = com.music.bitchord.data.model.LikeStatus.INDIFFERENT,
            onToggleLike = {},
            onPlayPause = playRequest,
            onNext = nextRequest,
            onPrevious = previousRequest,
            onBlockedControl = {},
            onSeek = seekRequest,
            onSeekFraction = fractionRequest,
            onToggleShuffle = {},
            onCycleRepeat = {},
            onToggleAutoplay = {},
            onJumpTo = jumpRequest,
            onRemoveFromQueue = removeRequest,
            onMoveInQueue = moveRequest,
            onClearQueue = {},
            onOpenMenu = {},
            onOpenAlbum = {},
            onOpenArtist = { _, _ -> },
            onOpenPlaybackSource = {},
            onCollapse = collapseRequest,
            onListenTogether = {},
            lyrics = if (emptyLyrics) emptyList() else listOf(
                com.music.bitchord.data.lyrics.LyricLine(0, "First line of the song"),
                com.music.bitchord.data.lyrics.LyricLine(10_000, "Second line follows"),
                com.music.bitchord.data.lyrics.LyricLine(20_000, "Third line carries on"),
            ),
            lyricsSource = com.music.bitchord.data.lyrics.LyricsSource.LRC_RED,
            lyricsProviderStates = emptyMap(),
            onSelectLyricsProvider = {},
            lyricsUnavailable = emptyLyrics,
            lyricsOffsetOpen = false,
            onDismissLyricsOffset = {},
            windowWidth = if (landscape) 780.dp else 360.dp,
            windowHeight = if (landscape) 360.dp else 780.dp,
        )
    }

    private fun paneShot(name: String, dark: Boolean, lyrics: Boolean, empty: Boolean = false, related: Boolean = false) {
        val restore = installVideoStubHost()
        val settings = com.music.bitchord.data.settings.AppSettings
        val oldTheme = settings.playerTheme.value
        val oldPane = settings.lastPlayerScreen.value
        settings.playerTheme.value = com.music.bitchord.data.settings.PlayerTheme.FLAT
        settings.lastPlayerScreen.value = when { related -> com.music.bitchord.data.settings.LastPlayerScreen.RELATED; lyrics -> com.music.bitchord.data.settings.LastPlayerScreen.LYRICS; else -> com.music.bitchord.data.settings.LastPlayerScreen.QUEUE }
        try { shot(name + "_reopens_main", dark) { PlayerShot(emptyLyrics = empty) } }
        finally {
            settings.playerTheme.value = oldTheme
            settings.lastPlayerScreen.value = oldPane
            com.music.bitchord.ui.player.PlayerPlatform.install(restore)
        }
    }

    @Composable
    private fun OverflowShot(extended: Boolean = false, audioVersion: Boolean = true) {
        com.music.bitchord.ui.components.SpoolPlayerOverflow(onDismiss = {}) {
            com.music.bitchord.ui.components.SongActionsMenu(
                song = detailSongs(1).first().copy(albumId = "fixture-album", albumName = "Fixture album", artistId = "fixture-artist"), signedIn = true,
                likeStatus = com.music.bitchord.data.model.LikeStatus.INDIFFERENT,
                onPlayNext = {}, onAddToQueue = {}, onStartRadio = {}, onDownload = {},
                onToggleLike = {}, onToggleDislike = {}, onAddToPlaylist = {},
                onOpenAlbum = {}, onOpenArtist = {},
                onShare = {}, onCopyLog = {}, onLyricsOffset = {}, showSleepTimer = true,
                onCast = {}, onAudioOutput = {},
                onAutomixOverview = if (extended) ({}) else null,
                onRemoveFromCache = if (extended) ({}) else null,
            )
        }
    }
    @Test fun settings_player_overflow_light() = shot("settings_player_overflow_light", false) { OverflowShot() }
    @Test fun settings_player_overflow_dark() = shot("settings_player_overflow_dark", true) { OverflowShot() }
    @Test fun settings_player_overflow_extended_dark() = shot("settings_player_overflow_extended_dark", true) { OverflowShot(extended = true) }
    @Test fun settings_player_overflow_video_light() = shot("settings_player_overflow_video_light", false) { OverflowShot(audioVersion = false) }

    private fun landscapeShot(name: String, dark: Boolean) {
        val original = org.robolectric.RuntimeEnvironment.getQualifiers()
        org.robolectric.RuntimeEnvironment.setQualifiers("+land")
        val restore = installVideoStubHost()
        try { shot(name, dark) { PlayerShot(landscape = true) } }
        finally {
            org.robolectric.RuntimeEnvironment.setQualifiers(original)
            com.music.bitchord.ui.player.PlayerPlatform.install(restore)
        }
    }
    @Test fun player_landscape_light() = landscapeShot("player_landscape_light", false)
    @Test fun player_landscape_dark() = landscapeShot("player_landscape_dark", true)
    private fun partyShot(name: String, dark: Boolean) {
        val restore = installVideoStubHost()
        val installed = com.music.bitchord.ui.player.PlayerPlatform.host
        com.music.bitchord.ui.player.PlayerPlatform.install(
            object : com.music.bitchord.ui.player.PlayerHost by installed {
                override val party: kotlinx.coroutines.flow.StateFlow<com.music.bitchord.ui.player.PartyUi> =
                    kotlinx.coroutines.flow.MutableStateFlow(com.music.bitchord.ui.player.PartyUi(inParty = true, controlsLocked = true))
            },
        )
        try { shot(name, dark) { PlayerShot(party = true) } }
        finally { com.music.bitchord.ui.player.PlayerPlatform.install(restore) }
    }
    @Test fun player_party_light() = partyShot("player_party_light", false)
    @Test fun player_party_dark() = partyShot("player_party_dark", true)
    @Test fun player_pane_related_light() = paneShot("player_pane_related_light", false, false, related = true)
    @Test fun player_pane_related_dark() = paneShot("player_pane_related_dark", true, false, related = true)
    @Test fun player_pane_queue_light() = paneShot("player_pane_queue_light", false, false)
    @Test fun player_pane_queue_dark() = paneShot("player_pane_queue_dark", true, false)
    @Test fun player_pane_lyrics_light() = paneShot("player_pane_lyrics_light", false, true)
    @Test fun player_pane_lyrics_dark() = paneShot("player_pane_lyrics_dark", true, true)
    @Test fun player_pane_empty_light() = paneShot("player_pane_empty_light", false, true, true)
    @Test fun player_pane_empty_dark() = paneShot("player_pane_empty_dark", true, true, true)

    @Test
    fun player_light() = shot("player_light", dark = false) { PlayerShot() }

    @Test
    fun player_dark() = shot("player_dark", dark = true) { PlayerShot() }

    // ---- The approved video-mode player: same fixture with a music video,
    // its picture stubbed (test-only) so the card renders filled rather than
    // spinning on a frame that never lands under Robolectric. ----

    // Returns the host to put back afterwards: PlayerPlatform's static
    // survives the test in this JVM, so every video capture must restore a
    // real host (the previously installed one, or a fresh Android host when
    // none was installed) or later captures become order-dependent.
    internal fun installVideoStubHost(): com.music.bitchord.ui.player.PlayerHost {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val real = com.music.bitchord.ui.player.AndroidPlayerHost(app)
        val previous = try {
            com.music.bitchord.ui.player.PlayerPlatform.host
        } catch (_: IllegalStateException) {
            null
        }
        com.music.bitchord.ui.player.PlayerPlatform.install(real)
        return previous ?: real
    }

    // Video-origin uploads now use the same audio-only screen as other tracks.
    // Cross app/system theme combinations keep the legacy fixtures useful for
    // checking that the chosen app theme wins. No picture renderer is mounted.
    private fun videoShot(name: String, appDark: Boolean, systemNight: Boolean) {
        val originalQualifiers = org.robolectric.RuntimeEnvironment.getQualifiers()
        org.robolectric.RuntimeEnvironment.setQualifiers(if (systemNight) "+night" else "+notnight")
        val restore = installVideoStubHost()
        try {
            shot(name, dark = appDark) { PlayerShot(video = true) }
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers(originalQualifiers)
            com.music.bitchord.ui.player.PlayerPlatform.install(restore)
        }
    }

    @Test
    fun player_video_light() = videoShot("player_video_light", appDark = false, systemNight = false)

    @Test
    fun player_video_dark() = videoShot("player_video_dark", appDark = true, systemNight = true)

    @Test
    fun player_video_sysdark_applight() =
        videoShot("player_video_sysdark_applight", appDark = false, systemNight = true)

    @Test
    fun player_video_syslight_appdark() =
        videoShot("player_video_syslight_appdark", appDark = true, systemNight = false)

    // ---- The approved (22:06) Spotify import dialog restyle. ----

    @Test
    fun import_dialog_light() = shot("import_dialog_light", dark = false) {
        com.music.bitchord.ui.components.SpotifyImportAlert(
            hazeState = dev.chrisbanes.haze.HazeState(),
            signedIn = false,
            onImported = { _, _, _ -> },
            onDismiss = {},
        )
    }

    @Test
    fun import_dialog_dark() = shot("import_dialog_dark", dark = true) {
        com.music.bitchord.ui.components.SpotifyImportAlert(
            hazeState = dev.chrisbanes.haze.HazeState(),
            signedIn = false,
            onImported = { _, _, _ -> },
            onDismiss = {},
        )
    }

    // Signed in adds the "Who can see it" privacy row.
    @Test
    fun import_dialog_signed_in_dark() = shot("import_dialog_signed_in_dark", dark = true) {
        com.music.bitchord.ui.components.SpotifyImportAlert(
            hazeState = dev.chrisbanes.haze.HazeState(),
            signedIn = true,
            onImported = { _, _, _ -> },
            onDismiss = {},
        )
    }

    // Mid-import: dimmed chips, disabled Import, working status line.
    @Test
    fun import_dialog_working() = shot("import_dialog_working", dark = true) {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.music.bitchord.ui.components.SpotifyImportAlert(
            hazeState = dev.chrisbanes.haze.HazeState(),
            signedIn = false,
            onImported = { _, _, _ -> },
            onDismiss = {},
            initialLink = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            initialWorking = true,
            initialStatus = app.getString(com.music.bitchord.R.string.spotify_import_fetching),
        )
    }

    // A rejected link: the status line turns into the error.
    @Test
    fun import_dialog_error() = shot("import_dialog_error", dark = true) {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.music.bitchord.ui.components.SpotifyImportAlert(
            hazeState = dev.chrisbanes.haze.HazeState(),
            signedIn = false,
            onImported = { _, _, _ -> },
            onDismiss = {},
            initialLink = "not a playlist",
            initialFailed = true,
            initialStatus = app.getString(com.music.bitchord.R.string.spotify_import_invalid),
        )
    }

    // After a keep: the songs nothing matched, listed instead of dropped.
    @Test
    fun import_dialog_unmatched() = shot("import_dialog_unmatched", dark = true) {
        com.music.bitchord.ui.components.SpotifyImportAlert(
            hazeState = dev.chrisbanes.haze.HazeState(),
            signedIn = false,
            onImported = { _, _, _ -> },
            onDismiss = {},
            initialUnmatched = listOf(
                com.music.bitchord.data.spotify.SpotifyImportTrack("Ennodu Nee Irundhaal", "Sid Sriram"),
                com.music.bitchord.data.spotify.SpotifyImportTrack("Kaatru Veliyidai", "A. R. Rahman"),
                com.music.bitchord.data.spotify.SpotifyImportTrack("Munbe Vaa", "Shreya Ghoshal"),
            ),
        )
    }

    // Manual / Other swaps the chips for the custom-language field.
    @Test
    fun import_dialog_manual_other() = shot("import_dialog_manual_other", dark = true) {
        com.music.bitchord.ui.components.SpotifyImportAlert(
            hazeState = dev.chrisbanes.haze.HazeState(),
            signedIn = false,
            onImported = { _, _, _ -> },
            onDismiss = {},
            initialLanguage = "Manual / Other",
        )
    }

    // ---- The REAL Listen Together landing (no party, signed in). ----

    @Test
    fun party_landing_light() = shot("party_landing_light", dark = false) {
        com.music.bitchord.ui.screens.ListenTogetherScreen(
            signedIn = true,
            onSignIn = {},
            contentPadding = PaddingValues(0.dp),
            onEditServer = {},
        )
    }

    @Test
    fun party_landing_dark() = shot("party_landing_dark", dark = true) {
        com.music.bitchord.ui.screens.ListenTogetherScreen(
            signedIn = true,
            onSignIn = {},
            contentPadding = PaddingValues(0.dp),
            onEditServer = {},
        )
    }

    // ---- The REAL Settings main page (search + category groups). ----

    @Composable
    internal fun SettingsShot(section: com.music.bitchord.ui.screens.SettingsCategory? = null) {
        com.music.bitchord.ui.screens.SettingsScreen(
            windowWidth = 360.dp,
            signedIn = false,
            account = null,
            onSignIn = {},
            onSignOut = {},
            onAccountScrobbling = {},
            onEqualizer = {},
            onOpenReplay = {},
            onLyricsSources = {},
            onTranslationLanguage = {},
            onSources = {},
            onListenTogether = {},
            onWearOs = {},
            onSpotifyCanvasAuth = {},
            onAppLanguage = {},
            onEditSignatureApi = {},
            onConfirmBitPerfect = {},
            onConfirmFixedVolume = {},
            contentPadding = PaddingValues(0.dp),
            section = section,
        )
    }

    @Test
    fun settings_main_light() = shot("settings_main_light", dark = false) { SettingsShot() }

    @Test
    fun settings_main_dark() = shot("settings_main_dark", dark = true) { SettingsShot() }
    @Test
    fun settings_downloads_light() = shot("settings_downloads_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.DOWNLOADS) }

    @Test
    fun settings_downloads_dark() = shot("settings_downloads_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.DOWNLOADS) }

    @Test
    fun settings_playback_light() = shot("settings_playback_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.PLAYBACK) }

    @Test
    fun settings_playback_dark() = shot("settings_playback_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.PLAYBACK) }

    @Test
    fun settings_content_preferences_light() = shot("settings_content_preferences_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.CONTENT_PREFERENCES) }

    @Test
    fun settings_content_preferences_dark() = shot("settings_content_preferences_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.CONTENT_PREFERENCES) }

    @Test
    fun settings_performance_light() = shot("settings_performance_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.PERFORMANCE) }

    @Test
    fun settings_performance_dark() = shot("settings_performance_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.PERFORMANCE) }

    @Test
    fun settings_local_music_light() = shot("settings_local_music_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.LOCAL_MUSIC) }

    @Test
    fun settings_local_music_dark() = shot("settings_local_music_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.LOCAL_MUSIC) }


    @Test
    fun settings_storage_light() = shot("settings_storage_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.STORAGE) }

    @Test
    fun settings_storage_dark() = shot("settings_storage_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.STORAGE) }

    @Test
    fun settings_your_data_light() = shot("settings_your_data_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.YOUR_DATA) }

    @Test
    fun settings_your_data_dark() = shot("settings_your_data_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.YOUR_DATA) }


    @Test
    fun settings_miscellaneous_light() = shot("settings_miscellaneous_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.MISCELLANEOUS) }

    @Test
    fun settings_miscellaneous_dark() = shot("settings_miscellaneous_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.MISCELLANEOUS) }

    @Test
    fun settings_language_light() = shot("settings_language_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.LANGUAGE) }

    @Test
    fun settings_language_dark() = shot("settings_language_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.LANGUAGE) }


    @Test
    fun settings_appearance_light() = shot("settings_appearance_light", dark = false) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.APPEARANCE) }

    @Test
    fun settings_appearance_dark() = shot("settings_appearance_dark", dark = true) { SettingsShot(com.music.bitchord.ui.screens.SettingsCategory.APPEARANCE) }

    @Test
    fun settings_has_no_advanced_visuals_category() {
        // The page was removed with the flat-only direction: nothing in the
        // settings root may route to it, and the enum itself is gone. This
        // fails to compile if the category is ever reintroduced.
        val titles = com.music.bitchord.ui.screens.SettingsCategory.entries.map { it.name }
        org.junit.Assert.assertFalse(titles.contains("ADVANCED_VISUALS"))
    }


    @Test
    fun settings_language_dialog_light() = shot("settings_language_dialog_light", dark = false) { com.music.bitchord.ui.components.AppLanguageDialog(hazeState = remember { dev.chrisbanes.haze.HazeState() }, onDismiss = {}) }

    @Test
    fun settings_language_dialog_dark() = shot("settings_language_dialog_dark", dark = true) { com.music.bitchord.ui.components.AppLanguageDialog(hazeState = remember { dev.chrisbanes.haze.HazeState() }, onDismiss = {}) }

    @Test
    fun settings_wear_os_light() = shot("settings_wear_os_light", dark = false) { com.music.bitchord.ui.screens.WearOsScreen(contentPadding = PaddingValues(0.dp)) }

    @Test
    fun settings_wear_os_dark() = shot("settings_wear_os_dark", dark = true) { com.music.bitchord.ui.screens.WearOsScreen(contentPadding = PaddingValues(0.dp)) }


    @Test
    fun settings_account_signed_out_light() = shot("settings_account_signed_out_light", dark = false) { com.music.bitchord.ui.screens.AccountAndScrobblingScreen(signedIn = false, account = null, channelName = null, onSignIn = {}, onSwitchChannel = {}, onSignOut = {}, onOpenListenBrainzLogin = {}, onOpenLastfmLogin = {}, onOpenDiscord = {}, onOpenSpotify = {}, contentPadding = PaddingValues(0.dp)) }

    @Test
    fun settings_account_signed_out_dark() = shot("settings_account_signed_out_dark", dark = true) { com.music.bitchord.ui.screens.AccountAndScrobblingScreen(signedIn = false, account = null, channelName = null, onSignIn = {}, onSwitchChannel = {}, onSignOut = {}, onOpenListenBrainzLogin = {}, onOpenLastfmLogin = {}, onOpenDiscord = {}, onOpenSpotify = {}, contentPadding = PaddingValues(0.dp)) }

    @Test
    fun settings_sources_light() = shot("settings_sources_light", dark = false) { com.music.bitchord.ui.screens.SourcesScreen(contentPadding = PaddingValues(0.dp), onEditSource = {}, onChooseSourceQuality = {}, onExplainOutdatedAddon = {}, onEditWebDav = {}, onEditSmb = {}, onConfirmJioSaavn = {}) }

    @Test
    fun settings_sources_dark() = shot("settings_sources_dark", dark = true) { com.music.bitchord.ui.screens.SourcesScreen(contentPadding = PaddingValues(0.dp), onEditSource = {}, onChooseSourceQuality = {}, onExplainOutdatedAddon = {}, onEditWebDav = {}, onEditSmb = {}, onConfirmJioSaavn = {}) }

    @Test
    fun settings_listen_signed_out_light() = shot("settings_listen_signed_out_light", dark = false) { com.music.bitchord.ui.screens.ListenTogetherScreen(signedIn = false, onSignIn = {}, contentPadding = PaddingValues(0.dp), onEditServer = {}) }

    @Test
    fun settings_listen_signed_out_dark() = shot("settings_listen_signed_out_dark", dark = true) { com.music.bitchord.ui.screens.ListenTogetherScreen(signedIn = false, onSignIn = {}, contentPadding = PaddingValues(0.dp), onEditServer = {}) }

    @Test
    fun settings_listen_create_light() = shot("settings_listen_create_light", dark = false) { com.music.bitchord.ui.screens.SpoolSettingsSurface { com.music.bitchord.ui.screens.CreatePartySheet(avatarUrl = null, nickname = "Listener", onNicknameChange = {}, maxMembers = 5, onMaxMembersChange = {}, busy = false, enabled = true, error = null, onCreate = {}) } }

    @Test
    fun settings_listen_create_dark() = shot("settings_listen_create_dark", dark = true) { com.music.bitchord.ui.screens.SpoolSettingsSurface { com.music.bitchord.ui.screens.CreatePartySheet(avatarUrl = null, nickname = "Listener", onNicknameChange = {}, maxMembers = 5, onMaxMembersChange = {}, busy = false, enabled = true, error = null, onCreate = {}) } }

    @Test
    fun settings_listen_join_light() = shot("settings_listen_join_light", dark = false) { com.music.bitchord.ui.screens.SpoolSettingsSurface { com.music.bitchord.ui.screens.JoinPartySheet(code = "", onCodeChange = {}, busy = false, enabled = true, error = null, onSubmit = {}) } }

    @Test
    fun settings_listen_join_dark() = shot("settings_listen_join_dark", dark = true) { com.music.bitchord.ui.screens.SpoolSettingsSurface { com.music.bitchord.ui.screens.JoinPartySheet(code = "", onCodeChange = {}, busy = false, enabled = true, error = null, onSubmit = {}) } }



    @Test fun signin_success_dark() = shot("signin_success_dark", dark = true) { com.music.bitchord.ui.components.SignInSuccessPanel("Listener", "listener@example.invalid", {}) }

    @Test fun signin_failure_dark() = shot("signin_failure_dark", dark = true) { com.music.bitchord.ui.components.SignInFailedPanel({}, {}) }

    @Test fun signin_choice_dark() = shot("signin_choice_dark", dark = true) { com.music.bitchord.ui.components.SignInProfileChoicePanel(channels = listOf(
                com.music.bitchord.data.model.AccountChannel("Personal", "@listener", null, null, "primary", true),
                com.music.bitchord.data.model.AccountChannel("Music channel", "@music", null, "channel", "sync", false)),
                selectedKey = null, onSelect = {}, onContinue = {}) }

    @Test fun signin_success_light() = shot("signin_success_light", dark = false) { com.music.bitchord.ui.components.SignInSuccessPanel("Listener", "listener@example.invalid", {}) }

    @Test fun signin_failure_light() = shot("signin_failure_light", dark = false) { com.music.bitchord.ui.components.SignInFailedPanel({}, {}) }

    @Test fun signin_choice_light() = shot("signin_choice_light", dark = false) { com.music.bitchord.ui.components.SignInProfileChoicePanel(channels = listOf(
                com.music.bitchord.data.model.AccountChannel("Personal", "@listener", null, null, "primary", true),
                com.music.bitchord.data.model.AccountChannel("Music channel", "@music", null, "channel", "sync", false)),
                selectedKey = null, onSelect = {}, onContinue = {}) }
    @Test fun choice_audio_quality_wifi_dark() = shot("choice_audio_quality_wifi_dark", dark = true) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.AUDIO_QUALITY_WIFI, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_audio_quality_wifi_light() = shot("choice_audio_quality_wifi_light", dark = false) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.AUDIO_QUALITY_WIFI, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_audio_quality_cellular_dark() = shot("choice_audio_quality_cellular_dark", dark = true) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.AUDIO_QUALITY_CELLULAR, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_audio_quality_cellular_light() = shot("choice_audio_quality_cellular_light", dark = false) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.AUDIO_QUALITY_CELLULAR, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_download_quality_dark() = shot("choice_download_quality_dark", dark = true) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.DOWNLOAD_QUALITY, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_download_quality_light() = shot("choice_download_quality_light", dark = false) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.DOWNLOAD_QUALITY, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_automix_performance_dark() = shot("choice_automix_performance_dark", dark = true) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.AUTOMIX_PERFORMANCE, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_automix_performance_light() = shot("choice_automix_performance_light", dark = false) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.AUTOMIX_PERFORMANCE, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_bit_perfect_dark() = shot("choice_bit_perfect_dark", dark = true) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.BIT_PERFECT, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_bit_perfect_light() = shot("choice_bit_perfect_light", dark = false) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.BIT_PERFECT, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_font_dark() = shot("choice_font_dark", dark = true) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.FONT, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }

    @Test fun choice_font_light() = shot("choice_font_light", dark = false) { com.music.bitchord.ui.screens.SettingsChoicePage(
        choice = com.music.bitchord.ui.screens.SettingsChoiceKind.FONT, onChosen = {},
        onConfirmBitPerfect = {}, contentPadding = PaddingValues(0.dp)) }


}
