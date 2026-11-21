@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers=RobolectricDeviceQualifiers.Pixel7)
abstract class IntegratedRootHeaderScreenshotTestBase {
 @get:Rule(order=0) val nativeDecode=NativeDecodeMainThreadRule()
 @get:Rule(order=1) val compose=createComposeRule()
 @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
 protected fun shot(page:String,dark:Boolean,width:Int=390,scale:Float=1f,art:Boolean=true) {
  listOf("java.nio.Buffer","java.nio.ByteBuffer","java.nio.FloatBuffer","java.nio.IntBuffer","java.nio.ShortBuffer","java.nio.LongBuffer","java.nio.DoubleBuffer").forEach { Class.forName(it,true,ClassLoader.getSystemClassLoader()) }
  V6ScreenshotTest().installUiHost()
  val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
  val image=java.io.File(context.cacheDir,"home-station-1200.png")
  val bitmap=android.graphics.Bitmap.createBitmap(1200,1200,android.graphics.Bitmap.Config.ARGB_8888)
  bitmap.eraseColor(android.graphics.Color.rgb(42,72,92))
  val canvas=android.graphics.Canvas(bitmap)
  val paint=android.graphics.Paint().apply{color=android.graphics.Color.rgb(238,191,105);isAntiAlias=true}
  canvas.drawCircle(600f,530f,260f,paint)
  paint.color=android.graphics.Color.rgb(42,72,92);canvas.drawCircle(600f,530f,75f,paint)
  paint.color=android.graphics.Color.WHITE;paint.textSize=72f;canvas.drawText("TEST COVER",370f,1000f,paint)
  image.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  val uri=image.toURI().toString()
  val prepared=listOf(TopPickKind.PERSONAL_STATION,TopPickKind.DISCOVERY_STATION).map { kind ->
   TopPick(kind,images=if(art)listOf(uri)else emptyList(),preparedSongs=listOf(Song("home:${kind.name}","Home song","Artist",if(art)uri else null)))
  }
  val taps=mutableListOf<TopPick>()
  var measured by mutableIntStateOf(0)
  var signInCalls = 0
  var homeList: androidx.compose.foundation.lazy.LazyListState? = null
  compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
   BitChordTheme(darkTheme=dark) {
    Box(Modifier.width(width.dp).height(740.dp).background(MaterialTheme.colorScheme.background).testTag("integrated-root")) {
     val padding=PaddingValues(top=measured.dp+12.dp,bottom=80.dp)
     val list=rememberLazyListState()
     if(page == "Listen") homeList = list
     when(page) {
      "Listen" -> HomeScreen(state=UiState.Success(emptyList()),listState=list,onItemClick={_,_->},onRetry={},
       refreshing=false,onRefresh={},pullState=rememberPullToRefreshState(),contentPadding=padding,
       title="Listen",showTitle=false,signedIn=false,onSignIn={signInCalls++},onSearchClick={},onMoodClick={_,_->},
       topPicks=prepared,onTopPickClick={pick,_->taps+=pick})
      "Explore" -> ExploreScreen(state=UiState.Success(listOf(MoodGenreSection("Moods & moments",emptyList()))),
       listState=list,onCategoryClick={},onRetry={},refreshing=false,onRefresh={},
       pullState=rememberPullToRefreshState(),contentPadding=padding,showTitle=false)
      "Library" -> LibraryScreen(signedIn=true,state=UiState.Success(LibraryPage(emptyList(),emptyList(),emptyList(),null)),
       listState=list,onShelfItemClick={},onShelfItemLongPress={},onNewPlaylist={},onShowAll={},
       replay={ Text("Your listening experience",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onBackground,
         modifier=Modifier.padding(16.dp)) },onSignIn={},onRetry={},refreshing=false,onRefresh={},
       pullState=rememberPullToRefreshState(),contentPadding=padding,links=emptyList(),deviceItems=emptyList(),showTitle=false)
     }
     FrostedTopBar(title=page,rootTitle=page,onRootHeaderMeasured={measured=it},statusBarInsets=WindowInsets(top=32.dp),
      scrolled={false},transparentBackdrop=true,artworkPageChrome=true,actions={
       if(page!="Explore") HeaderActionButton({}) { HeaderIcon(Icons.Rounded.History,"History") }
       TopBarAccountButton(null,{})
      })
    }
   }
  } }
  if(page=="Listen") {
   compose.waitUntil(10_000) { compose.onAllNodesWithTag(if(art)"station-art-loaded" else "station-art-fallback",useUnmergedTree=true).fetchSemanticsNodes().size==2 }
   compose.onNodeWithText("Your Station").performClick()
   compose.runOnIdle { assertEquals(prepared.first().preparedSongs,taps.single().preparedSongs) }
  }
  repeat(4) { org.robolectric.shadows.ShadowLooper.idleMainLooper(300L,java.util.concurrent.TimeUnit.MILLISECONDS);compose.mainClock.advanceTimeBy(300L);compose.waitForIdle()
   if(page=="Listen"&&art) compose.onNodeWithTag("integrated-root").captureRoboImage("build/outputs/roborazzi/readiness_${width}_${scale}_${dark}.png", roborazziOptions=RoborazziOptions(taskType=RoborazziTaskType.Record))
  }
  assertTrue(measured>32)
  compose.onNodeWithText(page).assertExists()
  val first=when(page){"Listen"->compose.onNodeWithText("Songs, artists, albums",substring=true);"Explore"->compose.onNodeWithText("Moods & moments");else->compose.onNodeWithText("Your listening experience")}
  val bounds=first.fetchSemanticsNode().boundsInRoot
  assertTrue("first content below measured header",bounds.top>=measured)
  assertTrue("no extra toolbar-sized gap",bounds.top-measured<55)
  compose.onNodeWithTag("integrated-root").captureRoboImage("build/outputs/roborazzi/integrated_${page}_${width}_${scale}_${if(art)"loaded" else "fallback"}_${if(dark)"dark" else "light"}.png", roborazziOptions=RoborazziOptions(taskType=RoborazziTaskType.Record))
  if(page == "Listen" && width == 320 && scale == 2f) {
   // Additional reachability check AFTER all original assertions/captures.
   // The Google row is a visual button inside the production clickable banner;
   // bind that actual merged OnClick ancestor, not a made-up Role.Button.
   val googleText = hasText("Sign in with Google")
   val signInTarget = googleText and hasClickAction() and isEnabled()
   val candidates = compose.onAllNodes(signInTarget).fetchSemanticsNodes()
   assertEquals("Exactly one production Google sign-in click target", 1, candidates.size)
   val id = candidates.single().id
   val actual = compose.onNode(SemanticsMatcher("captured Google sign-in banner id=$id") { it.id == id })
   val unmergedCard = compose.onNode(SemanticsMatcher("captured card unmerged") { it.id == id }, useUnmergedTree=true)
   val labelCandidates = compose.onAllNodes(googleText, useUnmergedTree=true).fetchSemanticsNodes().filter { node ->
    generateSequence(node.parent) { it.parent }.any { it.id == id }
   }
   assertEquals("Exactly one Google label descendant of captured card", 1, labelCandidates.size)
   val labelId = labelCandidates.single().id
   val labelTarget = compose.onNode(SemanticsMatcher("captured card Google label id=$labelId") { it.id == labelId }, useUnmergedTree=true)
   val listNode = generateSequence(unmergedCard.fetchSemanticsNode().parent) { it.parent }.firstOrNull {
    it.config.contains(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) &&
     it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
   } ?: error("Captured sign-in card has no vertical scrolling ancestor")
   val listId = listNode.id
   val listTarget = compose.onNode(SemanticsMatcher("same enclosing Home list id=$listId") { it.id == listId }, useUnmergedTree=true)
   val before = homeList!!.firstVisibleItemIndex to homeList!!.firstVisibleItemScrollOffset
   println("HOME-SIGNIN-BEFORE card=$id label=$labelId list=$listId state=$before")
   actual.performScrollTo()
   compose.waitForIdle()
   val root = compose.onNodeWithTag("integrated-root").getUnclippedBoundsInRoot()
   // Dp bounds equal pixels in this fixture's Density1, not Android insets.
   val safeTop = root.top.value + measured
   val safeBottom = root.bottom.value - 80f
   fun full(target: SemanticsNodeInteraction): androidx.compose.ui.geometry.Rect {
    val b = target.getUnclippedBoundsInRoot()
    return androidx.compose.ui.geometry.Rect(b.left.value, b.top.value, b.right.value, b.bottom.value)
   }
   fun inside(b: androidx.compose.ui.geometry.Rect) = b.width > 0f && b.height > 0f &&
    b.left >= root.left.value && b.right <= root.right.value && b.top >= safeTop && b.bottom <= safeBottom
   for(attempt in 0 until 8) {
    val card = full(actual)
    val label = full(labelTarget)
    check(card.height <= safeBottom-safeTop && card.width <= root.width.value) { "Sign-in target is oversized: $card" }
    if(inside(card) && inside(label)) break
    val delta = if(card.bottom > safeBottom) card.bottom-safeBottom else card.top-safeTop
    check(kotlin.math.abs(delta) > 0.5f) { "No useful scroll delta for hidden sign-in target" }
    val previous = homeList!!.firstVisibleItemIndex to homeList!!.firstVisibleItemScrollOffset
    listTarget.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { scroll ->
     check(scroll(0f, delta)) { "Home list rejected measured scroll" }
    }
    compose.waitForIdle()
    val next = homeList!!.firstVisibleItemIndex to homeList!!.firstVisibleItemScrollOffset
    check(previous != next) { "Home list made no scroll progress: $previous" }
   }
   val after = homeList!!.firstVisibleItemIndex to homeList!!.firstVisibleItemScrollOffset
   assertNotEquals("Clipped initial sign-in target must really scroll into view", before, after)
   val card = full(actual)
   val label = full(labelTarget)
   assertTrue("Whole unclipped banner within fixture safe band: $card / $safeTop..$safeBottom", inside(card))
   assertTrue("Unclipped captured Google label within fixture safe band: $label", inside(label))
   compose.onNodeWithTag("integrated-root").captureRoboImage(
    "build/outputs/roborazzi/integrated_Listen_320_2.0_signin_scrolled_${if(dark)"dark" else "light"}.png",
    roborazziOptions=RoborazziOptions(taskType=RoborazziTaskType.Record))
   // Touch the visual Google row, whose clickable ancestor is the bound banner.
   labelTarget.performTouchInput { click() }
   compose.runOnIdle { assertEquals("Production sign-in callback invoked exactly once", 1, signInCalls) }
   println("HOME-SIGNIN-REACHABLE id=$id card=$card label=$label list=$after callback=$signInCalls")
  }
 }
}
class IntegratedRootHeaderScreenshotTest_listenDark:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Listen",true)}
class IntegratedRootHeaderScreenshotTest_listenLight:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Listen",false)}
class IntegratedRootHeaderScreenshotTest_exploreDark:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Explore",true)}
class IntegratedRootHeaderScreenshotTest_exploreLight:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Explore",false)}
class IntegratedRootHeaderScreenshotTest_libraryDark:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Library",true)}
class IntegratedRootHeaderScreenshotTest_libraryLight:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Library",false)}
class IntegratedRootHeaderScreenshotTest_listenLarge:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Listen",true,320,2f)}
class IntegratedRootHeaderScreenshotTest_exploreLarge:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Explore",true,320,2f)}
class IntegratedRootHeaderScreenshotTest_libraryLarge:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Library",true,320,2f)}

class IntegratedRootHeaderScreenshotTest_listenFallback:IntegratedRootHeaderScreenshotTestBase(){@Test fun verify()=shot("Listen",true,art=false)}
