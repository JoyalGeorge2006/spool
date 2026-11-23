package com.music.bitchord

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.settings.AppSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import android.graphics.Bitmap

/** Real app navigation and OS keyboard. No fixture substitutes for the Home callback. */
@RunWith(AndroidJUnit4::class)
class ListenSearchKeyboardTest {
 @get:Rule val compose = createAndroidComposeRule<MainActivity>(
  effectContext = kotlinx.coroutines.test.StandardTestDispatcher())
 private fun ime(): Boolean {
  var visible=false
  compose.runOnUiThread { visible=ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true }
  return visible
 }
 private fun tab(label:String) {
  val matcher=hasClickAction() and (hasContentDescription(label) or hasAnyDescendant(hasContentDescription(label)))
  compose.waitUntil(15000) { compose.onAllNodes(matcher,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
  compose.onAllNodes(matcher,useUnmergedTree=true)[0].performClick()
  compose.waitForIdle()
 }
 private fun shot(name:String) {
  val i=InstrumentationRegistry.getInstrumentation()
  val bmp=requireNotNull(i.uiAutomation.takeScreenshot())
  File(i.targetContext.getExternalFilesDir(null),name+".png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
  bmp.recycle()
 }
 @Test fun listenTapFocusesOnceAndKeepsQueryOnReturn() {
  AppSettings.setOnboardingComplete();AppSettings.setDiwaliGreetingShown()
  compose.waitForIdle()
  tab("Listen")
  // Home exposes a clickable search placeholder rather than an editable field.
  compose.onNodeWithText("Songs, artists, albums").performClick()
  compose.onNode(hasSetTextAction()).assertIsFocused()
  compose.waitUntil(10000) { ime() }
  shot("listen_search_first_keyboard")
  compose.onNode(hasSetTextAction()).performTextInput("keyboard proof")
  Espresso.pressBack()
  compose.waitUntil(10000) { !ime() }
  compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
  assertFalse("Dismissed keyboard must stay dismissed",ime())
  tab("Listen")
  compose.onNodeWithText("Songs, artists, albums").performClick()
  compose.onNode(hasSetTextAction()).assertIsFocused().assertTextContains("keyboard proof")
  compose.waitUntil(10000) { ime() }
  shot("listen_search_repeat_preserved")
  Espresso.pressBack();compose.waitUntil(10000) { !ime() }
  tab("Listen");tab("Search")
  compose.onNode(hasSetTextAction()).assertTextContains("keyboard proof")
  compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
  assertFalse("Bottom Search with nonblank query keeps existing behavior",ime())
  shot("bottom_search_query_no_keyboard")
 }
}
