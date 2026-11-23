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
import org.junit.Rule
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.width
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LibrarySpacingProofTest {
 @get:Rule val compose=createComposeRule()
 private fun shot(dark:Boolean) {
  System.setProperty("roborazzi.test.record","true")
  V6ScreenshotTest().installUiHost()
  compose.setContent { BitChordTheme(darkTheme=dark) { LibrarySpacingFixture() } }
  compose.waitForIdle()
  compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/library_spacing_${if(dark) "dark" else "light"}.png")
 }
 @Test fun library_light()=shot(false)
 @Test fun library_dark()=shot(true)
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    internal fun LibrarySpacingFixture(rootPadding: PaddingValues = PaddingValues(0.dp), chrome: Boolean = true, floatingHeader: Boolean = false) {
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
                replay = {
                    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal=16.dp,vertical=6.dp)) {
                        com.music.bitchord.ui.replay.ReplayCreditCard(label="MINUTES LISTENED",value="16",detail="3 plays · 2026",artworkUrl=null,holder="JOYAL",memberSince="11/26",onClick={},modifier=Modifier.width(300.dp))
                    }
                },
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
                deviceItems = emptyList(),
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
}
