package com.music.bitchord

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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w320dp-h640dp-mdpi")
class PlayerCollapseControlTest {
 @get:Rule val compose = createComposeRule()
 private fun checkControl(dark: Boolean, font: Float) {
  val fixture = V6ScreenshotTest().also { it.installUiHost() }
  val host = fixture.installVideoStubHost()
  var collapsed = 0
  try {
   compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,font)) {
    BitChordTheme(darkTheme=dark) { Box(Modifier.width(320.dp).height(600.dp)) {
     fixture.PlayerShot(playing=false,collapseRequest={collapsed++})
    } }
   } }
   compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
   compose.onNodeWithTag("player-collapse-control").assertIsDisplayed().assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
   compose.onNodeWithTag("player-collapse-disc",useUnmergedTree=true).assertIsDisplayed().assertWidthIsEqualTo(34.dp).assertHeightIsEqualTo(34.dp)
   compose.onNodeWithTag("player-collapse-glyph",useUnmergedTree=true).assertIsDisplayed().assertWidthIsEqualTo(19.dp).assertHeightIsEqualTo(19.dp)
   val caption=compose.onNodeWithTag("player-origin-caption").fetchSemanticsNode().boundsInRoot
   val collapse=compose.onNodeWithTag("player-collapse-control").fetchSemanticsNode().boundsInRoot
   Assert.assertTrue("Caption must not overlap collapse control",caption.left >= collapse.right+4f)
   Assert.assertTrue("Caption must preserve symmetric right control gutter",caption.right <= 256f)
   compose.onRoot().captureRoboImage("build/outputs/roborazzi/collapse_${dark}_${font}.png")
   compose.onNodeWithTag("player-collapse-control").performClick();Assert.assertEquals(1,collapsed)
  } finally { PlayerPlatform.install(host) }
 }
 @Test fun light() = checkControl(false,1f)
 @Test fun dark() = checkControl(true,1f)
 @Test fun largeTextLight() = checkControl(false,2f)
 @Test fun largeTextDark() = checkControl(true,2f)
}
