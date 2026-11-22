@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.*
import com.music.bitchord.data.model.*
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.screens.*
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production-composable fixtures. Not a claim of full navigation, auth or service acceptance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w390dp-h844dp-mdpi")
abstract class CandidatePageMatrixBase {
 @get:Rule val compose=createComposeRule()
 protected fun shot(page:String,dark:Boolean,material:Boolean,narrow:Boolean=false) {
  val fixture=V6ScreenshotTest();fixture.installUiHost()
  val palette=if(material)PaletteMode.MATERIAL_YOU else PaletteMode.ASTRYX
  val oldPalette=AppSettings.paletteMode.value
  val oldTheme=AppSettings.themeMode.value
  AppSettings.setPaletteMode(palette)
  AppSettings.setThemeMode(if(dark)ThemeMode.DARK else ThemeMode.LIGHT)
  try {
   compose.setContent { BitChordTheme(darkTheme=dark,palette=palette) {
    val settingsPage=page.startsWith("Settings")||page.startsWith("Choice_")||page in listOf("Spotify","Discord","Equalizer","Presets","Profile","SwitchProfile","ManageAccounts","AboutDeveloper","Credits","License","AppUpdates","WearOs","Sources","ListenTogether","AccountScrobbling")
    var headerHeight by remember { mutableIntStateOf(0) }
    val padding=PaddingValues(top=if(page in listOf("Listen","Explore","Library","Search"))32.dp else maxOf(92.dp,headerHeight.dp+12.dp),bottom=150.dp)
    Box(Modifier.width(if(narrow)320.dp else 390.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background).testTag("candidate-page")) {
     when {
      page=="Listen" -> fixture.HomeWithChrome(padding,floatingHeader=true)
      page=="Explore" -> fixture.ExploreWithChrome(padding,floatingHeader=true)
      page=="Library" -> fixture.LibraryWithChrome(padding,floatingHeader=true)
      page=="Search" -> fixture.SearchWithChrome(padding,floatingHeader=true)
      page=="Profile" -> ProfileSettingsPage(false,null,null,null,{},{},{},padding)
      page=="SwitchProfile" -> AccountSettingsPage(false,emptyList(),null,null,{_,_->},{},{},padding)
      page=="ManageAccounts" -> AccountSettingsPage(true,emptyList(),null,null,{_,_->},{},{},padding)
      page=="AboutDeveloper" -> AboutDeveloperPage(padding,{},{})
      page=="Credits" -> CreditsLicensesPage(padding,{})
      page=="License" -> LicenseNoticePage("GPL-3.0.txt",padding)
      page=="WearOs" -> WearOsScreen(padding)
      page=="Sources" -> SourcesScreen(padding,{},{},{},{},{},{})
      page=="ListenTogether" -> ListenTogetherScreen(signedIn=false,onSignIn={},contentPadding=padding,onEditServer={})
      page=="AppUpdates" -> AppUpdatesContent(padding,com.music.bitchord.data.AppUpdateChecker.CheckState.UpToDate,null,com.music.bitchord.data.AppUpdateChecker.DownloadState.Idle,false,0L,false,{},{},{},{},{})
      page=="AccountScrobbling" -> AccountAndScrobblingScreen(signedIn=false,account=null,channelName=null,onSignIn={},onSwitchChannel={},onSignOut={},onOpenListenBrainzLogin={},onOpenLastfmLogin={},onOpenDiscord={},onOpenSpotify={},contentPadding=padding,onProfileClick={})
      page=="LocalMusic" -> fixture.LocalMusicShot(padding)
      page=="MoodCategory" -> MoodGenrePlaylistsScreen("Focus",UiState.Success(listOf(HomeShelf("Playlists",listOf(ShelfItem("Quiet focus","Playlist",null,null,"fixture:focus"))))),rememberLazyListState(),{},{},padding)
      page=="Replay" -> com.music.bitchord.ui.replay.ReplayScreen(com.music.bitchord.ui.replay.ReplayState(com.music.bitchord.data.stats.ReplayPeriod.THIS_YEAR,ReplayAuditScreenshotTest().summary,false,"01/26"),"Fixture listener",{},{},{},{_,_->},{_,_,_,_->},{},padding)
      page=="History" -> HistoryScreen(UiState.Success(List(12){Song("history:$it","Fixture track ${it+1}","Fixture artist",null)}),rememberLazyListState(),{_,_->},{},{},{},padding)
      page=="Downloads" -> DownloadManagerPage({},padding)
      page=="Moods" -> MoodGridPage(rememberLazyGridState(),{_,_->},padding)
      page=="LibraryGrid" -> LibraryGridPage(HomeShelf("Playlists",emptyList()),rememberLazyGridState(),{},{},padding,onNewPlaylist={},scrollingTitle=true)
      page=="Spotify" -> SpotifyLibraryScreen({},{},{},padding)
      page=="Discord" -> DiscordScreen(null,0L,0L,{},{},padding)
      page=="Equalizer" -> EqualizerScreen(padding)
      page=="Presets" -> EqualizerPresetPage({},padding)
      page=="Settings" -> SettingsScrollScreenshotTest().SettingsShot(padding=padding)
      page.startsWith("Settings_") -> SettingsScrollScreenshotTest().SettingsShot(SettingsCategory.valueOf(page.removePrefix("Settings_")),padding)
      page.startsWith("Choice_") -> SettingsChoicePage(SettingsChoiceKind.valueOf(page.removePrefix("Choice_")),{},{},padding)
      page=="Album" -> fixture.DetailShot(fixture.albumPage(),padding)
      page=="Playlist" -> fixture.DetailShot(fixture.playlistPage(),padding)
      page=="Artist" -> fixture.DetailShot(fixture.artistPage(),padding)
      page=="ArtistShelf" -> fixture.DetailShot(fixture.artistPage(),padding,HomeShelf("Albums",listOf(ShelfItem("Fixture album","Album",null,null,"fixture:album"))))
     }
     // The production controls-only component, same navigation-free fixture callbacks.
     // Confirmed Settings-family exception: no history/profile capsule on those pages.
     if(!settingsPage) FrostedTopBar(
      title="No fixed title",controlsOnly=true,onRootHeaderMeasured={headerHeight=it},statusBarInsets=WindowInsets(top=32.dp),scrolled={false},
      onBack=if(page in listOf("Listen","Explore","Library","Search"))null else ({}),
      actions={
       if(page in listOf("Album","Playlist","LocalMusic")) {
        HeaderActionButton({}){HeaderIcon(Icons.Rounded.Search,"Search this list")}
        HeaderActionButton({}){HeaderIcon(Icons.Rounded.Sort,"Sort songs")}
        HeaderActionButton({}){HeaderIcon(Icons.Rounded.MoreHoriz,"More")}
       }
       if(page=="Artist")HeaderActionButton({}){HeaderIcon(Icons.Rounded.IosShare,"Share")}
       if(page=="LibraryGrid")HeaderActionButton({}){HeaderIcon(Icons.Rounded.Sort,"Sort library")}
       FloatingHistoryProfileCapsule(null,{},{})})
     else FrostedTopBar(title="",onBack={},statusBarInsets=WindowInsets(top=32.dp),scrolled={false},transparentBackdrop=true,artworkPageChrome=true,settingsHomeChrome=true,actions={})
    }
   } }
   compose.waitForIdle()
   compose.onNodeWithText("No fixed title").assertDoesNotExist()
   val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
   val title = when {
    page.startsWith("Settings_") -> context.getString(SettingsCategory.valueOf(page.removePrefix("Settings_")).title)
    page.startsWith("Choice_") -> context.getString(SettingsChoiceKind.valueOf(page.removePrefix("Choice_")).title)
    else -> when(page) {
     "Listen" -> "Listen"; "Explore" -> "Explore"; "Library" -> "Library"
     "Search" -> "Songs, artists, albums"; "Album" -> "Afterglow"; "Playlist" -> "Rainy Day Drive"
     "Artist" -> "Nova Rae"; "ArtistShelf" -> "Albums"; "LocalMusic" -> "Local Music"
     "MoodCategory" -> "Focus"; "LibraryGrid" -> "Playlists"; "License" -> "GNU GPL v3"
     else -> context.getString(when(page) {
      "Settings" -> R.string.settings; "Profile" -> R.string.acct_profile_header
      "SwitchProfile" -> R.string.acct_switch_profile; "ManageAccounts" -> R.string.manage_accounts
      "AboutDeveloper" -> R.string.about_developer_title; "Credits" -> R.string.about_credits
      "WearOs" -> R.string.wear_os_page_title; "Sources" -> R.string.source
      "ListenTogether" -> R.string.listen_together; "AppUpdates" -> R.string.app_updates
      "AccountScrobbling" -> R.string.account_integrations; "Replay" -> R.string.replay
      "History" -> R.string.history; "Downloads" -> R.string.downloads
      "Moods" -> R.string.find_your_mood; "Spotify" -> R.string.spotify; "Discord" -> R.string.discord
      "Equalizer" -> R.string.equalizer; "Presets" -> R.string.equalizer_preset
      else -> error("No identity assertion for $page")
     })
    }
   }
   val identities = compose.onAllNodesWithText(title).fetchSemanticsNodes()
   assertTrue("$page must render its own identity '$title'", identities.isNotEmpty())
   assertTrue("$page identity must clear the fixture status inset",
    identities.any { it.boundsInRoot.top >= 31f && it.boundsInRoot.height > 0f })

   val name="candidate_${page}_${palette.name}_${if(dark)"dark" else "light"}_${if(narrow)"narrow" else "normal"}"
   compose.onNodeWithTag("candidate-page").captureRoboImage("build/outputs/roborazzi/$name.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
   if(page in listOf("Listen","Explore","Library","Search","History")) {
    compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
    compose.waitForIdle()
    compose.onNodeWithTag("floating-history-profile").assertIsDisplayed()
    compose.onNodeWithTag("candidate-page").captureRoboImage("build/outputs/roborazzi/${name}_scrolled.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
   }
  } finally {
   AppSettings.setPaletteMode(oldPalette);AppSettings.setThemeMode(oldTheme)
  }
 }
}
class CandidatePageMatrix_Listen_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Listen",true,false)}
class CandidatePageMatrix_Listen_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Listen",true,true)}
class CandidatePageMatrix_Listen_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Listen",false,false)}
class CandidatePageMatrix_Listen_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Listen",false,true)}
class CandidatePageMatrix_Explore_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Explore",true,false)}
class CandidatePageMatrix_Explore_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Explore",true,true)}
class CandidatePageMatrix_Explore_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Explore",false,false)}
class CandidatePageMatrix_Explore_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Explore",false,true)}
class CandidatePageMatrix_Library_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Library",true,false)}
class CandidatePageMatrix_Library_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Library",true,true)}
class CandidatePageMatrix_Library_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Library",false,false)}
class CandidatePageMatrix_Library_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Library",false,true)}
class CandidatePageMatrix_Search_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Search",true,false)}
class CandidatePageMatrix_Search_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Search",true,true)}
class CandidatePageMatrix_Search_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Search",false,false)}
class CandidatePageMatrix_Search_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Search",false,true)}
class CandidatePageMatrix_History_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("History",true,false)}
class CandidatePageMatrix_History_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("History",true,true)}
class CandidatePageMatrix_History_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("History",false,false)}
class CandidatePageMatrix_History_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("History",false,true)}
class CandidatePageMatrix_Downloads_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Downloads",true,false)}
class CandidatePageMatrix_Downloads_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Downloads",true,true)}
class CandidatePageMatrix_Downloads_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Downloads",false,false)}
class CandidatePageMatrix_Downloads_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Downloads",false,true)}
class CandidatePageMatrix_Moods_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Moods",true,false)}
class CandidatePageMatrix_Moods_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Moods",true,true)}
class CandidatePageMatrix_Moods_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Moods",false,false)}
class CandidatePageMatrix_Moods_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Moods",false,true)}
class CandidatePageMatrix_LibraryGrid_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("LibraryGrid",true,false)}
class CandidatePageMatrix_LibraryGrid_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("LibraryGrid",true,true)}
class CandidatePageMatrix_LibraryGrid_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("LibraryGrid",false,false)}
class CandidatePageMatrix_LibraryGrid_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("LibraryGrid",false,true)}
class CandidatePageMatrix_Spotify_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Spotify",true,false)}
class CandidatePageMatrix_Spotify_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Spotify",true,true)}
class CandidatePageMatrix_Spotify_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Spotify",false,false)}
class CandidatePageMatrix_Spotify_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Spotify",false,true)}
class CandidatePageMatrix_Discord_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Discord",true,false)}
class CandidatePageMatrix_Discord_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Discord",true,true)}
class CandidatePageMatrix_Discord_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Discord",false,false)}
class CandidatePageMatrix_Discord_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Discord",false,true)}
class CandidatePageMatrix_Equalizer_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Equalizer",true,false)}
class CandidatePageMatrix_Equalizer_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Equalizer",true,true)}
class CandidatePageMatrix_Equalizer_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Equalizer",false,false)}
class CandidatePageMatrix_Equalizer_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Equalizer",false,true)}
class CandidatePageMatrix_Presets_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Presets",true,false)}
class CandidatePageMatrix_Presets_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Presets",true,true)}
class CandidatePageMatrix_Presets_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Presets",false,false)}
class CandidatePageMatrix_Presets_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Presets",false,true)}
class CandidatePageMatrix_Settings_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings",true,false)}
class CandidatePageMatrix_Settings_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings",true,true)}
class CandidatePageMatrix_Settings_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings",false,false)}
class CandidatePageMatrix_Settings_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings",false,true)}
class CandidatePageMatrix_Album_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Album",true,false)}
class CandidatePageMatrix_Album_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Album",true,true)}
class CandidatePageMatrix_Album_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Album",false,false)}
class CandidatePageMatrix_Album_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Album",false,true)}
class CandidatePageMatrix_Playlist_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Playlist",true,false)}
class CandidatePageMatrix_Playlist_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Playlist",true,true)}
class CandidatePageMatrix_Playlist_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Playlist",false,false)}
class CandidatePageMatrix_Playlist_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Playlist",false,true)}
class CandidatePageMatrix_Artist_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Artist",true,false)}
class CandidatePageMatrix_Artist_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Artist",true,true)}
class CandidatePageMatrix_Artist_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Artist",false,false)}
class CandidatePageMatrix_Artist_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Artist",false,true)}
class CandidatePageMatrix_Settings_DOWNLOADS_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_DOWNLOADS",true,false)}
class CandidatePageMatrix_Settings_DOWNLOADS_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_DOWNLOADS",true,true)}
class CandidatePageMatrix_Settings_DOWNLOADS_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_DOWNLOADS",false,false)}
class CandidatePageMatrix_Settings_DOWNLOADS_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_DOWNLOADS",false,true)}
class CandidatePageMatrix_Settings_PLAYBACK_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_PLAYBACK",true,false)}
class CandidatePageMatrix_Settings_PLAYBACK_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_PLAYBACK",true,true)}
class CandidatePageMatrix_Settings_PLAYBACK_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_PLAYBACK",false,false)}
class CandidatePageMatrix_Settings_PLAYBACK_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_PLAYBACK",false,true)}
class CandidatePageMatrix_Settings_CONTENT_PREFERENCES_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_CONTENT_PREFERENCES",true,false)}
class CandidatePageMatrix_Settings_CONTENT_PREFERENCES_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_CONTENT_PREFERENCES",true,true)}
class CandidatePageMatrix_Settings_CONTENT_PREFERENCES_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_CONTENT_PREFERENCES",false,false)}
class CandidatePageMatrix_Settings_CONTENT_PREFERENCES_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_CONTENT_PREFERENCES",false,true)}
class CandidatePageMatrix_Settings_APPEARANCE_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_APPEARANCE",true,false)}
class CandidatePageMatrix_Settings_APPEARANCE_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_APPEARANCE",true,true)}
class CandidatePageMatrix_Settings_APPEARANCE_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_APPEARANCE",false,false)}
class CandidatePageMatrix_Settings_APPEARANCE_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_APPEARANCE",false,true)}
class CandidatePageMatrix_Settings_PERFORMANCE_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_PERFORMANCE",true,false)}
class CandidatePageMatrix_Settings_PERFORMANCE_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_PERFORMANCE",true,true)}
class CandidatePageMatrix_Settings_PERFORMANCE_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_PERFORMANCE",false,false)}
class CandidatePageMatrix_Settings_PERFORMANCE_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_PERFORMANCE",false,true)}
class CandidatePageMatrix_Settings_LOCAL_MUSIC_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_LOCAL_MUSIC",true,false)}
class CandidatePageMatrix_Settings_LOCAL_MUSIC_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_LOCAL_MUSIC",true,true)}
class CandidatePageMatrix_Settings_LOCAL_MUSIC_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_LOCAL_MUSIC",false,false)}
class CandidatePageMatrix_Settings_LOCAL_MUSIC_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_LOCAL_MUSIC",false,true)}
class CandidatePageMatrix_Settings_STORAGE_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_STORAGE",true,false)}
class CandidatePageMatrix_Settings_STORAGE_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_STORAGE",true,true)}
class CandidatePageMatrix_Settings_STORAGE_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_STORAGE",false,false)}
class CandidatePageMatrix_Settings_STORAGE_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_STORAGE",false,true)}
class CandidatePageMatrix_Settings_YOUR_DATA_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_YOUR_DATA",true,false)}
class CandidatePageMatrix_Settings_YOUR_DATA_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_YOUR_DATA",true,true)}
class CandidatePageMatrix_Settings_YOUR_DATA_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_YOUR_DATA",false,false)}
class CandidatePageMatrix_Settings_YOUR_DATA_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_YOUR_DATA",false,true)}
class CandidatePageMatrix_Settings_MISCELLANEOUS_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_MISCELLANEOUS",true,false)}
class CandidatePageMatrix_Settings_MISCELLANEOUS_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_MISCELLANEOUS",true,true)}
class CandidatePageMatrix_Settings_MISCELLANEOUS_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_MISCELLANEOUS",false,false)}
class CandidatePageMatrix_Settings_MISCELLANEOUS_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_MISCELLANEOUS",false,true)}
class CandidatePageMatrix_Settings_LANGUAGE_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_LANGUAGE",true,false)}
class CandidatePageMatrix_Settings_LANGUAGE_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_LANGUAGE",true,true)}
class CandidatePageMatrix_Settings_LANGUAGE_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_LANGUAGE",false,false)}
class CandidatePageMatrix_Settings_LANGUAGE_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Settings_LANGUAGE",false,true)}
class CandidatePageMatrix_Choice_AUDIO_QUALITY_WIFI_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUDIO_QUALITY_WIFI",true,false)}
class CandidatePageMatrix_Choice_AUDIO_QUALITY_WIFI_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUDIO_QUALITY_WIFI",true,true)}
class CandidatePageMatrix_Choice_AUDIO_QUALITY_WIFI_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUDIO_QUALITY_WIFI",false,false)}
class CandidatePageMatrix_Choice_AUDIO_QUALITY_WIFI_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUDIO_QUALITY_WIFI",false,true)}
class CandidatePageMatrix_Choice_AUDIO_QUALITY_CELLULAR_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUDIO_QUALITY_CELLULAR",true,false)}
class CandidatePageMatrix_Choice_AUDIO_QUALITY_CELLULAR_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUDIO_QUALITY_CELLULAR",true,true)}
class CandidatePageMatrix_Choice_AUDIO_QUALITY_CELLULAR_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUDIO_QUALITY_CELLULAR",false,false)}
class CandidatePageMatrix_Choice_AUDIO_QUALITY_CELLULAR_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUDIO_QUALITY_CELLULAR",false,true)}
class CandidatePageMatrix_Choice_DOWNLOAD_QUALITY_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_DOWNLOAD_QUALITY",true,false)}
class CandidatePageMatrix_Choice_DOWNLOAD_QUALITY_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_DOWNLOAD_QUALITY",true,true)}
class CandidatePageMatrix_Choice_DOWNLOAD_QUALITY_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_DOWNLOAD_QUALITY",false,false)}
class CandidatePageMatrix_Choice_DOWNLOAD_QUALITY_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_DOWNLOAD_QUALITY",false,true)}
class CandidatePageMatrix_Choice_AUTOMIX_PERFORMANCE_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUTOMIX_PERFORMANCE",true,false)}
class CandidatePageMatrix_Choice_AUTOMIX_PERFORMANCE_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUTOMIX_PERFORMANCE",true,true)}
class CandidatePageMatrix_Choice_AUTOMIX_PERFORMANCE_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUTOMIX_PERFORMANCE",false,false)}
class CandidatePageMatrix_Choice_AUTOMIX_PERFORMANCE_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_AUTOMIX_PERFORMANCE",false,true)}
class CandidatePageMatrix_Choice_BIT_PERFECT_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_BIT_PERFECT",true,false)}
class CandidatePageMatrix_Choice_BIT_PERFECT_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_BIT_PERFECT",true,true)}
class CandidatePageMatrix_Choice_BIT_PERFECT_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_BIT_PERFECT",false,false)}
class CandidatePageMatrix_Choice_BIT_PERFECT_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_BIT_PERFECT",false,true)}
class CandidatePageMatrix_Choice_FONT_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_FONT",true,false)}
class CandidatePageMatrix_Choice_FONT_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_FONT",true,true)}
class CandidatePageMatrix_Choice_FONT_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_FONT",false,false)}
class CandidatePageMatrix_Choice_FONT_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Choice_FONT",false,true)}
class CandidatePageMatrix_ArtistShelf_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("ArtistShelf",true,false)}
class CandidatePageMatrix_ArtistShelf_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("ArtistShelf",true,true)}
class CandidatePageMatrix_ArtistShelf_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("ArtistShelf",false,false)}
class CandidatePageMatrix_ArtistShelf_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("ArtistShelf",false,true)}
class CandidatePageMatrix_Profile_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Profile",true,false)}
class CandidatePageMatrix_Profile_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Profile",true,true)}
class CandidatePageMatrix_Profile_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Profile",false,false)}
class CandidatePageMatrix_Profile_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Profile",false,true)}
class CandidatePageMatrix_SwitchProfile_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("SwitchProfile",true,false)}
class CandidatePageMatrix_SwitchProfile_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("SwitchProfile",true,true)}
class CandidatePageMatrix_SwitchProfile_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("SwitchProfile",false,false)}
class CandidatePageMatrix_SwitchProfile_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("SwitchProfile",false,true)}
class CandidatePageMatrix_ManageAccounts_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("ManageAccounts",true,false)}
class CandidatePageMatrix_ManageAccounts_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("ManageAccounts",true,true)}
class CandidatePageMatrix_ManageAccounts_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("ManageAccounts",false,false)}
class CandidatePageMatrix_ManageAccounts_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("ManageAccounts",false,true)}
class CandidatePageMatrix_AboutDeveloper_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("AboutDeveloper",true,false)}
class CandidatePageMatrix_AboutDeveloper_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("AboutDeveloper",true,true)}
class CandidatePageMatrix_AboutDeveloper_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("AboutDeveloper",false,false)}
class CandidatePageMatrix_AboutDeveloper_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("AboutDeveloper",false,true)}
class CandidatePageMatrix_Credits_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Credits",true,false)}
class CandidatePageMatrix_Credits_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Credits",true,true)}
class CandidatePageMatrix_Credits_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Credits",false,false)}
class CandidatePageMatrix_Credits_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Credits",false,true)}
class CandidatePageMatrix_License_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("License",true,false)}
class CandidatePageMatrix_License_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("License",true,true)}
class CandidatePageMatrix_License_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("License",false,false)}
class CandidatePageMatrix_License_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("License",false,true)}
class CandidatePageMatrix_AppUpdates_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("AppUpdates",true,false)}
class CandidatePageMatrix_AppUpdates_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("AppUpdates",true,true)}
class CandidatePageMatrix_AppUpdates_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("AppUpdates",false,false)}
class CandidatePageMatrix_AppUpdates_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("AppUpdates",false,true)}
class CandidatePageMatrix_WearOs_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("WearOs",true,false)}
class CandidatePageMatrix_WearOs_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("WearOs",true,true)}
class CandidatePageMatrix_WearOs_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("WearOs",false,false)}
class CandidatePageMatrix_WearOs_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("WearOs",false,true)}
class CandidatePageMatrix_Sources_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Sources",true,false)}
class CandidatePageMatrix_Sources_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Sources",true,true)}
class CandidatePageMatrix_Sources_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Sources",false,false)}
class CandidatePageMatrix_Sources_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Sources",false,true)}
class CandidatePageMatrix_ListenTogether_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("ListenTogether",true,false)}
class CandidatePageMatrix_ListenTogether_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("ListenTogether",true,true)}
class CandidatePageMatrix_ListenTogether_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("ListenTogether",false,false)}
class CandidatePageMatrix_ListenTogether_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("ListenTogether",false,true)}
class CandidatePageMatrix_AccountScrobbling_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("AccountScrobbling",true,false)}
class CandidatePageMatrix_AccountScrobbling_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("AccountScrobbling",true,true)}
class CandidatePageMatrix_AccountScrobbling_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("AccountScrobbling",false,false)}
class CandidatePageMatrix_AccountScrobbling_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("AccountScrobbling",false,true)}

class CandidatePageMatrix_albumNarrow:CandidatePageMatrixBase(){@Test fun render()=shot("Album",true,false,true)}
class CandidatePageMatrix_playlistNarrow:CandidatePageMatrixBase(){@Test fun render()=shot("Playlist",false,true,true)}
class CandidatePageMatrix_LocalMusic_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("LocalMusic",true,false)}
class CandidatePageMatrix_LocalMusic_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("LocalMusic",true,true)}
class CandidatePageMatrix_LocalMusic_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("LocalMusic",false,false)}
class CandidatePageMatrix_LocalMusic_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("LocalMusic",false,true)}
class CandidatePageMatrix_MoodCategory_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("MoodCategory",true,false)}
class CandidatePageMatrix_MoodCategory_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("MoodCategory",true,true)}
class CandidatePageMatrix_MoodCategory_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("MoodCategory",false,false)}
class CandidatePageMatrix_MoodCategory_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("MoodCategory",false,true)}
class CandidatePageMatrix_Replay_dark_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Replay",true,false)}
class CandidatePageMatrix_Replay_dark_material:CandidatePageMatrixBase(){@Test fun render()=shot("Replay",true,true)}
class CandidatePageMatrix_Replay_light_astryx:CandidatePageMatrixBase(){@Test fun render()=shot("Replay",false,false)}
class CandidatePageMatrix_Replay_light_material:CandidatePageMatrixBase(){@Test fun render()=shot("Replay",false,true)}
