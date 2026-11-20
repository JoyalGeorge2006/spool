@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.screens.PaletteCards
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production palette picker and floating bars over an illustrative source layer. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w390dp-h844dp-mdpi")
class AppearanceMatrixTest {
 @get:Rule val compose=createComposeRule()
 private fun probe(dark:Boolean,palette:PaletteMode,blur:Boolean,font:Float,checker:Boolean=false) {
  V6ScreenshotTest().installUiHost()
  val old=AppSettings.blurEffects.value
  AppSettings.blurEffects.value=blur
  try {
   compose.setContent {
    CompositionLocalProvider(LocalDensity provides Density(1f,font)) {
     BitChordTheme(darkTheme=dark,palette=palette) {
      val haze=remember { HazeState() }
      Box(Modifier.width(390.dp).height(800.dp).testTag("appearance-matrix").drawWithContent { println("APPEARANCE_CANVAS sdk=${android.os.Build.VERSION.SDK_INT} hardware=${drawContext.canvas.nativeCanvas.isHardwareAccelerated}");drawContent() }) {
       // Production host shape (SettingsSheet.kt:502): the page content scrolls,
       // so PaletteCards' fillMaxHeight meets an UNBOUNDED height constraint and
       // wraps its content. A fixed non-scrolling column made it fill the
       // viewport instead - a harness artifact, not the shipped layout.
       Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).hazeSource(haze).background(MaterialTheme.colorScheme.background)) {
        Text("Appearance",color=MaterialTheme.colorScheme.onBackground,style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(16.dp))
        PaletteCards(selected=palette,onSelect={},modifier=Modifier.padding(horizontal=12.dp))
        Spacer(Modifier.height(16.dp))
        Text(if(blur)"Blur effects: on" else "Blur effects: off",color=MaterialTheme.colorScheme.onBackground,modifier=Modifier.padding(16.dp))
        // Fixed height rather than fillMaxSize: under verticalScroll the child
        // gets unbounded height, where fillMaxSize would collapse to zero.
        if(checker) Canvas(Modifier.fillMaxWidth().height(800.dp)) {
         for(y in 0..100) for(x in 0..40) drawRect(if((x+y)%2==0)Color.Black else Color.White,androidx.compose.ui.geometry.Offset(x*12f,y*12f),androidx.compose.ui.geometry.Size(12f,12f))
        } else repeat(18) { i ->
         Row(Modifier.fillMaxWidth().height(48.dp).background(if(i%2==0)Color(0xFF6750A4) else Color(0xFF25836C))) {
          Text("Background source ${i+1}",color=Color.White,modifier=Modifier.padding(10.dp))
         }
        }
       }
       Column(Modifier.align(Alignment.BottomCenter)) {
        MiniPlayer(song=Song(videoId="fixture",title="Midnight Rain",artist="Nova Rae",thumbnailUrl=null,durationText="3:48"),isPlaying=false,isLoading=false,hazeState=haze,onPlayPause={},onNext={},onPrevious={},onExpand={})
        Spacer(Modifier.height(8.dp))
        FloatingBottomBar(tabs=listOf(BottomTab("Home",Icons.Rounded.Home),BottomTab("Explore",Icons.Rounded.Search),BottomTab("Library",Icons.Rounded.LibraryMusic),BottomTab("Settings",Icons.Rounded.Settings)),selectedIndex=0,onTabSelected={},hazeState=haze)
       }
      }
     }
    }
   }
   compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
   compose.onNodeWithText("Astryx neutral").assertIsDisplayed()
   compose.onNodeWithText("Material You").assertIsDisplayed()
   compose.onNodeWithTag("appearance-matrix").captureRoboImage("build/outputs/roborazzi/appearance_checker${checker}_sdk${android.os.Build.VERSION.SDK_INT}_${dark}_${palette}_${blur}_${font}.png")
  } finally { AppSettings.blurEffects.value=old }
 }
 @Test @Config(sdk=[31]) fun android12SolidFallback()=probe(false,PaletteMode.ASTRYX,true,1f)
 @Test @Config(sdk=[30]) fun olderPhoneSolidFallback()=probe(false,PaletteMode.ASTRYX,true,1f)
 @Test fun checkerBarsOff()=probe(false,PaletteMode.ASTRYX,false,1f,true)
 @Test fun checkerBarsOn()=probe(false,PaletteMode.ASTRYX,true,1f,true)
 @Test fun checkerLargeBarsOn()=probe(false,PaletteMode.ASTRYX,true,2f,true)
 @Test fun lightAstryxFlat()=probe(false,PaletteMode.ASTRYX,false,1f)
 @Test fun lightAstryxBlur()=probe(false,PaletteMode.ASTRYX,true,1f)
 @Test fun lightMaterialFlat()=probe(false,PaletteMode.MATERIAL_YOU,false,1f)
 @Test fun lightMaterialBlur()=probe(false,PaletteMode.MATERIAL_YOU,true,1f)
 @Test fun darkAstryxFlat()=probe(true,PaletteMode.ASTRYX,false,1f)
 @Test fun darkAstryxBlur()=probe(true,PaletteMode.ASTRYX,true,1f)
 @Test fun darkMaterialFlat()=probe(true,PaletteMode.MATERIAL_YOU,false,1f)
 @Test fun darkMaterialBlur()=probe(true,PaletteMode.MATERIAL_YOU,true,1f)
 @Test fun largeLightAstryxFlat()=probe(false,PaletteMode.ASTRYX,false,2f)
 @Test fun largeLightAstryxBlur()=probe(false,PaletteMode.ASTRYX,true,2f)
 @Test fun largeLightMaterialFlat()=probe(false,PaletteMode.MATERIAL_YOU,false,2f)
 @Test fun largeLightMaterialBlur()=probe(false,PaletteMode.MATERIAL_YOU,true,2f)
 @Test fun largeDarkAstryxFlat()=probe(true,PaletteMode.ASTRYX,false,2f)
 @Test fun largeDarkAstryxBlur()=probe(true,PaletteMode.ASTRYX,true,2f)
 @Test fun largeDarkMaterialFlat()=probe(true,PaletteMode.MATERIAL_YOU,false,2f)
 @Test fun largeDarkMaterialBlur()=probe(true,PaletteMode.MATERIAL_YOU,true,2f)

 /** Measured card heights (px) for both palette cards in the production host shape. */
 private fun cardHeights(font:Float):Pair<Float,Float> {
  V6ScreenshotTest().installUiHost()
  compose.setContent {
   CompositionLocalProvider(LocalDensity provides Density(1f,font)) {
    BitChordTheme(darkTheme=false,palette=PaletteMode.ASTRYX) {
     // Same production shape as SettingsSheet.kt:502: scrolling parent, so the
     // cards see an unbounded height constraint exactly as in the app.
     Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.background)) {
      Text("Appearance",color=MaterialTheme.colorScheme.onBackground,style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(16.dp))
      PaletteCards(selected=PaletteMode.MATERIAL_YOU,onSelect={},modifier=Modifier.padding(horizontal=12.dp))
     }
    }
   }
  }
  compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
  // The only clickables in this host are the two cards themselves.
  val cards=compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
  org.junit.Assert.assertEquals("expected exactly the two palette cards to be clickable",2,cards.size)
  return cards[0].boundsInRoot.height to cards[1].boundsInRoot.height
 }

 @Test fun paletteCardsEqualHeightFont1x() {
  val(a,b)=cardHeights(1f)
  org.junit.Assert.assertTrue("palette cards must measure equal heights at font 1x (got $a vs $b px)",kotlin.math.abs(a-b)<=1f)
 }

 @Test fun paletteCardsEqualHeightFont2x() {
  val(a,b)=cardHeights(2f)
  org.junit.Assert.assertTrue("palette cards must measure equal heights at font 2x with wrapped text (got $a vs $b px)",kotlin.math.abs(a-b)<=1f)
 }

 /** WCAG contrast of a color pair. */
 private fun contrast(fg:Color,bg:Color):Double {
  fun lin(c:Float):Double { val d=c.toDouble(); return if(d<=0.03928) d/12.92 else Math.pow((d+0.055)/1.055,2.4) }
  fun lum(c:Color)=0.2126*lin(c.red)+0.7152*lin(c.green)+0.0722*lin(c.blue)
  val l1=lum(fg);val l2=lum(bg)
  return (Math.max(l1,l2)+0.05)/(Math.min(l1,l2)+0.05)
 }

 @Test fun lightSecondaryTextContrastMeasured() {
  V6ScreenshotTest().installUiHost()
  var pair:Pair<Color,Color>?=null
  var myPair:Pair<Color,Color>?=null
  // One setContent per test: both palettes sampled in the same composition.
  compose.setContent {
   BitChordTheme(darkTheme=false,palette=PaletteMode.ASTRYX) {
    val scheme=MaterialTheme.colorScheme
    SideEffect { pair=scheme.onSurfaceVariant to scheme.surface }
   }
   BitChordTheme(darkTheme=false,palette=PaletteMode.MATERIAL_YOU) {
    val scheme=MaterialTheme.colorScheme
    SideEffect { myPair=scheme.onSurfaceVariant to scheme.surface }
   }
  }
  compose.waitForIdle()
  val(astryxFg,astryxBg)=pair!!
  val astryx=contrast(astryxFg,astryxBg)
  val(myFg,myBg)=myPair!!
  val my=contrast(myFg,myBg)
  System.err.println("contrast ASTRYX light onSurfaceVariant($astryxFg) vs surface($astryxBg) = $astryx ; MATERIAL_YOU light = $my ($myFg on $myBg)")
  org.junit.Assert.assertTrue("light secondary text must reach 4.8:1 against its actual surface (measured $astryx)",astryx>=4.8)
 }
}

