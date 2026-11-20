@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.player.RelatedSongRow
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
class RelatedRoundedActionsTest {
 @get:Rule val compose=createComposeRule()
 private val song=Song(videoId="rounded-action",title="Action track",artist="Fixture artist",thumbnailUrl=null,durationText="3:21")
 @Test fun tapPlaysAndLongPressAddsExplicitly() {
  var played=0;var added=0
  compose.setContent { BitChordTheme(darkTheme=false) { RelatedSongRow(song,false,true,true,{played++},{added++}) } }
  compose.onNodeWithTag("related-track-rounded-action").performClick()
  assertEquals(1,played);assertEquals(0,added)
  compose.onNodeWithTag("related-track-rounded-action").performTouchInput { longClick() }
  compose.onNodeWithText("Add to queue").performClick()
  assertEquals(1,added);assertEquals(1,played)
  compose.onNodeWithText("3:21").assertExists()
 }
 @Test fun lockedRowsDoNotExposeQueueActions() {
  var changed=0
  compose.setContent { BitChordTheme(darkTheme=true) { RelatedSongRow(song,true,true,true,{changed++},{changed++}) } }
  compose.onNodeWithTag("related-track-rounded-action").assertIsNotEnabled()
  compose.onNodeWithText("Add to queue").assertDoesNotExist()
  assertEquals(0,changed)
 }
}
