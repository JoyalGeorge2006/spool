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
class StableMenuRowsTest {
 @get:Rule val compose=createComposeRule()
 private fun check(dark:Boolean) {
  System.setProperty("roborazzi.test.record","true")
  var loading by mutableStateOf(true)
  var song by mutableStateOf(Song("stable","Stable menu track","Artist",null))
  var albums=0;var artists=0
  compose.setContent { BitChordTheme(darkTheme=dark) {
   SpoolPlayerOverflow(onDismiss={}) {
    SongActionsMenu(song=song,signedIn=true,likeStatus=LikeStatus.INDIFFERENT,resolvingLinks=loading,
     onPlayNext={},onAddToQueue={},onStartRadio={},onDownload={},onShare={},onCopyLog={},onLyricsOffset={},showSleepTimer=true,onCast={},onAudioOutput={},onAutomixOverview={},onToggleLike={},onToggleDislike={},onAddToPlaylist={},
     onOpenAlbum={albums++},onOpenArtist={artists++})
   }
  } }
  fun bounds()=listOf("Open album","Open artist","Song info").map {compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot}
  fun shot(n:String)=compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/stable_${if(dark) "dark" else "light"}_$n.png")
  compose.waitForIdle();val before=bounds();shot("loading")
  compose.onNodeWithText("Open album").assertIsNotEnabled()
  compose.onNodeWithText("Open artist").assertIsNotEnabled()
  compose.runOnIdle {loading=false}
  compose.waitForIdle();assertEquals(before,bounds());shot("none")
  compose.onAllNodesWithText("None",useUnmergedTree=true).assertCountEquals(2)
  compose.runOnIdle {song=song.copy(albumId="album",albumName="Album name",artistId="artist")}
  compose.waitForIdle();assertEquals(before,bounds());shot("found")
  compose.onNodeWithText("Open album").assertIsEnabled().performClick()
  compose.onNodeWithText("Open artist").assertIsEnabled().performClick()
  assertEquals(1,albums);assertEquals(1,artists)
  compose.runOnIdle {song=song.copy(albumId=null,albumName=null)}
  compose.waitForIdle();assertEquals(before,bounds());shot("album_none")
  compose.onNodeWithText("Open album").assertIsNotEnabled()
  compose.onNodeWithText("Open artist").assertIsEnabled()
 }
 @Test fun stable_dark()=check(true)
 @Test fun stable_light()=check(false)
}
