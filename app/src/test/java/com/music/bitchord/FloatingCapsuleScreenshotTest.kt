package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowCircleUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.DownloadSession
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="w390dp-h844dp-mdpi")
class FloatingCapsuleScreenshotTest {
 @get:Rule val compose = createComposeRule()
 private fun shot(dark:Boolean, large:Boolean=false, material:Boolean=false, extra:Boolean=false) {
  V6ScreenshotTest().installUiHost()
  var history=0;var profile=0;var back=0;var update=0;var downloads=0
  DownloadSession.clear()
  if(extra) {DownloadSession.queued(Song("capture:download","Download fixture","Fixture artist",null));DownloadSession.running("capture:download",0.42f)}
  try {
  compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
   BitChordTheme(darkTheme=dark,palette=if(material)PaletteMode.MATERIAL_YOU else PaletteMode.ASTRYX) {
    Box(Modifier.width(if(large)320.dp else 390.dp).height(240.dp).background(MaterialTheme.colorScheme.background).testTag("floating-frame")) {
     FrostedTopBar(title="Never rendered as fixed title",controlsOnly=true,
      statusBarInsets=WindowInsets(top=32.dp),scrolled={true},onBack={back++},
      actions={
       if(extra) {
        HeaderActionButton({update++}) { HeaderIcon(Icons.Rounded.ArrowCircleUp,"Update available") }
        TopBarDownloadButton({downloads++})
       }
       FloatingHistoryProfileCapsule(null,{history++},{profile++}) })
    }
   }
  } }
  compose.onNodeWithText("Never rendered as fixed title").assertDoesNotExist()
  compose.onNodeWithTag("floating-history-profile").assertIsDisplayed()
  compose.mainClock.advanceTimeBy(700L);compose.waitForIdle()
  val targets=compose.onAllNodesWithTag("header-action-target").fetchSemanticsNodes()
  targets.forEach { n -> assertTrue(n.boundsInRoot.width>=47.5f);assertTrue(n.boundsInRoot.height>=47.5f) }
  for(a in targets.indices)for(b in a+1 until targets.size)assertFalse(targets[a].boundsInRoot.overlaps(targets[b].boundsInRoot))
  if(extra) {
   compose.onNodeWithContentDescription("Update available").performClick()
   compose.onNodeWithContentDescription(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.downloads_progress,42)).performClick()
   compose.runOnIdle {assertEquals(1,update);assertEquals(1,downloads)}
  }
  compose.onNodeWithContentDescription("Listening history").performClick()
  compose.onNodeWithContentDescription("Settings",substring=true).performClick()
  compose.onNodeWithContentDescription("Back").performClick()
  compose.runOnIdle { assertEquals(1,history); assertEquals(1,profile); assertEquals(1,back) }
  compose.onNodeWithTag("floating-frame").captureRoboImage("build/outputs/roborazzi/floating_capsule_${dark}_${large}_${material}_${extra}.png")
  } finally { DownloadSession.clear() }
 }
 @Test fun dark()=shot(true)
 @Test fun light()=shot(false)
 @Test fun largeFont()=shot(true,true)
 @Test fun materialDark()=shot(true,material=true)
 @Test fun materialLight()=shot(false,material=true)
 @Test fun activeAstryxDark()=shot(true,extra=true)
 @Test fun activeAstryxLight()=shot(false,extra=true)
 @Test fun activeMaterialDark()=shot(true,material=true,extra=true)
 @Test fun activeMaterialLight()=shot(false,material=true,extra=true)
 @Test fun allActionsNarrowLarge()=shot(true,true,true,true)
}
