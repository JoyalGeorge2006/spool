@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.music.bitchord.ui.player.RepeatModes
import com.music.bitchord.ui.player.LocalPlayerFlat
import com.music.bitchord.ui.player.LocalPlayerInk
import com.music.bitchord.ui.player.TransportRow
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers=RobolectricDeviceQualifiers.Pixel7)
abstract class RepeatIconScreenshotTestBase {
 @get:Rule val compose=createComposeRule()
 protected fun verify(dark:Boolean) {
  V6ScreenshotTest().installUiHost()
  var mode by mutableIntStateOf(RepeatModes.OFF)
  var repeatClicks=0;var previous=0;var next=0;var play=0;var shuffle=0
  compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f),LocalPlayerInk provides true,LocalPlayerFlat provides true) {
   BitChordTheme(darkTheme=dark) {
    Column(Modifier.width(390.dp).height(180.dp).background(MaterialTheme.colorScheme.background).padding(vertical=20.dp).testTag("repeat-transport")) {
     Text("Repeat icon: ${when(mode){RepeatModes.ALL->"all";RepeatModes.ONE->"one";else->"off"}}",color=MaterialTheme.colorScheme.onBackground,modifier=Modifier.padding(start=20.dp,bottom=20.dp))
     TransportRow(isPlaying=false,isLoading=false,previousEnabled=true,nextEnabled=true,
      onPrevious={previous++},onPlayPause={play++},onNext={next++},shuffleEnabled=false,repeatMode=mode,
      onToggleShuffle={shuffle++},onCycleRepeat={repeatClicks++;mode=when(mode){RepeatModes.OFF->RepeatModes.ALL;RepeatModes.ALL->RepeatModes.ONE;else->RepeatModes.OFF}})
    }
   }
  } }
  for ((index,label) in listOf("Repeat off","Repeat all","Repeat one").withIndex()) {
   compose.waitForIdle()
   val button=compose.onNodeWithContentDescription(label)
   button.assertHasClickAction()
   val bounds=button.fetchSemanticsNode().boundsInRoot
   assertTrue("Repeat target width preserved",bounds.width>=47.5f)
   assertTrue("Repeat target height preserved",bounds.height>=47.5f)
   if(index==2) compose.onNodeWithText("1",useUnmergedTree=true).assertExists()
   else compose.onNodeWithText("1",useUnmergedTree=true).assertDoesNotExist()
   compose.mainClock.advanceTimeBy(1000L);compose.waitForIdle()
   compose.onNodeWithTag("repeat-transport").captureRoboImage("build/outputs/roborazzi/repeat_${if(dark)"dark" else "light"}_${index}.png")
   button.performClick()
  }
  compose.onNodeWithContentDescription("Repeat off").assertExists()
  assertEquals(3,repeatClicks)
  assertEquals(RepeatModes.OFF,mode)
  assertEquals(0,previous);assertEquals(0,next);assertEquals(0,play);assertEquals(0,shuffle)
 }
}
class RepeatIconScreenshotTest_dark:RepeatIconScreenshotTestBase(){@Test fun statesAndCycle()=verify(true)}
class RepeatIconScreenshotTest_light:RepeatIconScreenshotTestBase(){@Test fun statesAndCycle()=verify(false)}
