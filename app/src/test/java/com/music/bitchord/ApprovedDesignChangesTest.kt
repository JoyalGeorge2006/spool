@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.*
import com.music.bitchord.data.settings.*
import com.music.bitchord.data.sources.*
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.screens.*
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Approved scope only. Reduced viewport is not a real native IME test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="w390dp-h844dp-mdpi")
class ApprovedDesignChangesTest {
 @get:Rule val compose=createComposeRule()
 @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
 private fun probe(page:String,dark:Boolean=false,large:Boolean=false,short:Boolean=false) {
  V6ScreenshotTest().installUiHost()
  val old=AppSettings.themeMode.value
  AppSettings.setThemeMode(if(dark)ThemeMode.DARK else ThemeMode.LIGHT)
  DownloadSession.clear()
  var retries=0;var signIns=0;var picks=0;var saves=0;var tests=0;var dismisses=0
  try {
   compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
    BitChordTheme(darkTheme=dark) {
     Box(Modifier.width(if(large)320.dp else 390.dp).height(if(short)400.dp else if(large)640.dp else 844.dp)
      .background(MaterialTheme.colorScheme.background).testTag("approved-root")) {
      val haze=remember{HazeState()};val scope=rememberCoroutineScope()
      when(page) {
       "home","home_signed_in","home_no_picks" -> HomeScreen(
        state=UiState.Success(emptyList()),listState=rememberLazyListState(),onItemClick={_,_->},onRetry={},
        refreshing=false,onRefresh={},pullState=rememberPullToRefreshState(),contentPadding=PaddingValues(),
        title="Listen",signedIn=page=="home_signed_in",onSignIn={signIns++},onSearchClick={},onMoodClick={_,_->},
        topPicks=if(page=="home_no_picks")emptyList() else listOf(TopPick(TopPickKind.PERSONAL_STATION),TopPick(TopPickKind.DISCOVERY_STATION)),
        onTopPickClick={_,_->picks++})
       "explore","explore_zero","explore_loaded","explore_error","explore_loading" -> ExploreScreen(
        state=when(page){
         "explore_zero"->UiState.Success(emptyList())
         "explore_error"->UiState.Error("Existing fixture error")
         "explore_loading"->UiState.Loading
         "explore_loaded"->UiState.Success(listOf(MoodGenreSection("Moods & moments",listOf(MoodGenre("Focus","fixture:focus",null)))))
         else->UiState.Success(listOf(MoodGenreSection("Moods & moments",emptyList())))
        },listState=rememberLazyListState(),onCategoryClick={},onRetry={retries++},refreshing=false,onRefresh={},
        pullState=rememberPullToRefreshState(),contentPadding=PaddingValues())
       "downloads"->DownloadManagerPage({},PaddingValues())
       "addon"->SourceEditorAlert(haze,SourceConfig(id="fixture:approved-addon",kind=SourceKind.ADDON),{}, {}, {},scope)
       "webdav","smb"->ServerEditorHost(hazeState=haze,
        title=if(page=="webdav")"WebDAV" else "SMB",
        description=androidx.compose.ui.res.stringResource(if(page=="webdav")R.string.webdav_description else R.string.smb_description),
        fields=(if(page=="webdav")listOf("https://cloud.example.invalid","Username","Password") else listOf("nas.local","Share name","Folder inside the share","Username","Password"))
         .map{FieldConfig("",it,isPassword=it=="Password")},
        canSubmit={it[0].isNotBlank()},testFailedRes=R.string.webdav_test_failed,
        onTest={tests++;Result.success(Unit)},onSave={saves++},onDismiss={dismisses++},adaptiveSurface=true)
      }
     }
    }
   } }
   compose.waitForIdle()
   when(page) {
    "home"->{
     val shelf=compose.onNodeWithText("Made for tonight").fetchSemanticsNode().boundsInRoot
     val sign=compose.onNodeWithText("Sign in to YouTube Music").fetchSemanticsNode().boundsInRoot
     assertTrue("P001 music precedes existing banner",shelf.bottom<sign.top)
     compose.onNodeWithText("Sign in with Google").performClick();assertEquals(1,signIns)
     compose.onNodeWithText("Your Station").performClick();assertEquals(1,picks)
    }
    "home_signed_in"->compose.onNodeWithText("Sign in to YouTube Music").assertDoesNotExist()
    "home_no_picks"->compose.onNodeWithText("Sign in to YouTube Music").assertIsDisplayed()
    "explore","explore_zero"->{compose.onNodeWithText("No moods available").assertIsDisplayed();compose.onNodeWithText("Try again").performClick();assertEquals(1,retries)}
    "explore_loaded"->{compose.onNodeWithText("Focus").assertExists();compose.onNodeWithText("No moods available").assertDoesNotExist()}
    "explore_error"->{compose.onNodeWithText("Existing fixture error").assertExists();compose.onNodeWithText("No moods available").assertDoesNotExist()}
    "explore_loading"->compose.onNodeWithText("No moods available").assertDoesNotExist()
    "downloads"->{compose.onNodeWithText("No download activity").assertIsDisplayed();compose.onNodeWithText("Clear").assertIsDisplayed();compose.onNodeWithText("Nothing downloading").assertDoesNotExist()}
    "addon","webdav","smb"->{
     compose.onNodeWithText("Test").assertIsNotEnabled()
     compose.onNodeWithText("Save").assertIsNotEnabled()
     compose.onNodeWithText("Cancel",useUnmergedTree=true).assertIsDisplayed()
     if(page!="addon"&&large)compose.onNodeWithText("Password",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
     assertEquals(0,tests);assertEquals(0,saves)
    }
   }
   compose.onNodeWithTag("approved-root").captureRoboImage("build/outputs/roborazzi/approved_${page}_${dark}_${large}_${short}.png")
  } finally {AppSettings.setThemeMode(old);DownloadSession.clear()}
 }
 @Test fun p001Light()=probe("home")
 @Test fun p001Dark()=probe("home",true)
 @Test fun p001SignedInUnchanged()=probe("home_signed_in")
 @Test fun p001NoPicksKeepsSignIn()=probe("home_no_picks")
 @Test fun p002Light()=probe("explore")
 @Test fun p002Dark()=probe("explore",true)
 @Test fun p002EmptyResponse()=probe("explore_zero")
 @Test fun p002LoadedUnchanged()=probe("explore_loaded")
 @Test fun p002ErrorUnchanged()=probe("explore_error")
 @Test fun p002LoadingUnchanged()=probe("explore_loading")
 @Test fun p014Light()=probe("downloads")
 @Test fun p014DarkLarge()=probe("downloads",true,true)
 @Test fun p039aLarge()=probe("addon",large=true)
 @Test fun p039aNormal()=probe("addon")
 @Test fun p039aShortViewport()=probe("addon",large=true,short=true)
 @Test fun p040aLarge()=probe("webdav",large=true)
 @Test fun p040aDarkLarge()=probe("webdav",true,true)
 @Test fun p041aLarge()=probe("smb",large=true)
 @Test fun p041aShortViewport()=probe("smb",large=true,short=true)
}
