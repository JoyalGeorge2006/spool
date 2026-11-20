package com.music.bitchord

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Temporal fixture evidence. It verifies movement, not real-device performance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w320dp-h640dp-mdpi")
class PlayerLargeTextTemporalTest {
 @get:Rule val compose=createComposeRule()
 private fun check(dark: Boolean) {
  val fixture=V6ScreenshotTest().also { it.installUiHost() }
  val host=fixture.installVideoStubHost()
  try {
   compose.mainClock.autoAdvance=false
   compose.setContent {
    CompositionLocalProvider(LocalDensity provides Density(1f,2f)) {
     BitChordTheme(darkTheme=dark) {
      Box(Modifier.width(320.dp).height(600.dp)) { fixture.PlayerShot(playing=false) }
     }
    }
   }
   val pixels=mutableListOf<IntArray>()
   for (step in 0..2) {
    compose.mainClock.advanceTimeBy(if(step==0)1500L else 4000L)
    compose.waitForIdle()
    Assert.assertTrue("Full title retained in text semantics", compose.onAllNodesWithText("Midnight Rain",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty())
    val file=java.io.File("build/outputs/roborazzi/title_temporal_${dark}_${step}.png")
    compose.onRoot().captureRoboImage(file.absolutePath)
    val bitmap=BitmapFactory.decodeFile(file.absolutePath)
    Assert.assertEquals(320,bitmap.width)
    Assert.assertEquals(600,bitmap.height)
    val crop=IntArray(260*45)
    bitmap.getPixels(crop,0,260,30,150,260,45)
    pixels.add(crop)
    bitmap.recycle()
   }
   Assert.assertTrue("Overflowing title must move between temporal samples",pixels.drop(1).any { !it.contentEquals(pixels[0]) })
  } finally { PlayerPlatform.install(host) }
 }
 @Test fun light()=check(false)
 @Test fun dark()=check(true)
}
