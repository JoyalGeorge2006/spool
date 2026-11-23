package com.music.bitchord
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers=RobolectricDeviceQualifiers.Pixel7)
class QueueNoticeProofTest {
 @get:Rule val compose=createComposeRule()
 private fun shot(dark:Boolean) {
  System.setProperty("roborazzi.test.record","true")
  compose.setContent { BitChordTheme(darkTheme=dark) {
   Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column { repeat(30) {i-> Text("Busy album content $i",Modifier.fillMaxWidth().background(if(i%2==0) Color(0xFF812E9F) else Color(0xFF164A69)),color=Color.White) } }
    QueueActionNoticeHost(QueueActionNotice(1,"Song added to queue"),Modifier.align(Alignment.BottomCenter))
   }
  } }
  compose.waitForIdle();compose.onNodeWithText("Song added to queue").assertIsDisplayed()
  compose.onRoot().captureRoboImage("build/outputs/roborazzi/queue_${if(dark) "dark" else "light"}.png")
 }
 @Test fun queue_dark()=shot(true)
 @Test fun queue_light()=shot(false)
}
