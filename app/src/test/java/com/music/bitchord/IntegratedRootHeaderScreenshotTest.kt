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
  compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,scale)) {
   BitChordTheme(darkTheme=dark) {
    Box(Modifier.width(width.dp).height(740.dp).background(MaterialTheme.colorScheme.background).testTag("integrated-root")) {
     val padding=PaddingValues(top=measured.dp+12.dp,bottom=80.dp)
     val list=rememberLazyListState()
     when(page) {
      "Listen" -> HomeScreen(state=UiState.Success(emptyList()),listState=list,onItemClick={_,_->},onRetry={},
       refreshing=false,onRefresh={},pullState=rememberPullToRefreshState(),contentPadding=padding,
       title="Listen",showTitle=false,signedIn=false,onSignIn={},onSearchClick={},onMoodClick={_,_->},
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
