@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PlayerTheme
import com.music.bitchord.ui.player.PlayerHost
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
abstract class RelatedPageScreenshotTestBase {
 @get:Rule val compose=createComposeRule()
 protected fun capture(dark:Boolean,large:Boolean=false,failed:Boolean=false,material:Boolean=false) {
  val fixture=V6ScreenshotTest();fixture.installUiHost();val restore=fixture.installVideoStubHost()
  val installed=PlayerPlatform.host
  var requests=0
  PlayerPlatform.install(object:PlayerHost by installed {
   override suspend fun relatedSongs(song:Song):Result<List<Song>> {
    requests++
    return if(failed)Result.failure(IllegalStateException("Fixture failure")) else Result.success(List(8) {
     Song(videoId="related-fixture-$it",title="Related track ${it+1}",artist="Fixture artist",thumbnailUrl=null,durationText="3:${20+it}")
    })
   }
  })
  val old=AppSettings.playerTheme.value;AppSettings.playerTheme.value=PlayerTheme.FLAT
  try {
   compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
    BitChordTheme(darkTheme=dark,palette=if(material)com.music.bitchord.data.settings.PaletteMode.MATERIAL_YOU else com.music.bitchord.data.settings.PaletteMode.ASTRYX) {
     Box(Modifier.width(if(large)320.dp else 390.dp).height(780.dp).background(MaterialTheme.colorScheme.background).testTag("related-page-capture")) {
      fixture.PlayerShot(playing=false,titleText="Track 1",alignQueueSeed=true)
     }
    }
   } }
   compose.onAllNodes(hasText("Related") and SemanticsMatcher("on screen") { it.boundsInRoot.left >= 0f && it.boundsInRoot.left < 400f && it.boundsInRoot.top >= 0f && it.boundsInRoot.top < 780f }).onFirst().performClick()
   compose.mainClock.advanceTimeBy(1800);compose.waitForIdle()
   if(failed) {
    compose.onNodeWithTag("related-page-capture").captureRoboImage("build/outputs/roborazzi/related_error_before_assert.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
    println("RELATED-ERROR-SEMANTICS\n"+compose.onRoot(useUnmergedTree=true).printToString())
    compose.onNodeWithText("Couldn't load related music").assertIsDisplayed()
   }
   else compose.onNodeWithText("Related track 1").assertIsDisplayed()
   assertTrue("Read-only recommendations requested",requests>0)
   compose.onNodeWithTag("related-page-capture").captureRoboImage("build/outputs/roborazzi/related_page_${dark}_${large}_${failed}_${material}_top.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
   if(!failed) {
    compose.onNodeWithTag("related-music").performScrollToIndex(9)
    compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
    compose.onNodeWithTag("related-page-capture").captureRoboImage("build/outputs/roborazzi/related_page_${dark}_${large}_${failed}_${material}_bottom.png", roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
   }
  } finally {AppSettings.playerTheme.value=old;PlayerPlatform.install(restore)}
 }
}
class RelatedPageScreenshotTest_light:RelatedPageScreenshotTestBase(){@Test fun check()=capture(false)}
class RelatedPageScreenshotTest_dark:RelatedPageScreenshotTestBase(){@Test fun check()=capture(true)}
class RelatedPageScreenshotTest_large:RelatedPageScreenshotTestBase(){@Test fun check()=capture(false,true)}
class RelatedPageScreenshotTest_error:RelatedPageScreenshotTestBase(){@Test fun check()=capture(true,failed=true)}

class RelatedPageScreenshotTest_materialDark:RelatedPageScreenshotTestBase(){@Test fun check()=capture(true,material=true)}
class RelatedPageScreenshotTest_materialLight:RelatedPageScreenshotTestBase(){@Test fun check()=capture(false,material=true)}
