package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers=RobolectricDeviceQualifiers.Pixel7)
class ProfilePillFrostTest {
 @get:Rule val compose=createComposeRule()
 private fun check(dark:Boolean) {
  System.setProperty("roborazzi.test.record","true")
  val old=AppSettings.blurEffects.value
  var history=0;var profile=0
  try {
   AppSettings.setBlurEffects(false)
   compose.setContent { BitChordTheme(darkTheme=dark) {
    val haze=remember {HazeState()}
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
     Row(Modifier.fillMaxWidth().height(180.dp).hazeSource(haze,key=PageFrostSourceKey)) {
      repeat(18) {i->Box(Modifier.weight(1f).fillMaxHeight().background(if(i%2==0) Color.White else Color(0xFF7A24AD))) {Text("SHARP",color=Color.Black)} }
     }
     FloatingHistoryProfileCapsule(account=null,onHistory={history++},onProfile={profile++},hazeState=haze,modifier=Modifier.align(Alignment.TopEnd).padding(top=60.dp,end=16.dp))
    }
   } }
   compose.waitForIdle();val before=compose.onNodeWithTag("floating-history-profile").fetchSemanticsNode().boundsInRoot
   fun shot(name:String)=compose.onRoot().captureRoboImage("build/outputs/roborazzi/pill_${if(dark) "dark" else "light"}_$name.png")
   shot("off")
   compose.runOnIdle {AppSettings.setBlurEffects(true)}
   compose.mainClock.advanceTimeBy(500);compose.waitForIdle();shot("on")
   assertEquals(before,compose.onNodeWithTag("floating-history-profile").fetchSemanticsNode().boundsInRoot)
   compose.onNodeWithContentDescription("Listening history").performClick();assertEquals(1,history)
  } finally {AppSettings.setBlurEffects(old)}
 }
 @Test fun pill_dark()=check(true)
 @Test fun pill_light()=check(false)
}
