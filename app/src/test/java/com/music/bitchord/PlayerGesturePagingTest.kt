@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.settings.*
import com.music.bitchord.ui.player.*
import com.music.bitchord.ui.theme.BitChordTheme
import com.music.bitchord.ui.utils.guardSheetFromContentTouches
import org.junit.*
import kotlinx.coroutines.launch
import com.music.bitchord.playback.restoreRemovedQueueItem
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
abstract class PlayerGesturePagingTestBase {
 @get:Rule val compose=createComposeRule()
 protected fun verify(dark:Boolean,large:Boolean=false,reduced:Boolean=false,material:Boolean=false) {
  val fixture=V6ScreenshotTest();fixture.installUiHost();val host=fixture.installVideoStubHost()
  val oldReduced=AppSettings.reduceAnimation.value;AppSettings.reduceAnimation.value=reduced
  val oldTheme=AppSettings.playerTheme.value;AppSettings.playerTheme.value=PlayerTheme.FLAT
  var removes=0;var next=0;var previous=0;var collapses=0
  var jumps=0;var moves=0;var plays=0;var seeks=0
  val position=com.music.bitchord.playback.PlaybackPosition().apply {report(42000L)}
  try {
   compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
    BitChordTheme(darkTheme=dark,palette=if(material)PaletteMode.MATERIAL_YOU else PaletteMode.ASTRYX) { Box(Modifier.width(if(large)320.dp else 390.dp).height(780.dp).testTag("gesture-player")) {
     fixture.PlayerShot(playing=false,nextRequest={next++},previousRequest={previous++},removeRequest={removes++},collapseRequest={collapses++},jumpRequest={jumps++},moveRequest={_,_->moves++},playRequest={plays++},seekRequest={seeks++},fractionRequest={seeks++},fixturePosition=position)
    } }
   } }
   visibleText("Queue").performClick();settle()
   selected("Queue")
   // Real row/body drags must go to the pager, never removal or track skip.
   compose.onNodeWithTag("player-section-pager").performTouchInput { swipe(Offset(width*.85f,height*.25f),Offset(width*.15f,height*.25f),400) };settle()
   selected("Lyrics")
   compose.onNodeWithText("Third line carries on").assertIsDisplayed()
   compose.onNodeWithTag("gesture-player").captureRoboImage("build/outputs/roborazzi/gesture_${dark}_${large}_${material}_lyrics.png")
   compose.runOnIdle { position.report(0L) };settle()
   compose.onNodeWithText("First line of the song").assertIsDisplayed()
   compose.onNodeWithTag("gesture-player").captureRoboImage("build/outputs/roborazzi/gesture_${dark}_${large}_${material}_lyrics_start.png")
   compose.runOnIdle { position.report(42000L) };settle()
   compose.onNodeWithTag("player-section-pager").performTouchInput { swipe(Offset(width*.85f,height*.35f),Offset(width*.15f,height*.35f),400) };settle()
   selected("Related")
   compose.onNodeWithTag("gesture-player").captureRoboImage("build/outputs/roborazzi/gesture_${dark}_${large}_${material}_related.png")
   compose.onNodeWithTag("player-section-pager").performTouchInput { swipeLeft() };settle();selected("Related")
   visibleText("Queue").performClick();settle();selected("Queue")
   // Vertical content scroll cannot page or mutate queue.
   compose.onNodeWithTag("player-section-pager").performTouchInput { swipeUp() };settle();selected("Queue")
   compose.onNodeWithTag("player-section-pager").performTouchInput { swipeDown() };settle();selected("Queue")
   assertEquals(0,removes);assertEquals(0,next);assertEquals(0,previous);assertEquals(0,collapses)
   assertEquals(0,jumps);assertEquals(0,moves);assertEquals(0,plays);assertEquals(0,seeks);assertEquals(42000L,position.positionMs)
   compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) and onScreen).performScrollToIndex(0);settle()
   compose.onNodeWithTag("gesture-player").captureRoboImage("build/outputs/roborazzi/gesture_${dark}_${large}_${material}_queue.png")
   compose.onNodeWithTag("queue-row-menu-fixture-entry-0",useUnmergedTree=true).assertIsDisplayed()
   compose.onNodeWithTag("queue-row-remove-fixture-entry-0",useUnmergedTree=true).assertDoesNotExist()
   val menu=hasTestTag("queue-row-remove-fixture-entry-1") and onScreen
   compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) and onScreen).performScrollToNode(hasText("Track 2"));settle()
   compose.onAllNodes(menu,useUnmergedTree=true).onFirst().performClick()
   assertEquals(1,removes)
   assertEquals(0,next);assertEquals(0,previous)
   visibleText("Lyrics").performClick();settle();selected("Lyrics")
   compose.onNodeWithTag("player-section-pager").performTouchInput { swipeRight() };settle();selected("Queue")
   assertEquals(1,removes)
   // Header controls remain individually clickable instead of being drag-only.
   compose.onNode(hasContentDescription("Play") and hasAnyAncestor(hasTestTag("player-section-header"))).performClick()
   assertEquals(1,plays)
   compose.onNode(hasContentDescription("Next") and hasAnyAncestor(hasTestTag("player-section-header"))).performClick()
   assertEquals(1,next)
  } finally {AppSettings.reduceAnimation.value=oldReduced;AppSettings.playerTheme.value=oldTheme;PlayerPlatform.install(host)}
 }
 protected fun settle(){compose.mainClock.advanceTimeBy(1200);compose.waitForIdle()}
 protected val onScreen=SemanticsMatcher("on screen") { it.boundsInRoot.width>1f && it.boundsInRoot.height>1f && it.boundsInRoot.left>=0f && it.boundsInRoot.left<500f && it.boundsInRoot.top>=0f && it.boundsInRoot.top<900f }
 protected fun visibleText(name:String)=compose.onNode(hasText(name) and onScreen)

 private fun selected(name:String){visibleText(name).assertIsSelected()}
}
class PlayerGesturePagingTest_dark:PlayerGesturePagingTestBase(){@Test fun check()=verify(true)}
class PlayerGesturePagingTest_light:PlayerGesturePagingTestBase(){@Test fun check()=verify(false)}
class PlayerGesturePagingTest_reduced:PlayerGesturePagingTestBase(){@Test fun check()=verify(true,reduced=true)}
class PlayerGesturePagingTest_large:PlayerGesturePagingTestBase(){@Test fun check()=verify(true,true)}

@OptIn(ExperimentalMaterial3Api::class)
class PlayerGestureCollapseTest_main:PlayerGesturePagingTestBase(){
 @Test fun downwardArtworkCollapsesNativeSheet()=collapse(false)
 private fun collapse(section:Boolean) {
  val fixture=V6ScreenshotTest();fixture.installUiHost();val host=fixture.installVideoStubHost()
  val old=AppSettings.playerTheme.value;AppSettings.playerTheme.value=PlayerTheme.FLAT
  var open by mutableStateOf(true)
  try {
   compose.setContent { BitChordTheme(darkTheme=true) {
    Text("Mini-player host",Modifier.testTag("mini-host"))
    if(open){
     val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
     ModalBottomSheet(onDismissRequest={open=false},sheetState=sheet,dragHandle=null,contentWindowInsets={WindowInsets(0,0,0,0)}) {
      Box(Modifier.fillMaxSize().guardSheetFromContentTouches(sheet)) {
       fixture.PlayerShot(playing=false,collapseRequest={open=false})
      }
     }
    }
   } }
   settle()
   val region=compose.onNodeWithTag("player-artwork-collapse-region")
   // A short, slow pull must settle back rather than dismiss.
   region.performTouchInput { swipe(Offset(width*.5f,height*.4f),Offset(width*.5f,height*.4f+12f),800) };settle();assertTrue(open)
   region.performTouchInput { swipe(Offset(width*.5f,height*.25f),Offset(width*.5f,height*.25f+550f),300) };settle()
   assertFalse("Artwork down drag reaches sheet dismissal",open)
  } finally{AppSettings.playerTheme.value=old;PlayerPlatform.install(host)}
 }
}
@OptIn(ExperimentalMaterial3Api::class)
class PlayerGestureCollapseTest_header:PlayerGesturePagingTestBase(){
 @Test fun downwardSectionHeaderCollapsesNativeSheet() {
  val fixture=V6ScreenshotTest();fixture.installUiHost();val host=fixture.installVideoStubHost()
  val old=AppSettings.playerTheme.value;AppSettings.playerTheme.value=PlayerTheme.FLAT
  var open by mutableStateOf(true)
  try {
   compose.setContent { BitChordTheme(darkTheme=true) {
    Text("Mini-player host")
    if(open){val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
     ModalBottomSheet(onDismissRequest={open=false},sheetState=sheet,dragHandle=null,contentWindowInsets={WindowInsets(0,0,0,0)}) {
      Box(Modifier.fillMaxSize().guardSheetFromContentTouches(sheet)){fixture.PlayerShot(playing=false,collapseRequest={open=false})}
     }
    }
   } }
   settle();visibleText("Queue").performClick();settle()
   compose.onNodeWithTag("player-section-header").captureRoboImage("build/outputs/roborazzi/gesture_header_before_collapse.png")
   compose.onNodeWithTag("player-section-header").performTouchInput { swipe(Offset(width*.45f,height*.5f),Offset(width*.45f,height*.5f+600f),300) };settle()
   assertFalse("Section header down drag collapses sheet, not just section",open)
  } finally{AppSettings.playerTheme.value=old;PlayerPlatform.install(host)}
 }
}

class PlayerGestureMenuUndoTest:PlayerGesturePagingTestBase(){
 @Test fun oneTapRemoveAndUndoRestoreUpcomingRowWithoutChangingPlayhead(){
  val fixture=V6ScreenshotTest();fixture.installUiHost();val host=fixture.installVideoStubHost()
  val p=androidx.media3.exoplayer.ExoPlayer.Builder(org.robolectric.RuntimeEnvironment.getApplication()).build()
  val initial=List(5){i->com.music.bitchord.data.model.Song("id$i","Queue song $i","Artist",null,queueEntryId="entry$i",queueTier=com.music.bitchord.data.model.QueueTier.CONTEXT)}
  val media=initial.map{androidx.media3.common.MediaItem.Builder().setMediaId(it.videoId).setUri("https://example.invalid/${it.videoId}").build()}
  p.setMediaItems(media);p.seekTo(0,42000L);p.playWhenReady=true
  var queue by mutableStateOf(initial)
  var removed:Pair<Int,com.music.bitchord.data.model.Song>?=null
  try{
   compose.setContent{BitChordTheme(darkTheme=true){CompositionLocalProvider(LocalPlayerFlat provides true,LocalPlayerPageGestures provides true){
    val snack=remember{SnackbarHostState()};val scope=rememberCoroutineScope()
    Scaffold(snackbarHost={SnackbarHost(snack)}){pad->
     InlineQueue(queue=queue,currentIndex=0,autoplayEnabled=false,controlsLocked=false,
      onJumpTo={fail("Remove must not jump tracks")},onMove={_,_->fail("Remove must not reorder")},onClear={},
      onRemove={index->removed=index to queue[index];queue=queue.filterIndexed{i,_->i!=index};p.removeMediaItem(index)
       scope.launch {if(snack.showSnackbar("Removed from queue","Undo",duration=SnackbarDuration.Indefinite)==SnackbarResult.ActionPerformed){
        val (at,song)=requireNotNull(removed);queue=queue.toMutableList().also{it.add(at,song)}
        p.restoreRemovedQueueItem(at,media[at])
       }}
      },tabbed=true,modifier=Modifier.padding(pad).fillMaxSize())
    }
   }}}
   settle()
   val target=hasTestTag("queue-row-remove-entry1") and onScreen
   compose.onNode(target,useUnmergedTree=true).performClick()
   settle()
   assertEquals(4,queue.size);assertEquals(4,p.mediaItemCount)
   compose.onNodeWithText("Undo").performClick();settle()
   assertEquals(initial,queue);assertEquals(5,p.mediaItemCount)
   assertEquals("id0",p.currentMediaItem?.mediaId);assertEquals(42000L,p.currentPosition);assertTrue(p.playWhenReady)
   compose.onRoot().captureRoboImage("build/outputs/roborazzi/gesture_menu_undo.png")
  }finally{p.release();PlayerPlatform.install(host)}
 }
}

class QueueOneTapRemovalTest:PlayerGesturePagingTestBase(){
 private fun row(dark:Boolean,locked:Boolean=false,large:Boolean=false,lifted:Boolean=false,current:Boolean=false){
  val fixture=V6ScreenshotTest();fixture.installUiHost();val host=fixture.installVideoStubHost()
  var removes=0;var jumps=0
  val song=com.music.bitchord.data.model.Song("queue-test","Queued song","Artist",null,queueEntryId="queue-delete-fixture",queueTier=com.music.bitchord.data.model.QueueTier.CONTEXT)
  try{
   compose.setContent{CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f),LocalPlayerFlat provides true,LocalPlayerPageGestures provides true){
    BitChordTheme(darkTheme=dark){Box(Modifier.width(if(large)320.dp else 390.dp).background(MaterialTheme.colorScheme.background)){
     InlineQueueRow(song=song,isCurrent=current,onClick={jumps++},onRemove={removes++},locked=locked,draggable=true,lifted=lifted)
    }}
   }}
   settle()
   val control=compose.onNodeWithTag("queue-row-remove-queue-delete-fixture",useUnmergedTree=true)
   if(current){
    control.assertDoesNotExist()
    val menu=compose.onNodeWithTag("queue-row-menu-queue-delete-fixture",useUnmergedTree=true)
    menu.assertIsDisplayed().performClick();settle();assertEquals(0,removes)
    compose.onNodeWithText("Remove from queue",useUnmergedTree=true).assertIsDisplayed().performClick();settle()
    assertEquals(1,removes);assertEquals(0,jumps)
   }else if(lifted)control.assertDoesNotExist() else {
    control.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    compose.onNodeWithContentDescription("Drag to reorder").assertIsDisplayed()
    if(locked){control.assertIsNotEnabled();assertEquals(0,removes)}
    else{control.performClick();assertEquals(1,removes);assertEquals(0,jumps)}
   }
   compose.onRoot().captureRoboImage("build/outputs/roborazzi/queue_remove_${dark}_${locked}_${large}_${lifted}_${current}.png")
  }finally{PlayerPlatform.install(host)}
 }
 @Test fun currentRowPreservesOverflowLight()=row(false,current=true)
 @Test fun currentRowPreservesOverflowDark()=row(true,current=true)
 @Test fun directRemovalLight()=row(false)
 @Test fun directRemovalDark()=row(true)
 @Test fun directRemovalLargeText()=row(true,large=true)
 @Test fun partyLockDisablesRemove()=row(true,locked=true)
 @Test fun liftedDragCopyHasNoRemoveTarget()=row(true,lifted=true)
}

class QueueAdjacentRegressionTest:PlayerGesturePagingTestBase(){
 private fun mount(content:@Composable ()->Unit){
  val fixture=V6ScreenshotTest();fixture.installUiHost();fixture.installVideoStubHost()
  compose.setContent{CompositionLocalProvider(LocalPlayerFlat provides true,LocalPlayerPageGestures provides true){BitChordTheme(darkTheme=true){Box(Modifier.width(390.dp)){content()}}}}
  settle()
 }
 private fun song(id:String="one",entry:String="entry-one",tier:com.music.bitchord.data.model.QueueTier=com.music.bitchord.data.model.QueueTier.CONTEXT)=com.music.bitchord.data.model.Song(id,"Song $entry","Artist",null,queueEntryId=entry,queueTier=tier)
 @Test fun realPointerTapRemovesOnceWithoutPlaying(){
  var removes=0;var plays=0
  mount{InlineQueueRow(song(),false,{plays++},{removes++},draggable=true)}
  compose.onNodeWithTag("queue-row-remove-entry-one",useUnmergedTree=true).performTouchInput{click()};settle()
  assertEquals(1,removes);assertEquals(0,plays)
 }
 @Test fun rowBodyTapStillPlaysWithoutRemoving(){
  var removes=0;var plays=0
  mount{InlineQueueRow(song(),false,{plays++},{removes++},draggable=true)}
  compose.onNodeWithText("Song entry-one").performTouchInput{click()};settle()
  assertEquals(0,removes);assertEquals(1,plays)
 }
 @Test fun liveLockAndUnlockUpdateButtonAndRow(){
  var locked by mutableStateOf(false);var removes=0;var plays=0
  mount{InlineQueueRow(song(),false,{plays++},{removes++},locked=locked,draggable=!locked)}
  compose.runOnIdle{locked=true};settle()
  val control=compose.onNodeWithTag("queue-row-remove-entry-one",useUnmergedTree=true)
  control.assertIsNotEnabled().performTouchInput{click()}
  compose.onNodeWithText("Song entry-one").performTouchInput{click()};settle()
  assertEquals(0,removes);assertEquals(0,plays)
  compose.runOnIdle{locked=false};settle()
  control.assertIsEnabled().performTouchInput{click()};settle();assertEquals(1,removes)
 }
 @Test fun lockTakenBetweenPointerDownAndUpDoesNotRemove(){
  var locked by mutableStateOf(false);var removes=0
  mount{InlineQueueRow(song(),false,{},{removes++},locked=locked,draggable=!locked)}
  val control=compose.onNodeWithTag("queue-row-remove-entry-one",useUnmergedTree=true)
  control.performTouchInput{down(center)}
  compose.runOnIdle{locked=true};settle()
  control.performTouchInput{up()};settle();assertEquals(0,removes)
 }
 @Test fun recompositionUsesLatestRemovalCallback(){
  var useSecond by mutableStateOf(false);var first=0;var second=0
  mount{InlineQueueRow(song(),false,{},if(useSecond)({second++})else({first++}),draggable=true)}
  compose.runOnIdle{useSecond=true};settle()
  compose.onNodeWithTag("queue-row-remove-entry-one",useUnmergedTree=true).performTouchInput{click()};settle()
  assertEquals(0,first);assertEquals(1,second)
 }
 @Test fun realHandleDragDoesNotRemoveOrPlay(){
  var removes=0;var plays=0;var starts=0;var ends=0;var distance=0f
  mount{InlineQueueRow(song(),false,{plays++},{removes++},draggable=true,onDragStart={starts++},onDrag={distance+=it},onDragEnd={ends++})}
  compose.onNodeWithContentDescription("Drag to reorder",useUnmergedTree=true).performTouchInput{
   down(center);advanceEventTime(32);moveBy(Offset(0f,18f));advanceEventTime(32);moveBy(Offset(0f,24f));advanceEventTime(32);up()
  };settle()
  assertEquals(1,starts);assertEquals(1,ends);assertTrue(distance>0f);assertEquals(0,removes);assertEquals(0,plays)
 }
 @Test fun duplicateSongsRemoveOnlyAddressedQueueEntryAcrossSections(){
  var queue by mutableStateOf(listOf(song("now","now"),song("dup","user",com.music.bitchord.data.model.QueueTier.USER_QUEUE),song("dup","context"),song("dup","auto",com.music.bitchord.data.model.QueueTier.AUTOPLAY)))
  val removed=mutableListOf<String?>();var jumps=0
  mount{Box(Modifier.height(650.dp)){InlineQueue(queue=queue,currentIndex=0,autoplayEnabled=true,controlsLocked=false,onJumpTo={jumps++},onMove={_,_->},onClear={},onRemove={index->removed+=queue[index].queueEntryId;queue=queue.filterIndexed{i,_->i!=index}},tabbed=true,sideGutter=14.dp)}}
  for(entry in listOf("user","context","auto")){
   compose.onNodeWithTag("queue-row-remove-$entry",useUnmergedTree=true).performTouchInput{click()};settle()
   assertEquals(entry,removed.last());assertEquals(0,jumps)
  }
  assertEquals(listOf("now"),queue.map{it.queueEntryId});assertEquals(listOf("user","context","auto"),removed)
  compose.onNodeWithTag("queue-row-menu-now",useUnmergedTree=true).assertIsDisplayed()
 }
}

class PlayerGesturePagingTest_materialDark:PlayerGesturePagingTestBase(){@Test fun check()=verify(true,material=true)}
class PlayerGesturePagingTest_materialLight:PlayerGesturePagingTestBase(){@Test fun check()=verify(false,material=true)}
