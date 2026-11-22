@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.ui.components.UpdateAvailableDialog
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
class UpdatePopupContrastTest {
 @get:Rule val compose=createComposeRule()
 private fun check(dark:Boolean,large:Boolean) {
  V6ScreenshotTest().installUiHost()
  var opens=0;var dismisses=0
  var foreground=Color.Unspecified;var background=Color.Unspecified
  compose.setContent {
   CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
    BitChordTheme(darkTheme=dark) {
     foreground=MaterialTheme.colorScheme.onSurface;background=MaterialTheme.colorScheme.surface
     CompositionLocalProvider(LocalContentColor provides Color.Black) {
      Box(Modifier.width(if(large)320.dp else 390.dp).height(780.dp).testTag("popup-contrast")) {
       UpdateAvailableDialog("1.9.13",null,HazeState(),{dismisses++},{opens++},{},{},{})
      }
     }
    }
   }
  }
  val title="Spool 1.9.13 is available"
  val body="Open App updates to download, check and install this update. You can keep using Spool while it downloads."
  for(text in listOf(title,body)) {
   val layouts=mutableListOf<TextLayoutResult>()
   compose.onNodeWithText(text).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
   assertEquals(foreground,layouts.single().layoutInput.style.color)
  }
  val light=maxOf(foreground.luminance(),background.luminance());val dim=minOf(foreground.luminance(),background.luminance())
  assertTrue("Text must exceed WCAG AA normal-text contrast",(light+.05)/(dim+.05)>=4.5)
  compose.onNodeWithText("Open app updates").assertIsDisplayed()
  compose.onNodeWithText("Later").assertIsDisplayed()
  compose.onNodeWithTag("popup-contrast").captureRoboImage("build/outputs/roborazzi/update_popup_${if(dark)"dark" else "light"}_${if(large)"large" else "normal"}.png")
  compose.onNodeWithText("Open app updates").performClick();assertEquals(1,opens);assertEquals(0,dismisses)
  compose.onNodeWithText("Later").performClick();assertEquals(1,dismisses)
 }
 @Test fun darkIgnoresBlackAmbient()=check(true,false)
 @Test fun lightUsesDarkForeground()=check(false,false)
 @Test fun darkLargeText()=check(true,true)
 @Test fun lightLargeText()=check(false,true)
}
