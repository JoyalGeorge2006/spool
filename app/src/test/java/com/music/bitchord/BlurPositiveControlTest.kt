package com.music.bitchord

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.*
import dev.chrisbanes.haze.materials.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Diagnostic only: independent platform and library render-effect controls, no production change. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w390dp-h844dp-mdpi")
@OptIn(ExperimentalHazeMaterialsApi::class)
class BlurPositiveControlTest {
 @get:Rule val compose=createComposeRule()
 @Composable private fun Pattern(modifier:Modifier=Modifier) {
  Box(modifier) {
   Canvas(Modifier.fillMaxSize()) {
    for(y in 0..50) for(x in 0..50) drawRect(if((x+y)%2==0)Color.Black else Color.White,Offset(x*12f,y*12f),Size(12f,12f))
   }
   Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceEvenly) {
    repeat(8) { Text("SHARP text ABCDE 012345",color=Color.Red,modifier=Modifier.background(Color.White)) }
   }
  }
 }
 private fun probe(mode:String,radius:Float) {
  val haze=HazeState()
  compose.setContent {
   BitChordTheme(darkTheme=false) {
    Box(Modifier.size(390.dp,400.dp).drawWithContent { println("BLUR_CONTROL_CANVAS hardware=${drawContext.canvas.nativeCanvas.isHardwareAccelerated}");drawContent() }) {
     if(mode=="direct") {
      Pattern(Modifier.fillMaxSize().graphicsLayer { renderEffect=android.graphics.RenderEffect.createBlurEffect(radius.coerceAtLeast(0.001f),radius.coerceAtLeast(0.001f),android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() })
     } else {
      Pattern(Modifier.fillMaxSize().hazeSource(haze))
      val style=if(mode.startsWith("material"))HazeMaterials.regular(MaterialTheme.colorScheme.surface).copy(blurRadius=radius.dp,noiseFactor=if(mode=="materialDefaultNoise")0.15f else 0f) else HazeStyle(backgroundColor=Color.White,tints=emptyList(),blurRadius=radius.dp,noiseFactor=0f)
      println("BLUR_CONTROL_STYLE mode=$mode style=$style")
      Box(Modifier.align(Alignment.Center).size(320.dp,280.dp).onGloballyPositioned { println("BLUR_CONTROL_EFFECT mode=$mode position=${it.positionOnScreen()} size=${it.size}") }.hazeEffect(haze,style=style) { blurEnabled=true })
     }
    }
   }
  }
  compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
  println("BLUR_CONTROL_SOURCE mode=$mode radius=$radius areas=${haze.areas}")
  compose.onRoot().captureRoboImage("build/outputs/roborazzi/blur_control_${mode}_${radius}.png")
 }
 @Test fun directZero()=probe("direct",0f)
 @Test fun directBlur()=probe("direct",24f)
 @Test fun hazeZero()=probe("haze",0f)
 @Test fun hazeBlur()=probe("haze",24f)
 @Test fun materialZero()=probe("material",0f)
 @Test fun materialBlur()=probe("material",24f)
 @Test fun materialDefaultNoiseZero()=probe("materialDefaultNoise",0f)
 @Test fun materialDefaultNoiseBlur()=probe("materialDefaultNoise",24f)
}
