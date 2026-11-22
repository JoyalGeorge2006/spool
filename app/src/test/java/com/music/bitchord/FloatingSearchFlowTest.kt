package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.*
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.screens.*
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Callback/focus/layout checks with offline fixture results. Does not verify service responses. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
abstract class FloatingSearchFlowBase {
 @get:Rule val compose=createComposeRule()
 protected fun flow(dark:Boolean,material:Boolean) {
  V6ScreenshotTest().installUiHost()
  var query by mutableStateOf("");var source by mutableIntStateOf(0)
  var submitted by mutableStateOf(false);var reset by mutableIntStateOf(0)
  var requestFocus by mutableStateOf(false);var handled=0;var submits=0;var pages=0;var played=0;var filters=0
  var filter by mutableStateOf(SearchFilter.ALL)
  var history by mutableStateOf(List(12){SearchHistoryEntity("recent:$it","Recent query $it","Fixture artist",null,EntityType.TRACK)})
  val songs=List(20){Song("flow:$it","Online fixture $it","Fixture artist",null)}
  compose.setContent { BitChordTheme(darkTheme=dark,palette=if(material)PaletteMode.MATERIAL_YOU else PaletteMode.ASTRYX) {
   Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("search-flow")) {
    SearchScreen(query=query,onQueryChange={query=it;submitted=false},filter=filter,currentSong=null,isPlaying=false,
     onFilterChange={filter=it;filters++},results=if(submitted&&source==0)UiState.Success(songs.map{SearchResult.Track(it)})else null,
     loadingMore=false,onLoadMore={pages++},listState=rememberLazyListState(),scrollResetTrigger=reset,
     focusRequested=requestFocus,onFocusHandled={requestFocus=false;handled++},
     onSongClick={_,_->played++},onSongLongPress={},onSongSwipe={},onTopResultPlay={played++},onTopResultPlaylist={},onBrowseClick={},
     history=history,suggestions=if(query.isNotEmpty()&&!submitted&&source==0)listOf(query,"Night suggestion")else emptyList(),
     typeaheadResults=if(query.isNotEmpty()&&!submitted&&source==0)listOf(SearchResult.Track(Song("typeahead","Typeahead fixture","Fixture artist",null)))else emptyList(),
     onSubmit={submitted=true;submits++;reset++},onSuggestionClick={query=it;submitted=true;reset++},
     onHistoryClick={query=it.title;submitted=true;reset++},onHistoryRemove={id->history=history.filterNot{it.id==id}},onHistoryClear={history=emptyList()},
     contentPadding=PaddingValues(top=32.dp,bottom=60.dp),topPadding=0.dp,showField=true,scrollingHeader=true,pageTitle="Search",
     searchingLibrary=source==1,searchingAddons=source==2,
     libraryResults=if(submitted)UiState.Success(listOf(Song("library","Library fixture","Fixture artist",null)))else null,
     addonResults=if(submitted)UiState.Success(listOf(Song("addon","Addon fixture","Fixture artist",null)))else null,
     sourceSwitcher={SegmentedControl(options=listOf("YouTube","Library","Addons"),selectedIndex=source,onSelect={source=it})})
    FrostedTopBar(title="Fixed title must not appear",controlsOnly=true,statusBarInsets=WindowInsets(top=32.dp),scrolled={true},actions={FloatingHistoryProfileCapsule(null,{},{})})
   }
  } }
  fun capture(state:String) { compose.waitForIdle();compose.onNodeWithTag("search-flow").captureRoboImage("build/outputs/roborazzi/search_flow_${dark}_${material}_$state.png") }
  compose.onNodeWithText("Library").performClick()
  compose.onNode(hasSetTextAction()).performTextInput("local")
  compose.onNode(hasSetTextAction()).performImeAction()
  compose.onNodeWithText("Library fixture").assertIsDisplayed().performClick()
  assertEquals(1,played);capture("library")
  compose.onNodeWithContentDescription("Clear search").performClick()
  compose.onNodeWithText("Addons").performClick()
  compose.onNode(hasSetTextAction()).performTextInput("addon")
  compose.onNode(hasSetTextAction()).performImeAction()
  compose.onNodeWithText("Addon fixture").assertIsDisplayed().performClick()
  assertEquals(2,played);capture("addons")
  compose.onNodeWithContentDescription("Clear search").performClick()
  compose.onNodeWithText("YouTube").performClick()
  compose.onNode(hasSetTextAction()).performTextInput("night")
  compose.onNodeWithText("Night suggestion").assertIsDisplayed()
  compose.onNodeWithText("Typeahead fixture").assertIsDisplayed()
  capture("suggestions")
  compose.onNodeWithText("Night suggestion").performClick()
  compose.onNode(hasText("Songs") and hasClickAction()).performClick();assertEquals(1,filters)
  compose.onNodeWithText("Online fixture 0").assertIsDisplayed()
  capture("results")
  compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Online fixture 19"))
  compose.waitForIdle();assertTrue("Pagination remains connected",pages>0)
  compose.runOnIdle { requestFocus=true }
  compose.waitForIdle()
  compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsFocused()
  compose.runOnIdle {assertEquals("Night suggestion",query);assertEquals(1,handled);assertEquals(2,submits)}
  capture("refocused")
  compose.onNodeWithContentDescription("Clear search").performClick()
  compose.onNodeWithText("Recent query 0").assertIsDisplayed()
  compose.onNodeWithText("Clear",ignoreCase=true).performClick()
  compose.onNodeWithText("Recent query 0").assertDoesNotExist()
  compose.onNodeWithText("Fixed title must not appear").assertDoesNotExist()
  capture("cleared")
 }
}
class FloatingSearchFlowAstryxDark:FloatingSearchFlowBase(){@Test fun check()=flow(true,false)}
class FloatingSearchFlowAstryxLight:FloatingSearchFlowBase(){@Test fun check()=flow(false,false)}
class FloatingSearchFlowMaterialDark:FloatingSearchFlowBase(){@Test fun check()=flow(true,true)}
class FloatingSearchFlowMaterialLight:FloatingSearchFlowBase(){@Test fun check()=flow(false,true)}
