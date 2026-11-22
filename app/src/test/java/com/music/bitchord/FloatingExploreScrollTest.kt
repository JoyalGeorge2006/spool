package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.music.bitchord.ui.screens.ExploreScreen
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Overflow fixture proves real list movement, not an assumed swipe distance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
abstract class FloatingExploreScrollBase {
 @get:Rule val compose=createComposeRule()
 @OptIn(ExperimentalMaterial3Api::class)
 protected fun verify(dark:Boolean,material:Boolean) {
  V6ScreenshotTest().installUiHost()
  lateinit var observedState:androidx.compose.foundation.lazy.LazyListState
  compose.setContent { BitChordTheme(darkTheme=dark,palette=if(material)PaletteMode.MATERIAL_YOU else PaletteMode.ASTRYX) {
   val state=rememberLazyListState()
   SideEffect { observedState=state }
   Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("explore-scroll-root")) {
    ExploreScreen(state=UiState.Success(List(8) { section -> MoodGenreSection("Section $section", List(8) { item ->
     MoodGenre("Mood $section:$item","fixture:$section:$item",null,thumbnailUrl=null,stripeColor=0xFF7A6A4A)
    }) }),listState=state,onCategoryClick={},onRetry={},refreshing=false,onRefresh={},pullState=rememberPullToRefreshState(),
     contentPadding=PaddingValues(top=32.dp,bottom=160.dp),showTitle=true,floatingTitle=true)
    FrostedTopBar(title="No fixed title",controlsOnly=true,statusBarInsets=WindowInsets(top=32.dp),scrolled={true},
     actions={FloatingHistoryProfileCapsule(null,{},{})})
   }
  } }
  compose.onNodeWithText("Explore").assertIsDisplayed()
  compose.onNodeWithTag("explore-scroll-root").captureRoboImage("build/outputs/roborazzi/explore_scroll_${dark}_${material}_top.png")
  compose.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
  compose.waitForIdle()
  compose.runOnIdle { assertTrue("real first visible index moved beyond title: ${observedState.firstVisibleItemIndex}",observedState.firstVisibleItemIndex>=9) }
  compose.onNodeWithText("Explore").assertIsNotDisplayed()
  compose.onNodeWithTag("floating-history-profile").assertIsDisplayed()
  compose.onNodeWithContentDescription("Listening history").assertHasClickAction()
  compose.onNodeWithTag("explore-scroll-root").captureRoboImage("build/outputs/roborazzi/explore_scroll_${dark}_${material}_away.png")
 }
}
class FloatingExploreScrollAstryxDark:FloatingExploreScrollBase(){@Test fun check()=verify(true,false)}
class FloatingExploreScrollAstryxLight:FloatingExploreScrollBase(){@Test fun check()=verify(false,false)}
class FloatingExploreScrollMaterialDark:FloatingExploreScrollBase(){@Test fun check()=verify(true,true)}
class FloatingExploreScrollMaterialLight:FloatingExploreScrollBase(){@Test fun check()=verify(false,true)}
