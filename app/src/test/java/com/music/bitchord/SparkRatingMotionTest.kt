package com.music.bitchord

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SparkRatingMotionTest {
 @get:Rule val compose = createComposeRule()
 private fun exercise(dark:Boolean) {
  System.setProperty("roborazzi.test.record", "true")
  var rating by mutableStateOf(LikeStatus.INDIFFERENT)
  var likes=0;var dislikes=0
  compose.setContent {
   BitChordTheme(darkTheme=dark) {
    Box(Modifier.fillMaxSize()) {
     SpoolPlayerOverflow(onDismiss={}) {
      SongActionsMenu(song=Song("spark-proof","Track","Artist",null),signedIn=true,likeStatus=rating,
       onPlayNext={},onAddToQueue={},onStartRadio={},onDownload={},onAddToPlaylist={},onOpenAlbum={},onOpenArtist={},
       onToggleLike={likes++;rating=if(rating==LikeStatus.LIKE) LikeStatus.INDIFFERENT else LikeStatus.LIKE},
       onToggleDislike={dislikes++;rating=if(rating==LikeStatus.DISLIKE) LikeStatus.INDIFFERENT else LikeStatus.DISLIKE})
     }
    }
   }
  }
  fun shot(name:String) { compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/spark_${if(dark) "dark" else "light"}_$name.png") }
  compose.waitForIdle();shot("idle")
  compose.mainClock.autoAdvance=false
  compose.onNodeWithText("Like").performClick()
  for(i in 1..8) {compose.mainClock.advanceTimeBy(128);shot("like_$i")}
  compose.onNodeWithText("Liked").assertIsDisplayed()
  compose.onNodeWithText("Dislike").performClick()
  for(i in 1..8) {compose.mainClock.advanceTimeBy(128);shot("dislike_$i")}
  compose.onNodeWithText("Like").assertIsDisplayed()
  compose.onNodeWithText("Disliked").assertIsDisplayed()
  compose.onNodeWithText("Disliked").performClick()
  compose.mainClock.advanceTimeBy(500);shot("undo")
  compose.onNodeWithText("Dislike").assertIsDisplayed()
  compose.onNodeWithText("Like").performClick();compose.mainClock.advanceTimeBy(150)
  compose.runOnUiThread {rating=LikeStatus.INDIFFERENT}
  compose.mainClock.advanceTimeBy(500);shot("rollback")
  compose.onNodeWithText("Like").assertIsDisplayed()
  compose.onNodeWithText("Liked").assertDoesNotExist()
  assertEquals(2,likes);assertEquals(2,dislikes)
  compose.mainClock.autoAdvance=true
 }
 @Test fun spark_dark()=exercise(true)
 @Test fun spark_light()=exercise(false)
}
