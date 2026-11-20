package com.music.bitchord

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import com.music.bitchord.sharedui.resources.*
import com.music.bitchord.ui.utils.guardSheetFromContentTouches
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.ui.components.MiniPlayer
import com.music.bitchord.ui.player.PlayerDock
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.player.PlayerSheetMotion
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual sheet/motion/mini/full composables; fixture media, not phone/provider acceptance. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PlayerTransitionRegressionTest {
    @get:Rule val compose = createComposeRule()
    private fun cycle(dark: Boolean, pane: LastPlayerScreen, journey: Boolean = false, exitOnly: Boolean = false) {
        compose.mainClock.autoAdvance = false
        val fixture = V6ScreenshotTest().also { it.installUiHost() }
        val oldHost = fixture.installVideoStubHost()
        val oldPane = AppSettings.lastPlayerScreen.value
        AppSettings.lastPlayerScreen.value = pane
        lateinit var motion: PlayerSheetMotion
        var shown = false
        var closeLabel = ""
        lateinit var back: OnBackPressedDispatcher
        try {
            compose.setContent { BitChordTheme(darkTheme = dark) {
                var open by remember { mutableStateOf(false) }
                closeLabel = stringResource(Res.string.close_player)
                SideEffect { shown = open }
                val scope = rememberCoroutineScope()
                val dock = remember { PlayerDock() }
                motion = remember { PlayerSheetMotion(scope, dock, { open }, { open = it }) }
                motion.windowInfo = LocalWindowInfo.current
                motion.flingVelocity = with(LocalDensity.current) { 400.dp.toPx() }
                val haze = remember { dev.chrisbanes.haze.HazeState() }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Column(Modifier.fillMaxSize().testTag("underlying-page").padding(20.dp)) {
                        Text("LIBRARY HOST", style = MaterialTheme.typography.headlineSmall)
                        repeat(12) { index ->
                            Text("Library row ${index + 1}", modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                            HorizontalDivider()
                        }
                    }
                    MiniPlayer(song = Song(videoId = "t1", title = "Midnight Rain", artist = "Nova Rae", thumbnailUrl = null),
                        isPlaying = false, isLoading = false, hazeState = haze,
                        onPlayPause = {}, onNext = {}, onPrevious = {},
                        onExpand = motion::open, pull = motion,
                        modifier = Modifier.testTag("mini-player").align(Alignment.BottomCenter).fillMaxWidth())
                }
                if (open) {
                    val density = LocalDensity.current
                    val sheet = remember { SheetState(skipPartiallyExpanded = true, density = density,
                        initialValue = SheetValue.Expanded, confirmValueChange = {
                            if (it == SheetValue.Hidden) { motion.close(); false } else true
                        }, skipHiddenState = false) }
                    DisposableEffect(sheet) {
                        motion.sheet = sheet
                        dock.sheetOffset = motion::position
                        onDispose { dock.sheetOffset = null; motion.onSheetGone() }
                    }
                    ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet,
                        dragHandle = null, sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
                        containerColor = Color.Transparent, scrimColor = Color.Transparent,
                        shape = object : Shape {
                            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                                Outline.Rectangle(Rect(0f, -size.height, size.width, size.height))
                        }, contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
                        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)) {
                        val sheetWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
                        DisposableEffect(sheetWindow) { motion.attachWindow(sheetWindow); onDispose { } }
                        back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                        BackHandler { motion.close() }
                        Box(Modifier.testTag("full-player").fillMaxSize().offset { IntOffset(0, motion.contentOffsetPx()) }
                            .drawBehind {
                                val open = dock.openFraction()
                                if (open > 0f && open < 1f) {
                                    val top = motion.position().takeUnless { it.isNaN() } ?: 0f
                                    drawRect(Color.Black.copy(alpha = 0.32f * open),
                                        topLeft = Offset(0f, -top.toInt().toFloat()), size = Size(size.width, size.height))
                                }
                            }.guardSheetFromContentTouches(sheet)
                            .onSizeChanged { dock.sheetTravel = it.height.toFloat(); motion.laidOut = true }) {
                            fixture.PlayerShot(collapseRequest = motion::close, playing = false)
                        }
                    }
                }
            } }
            repeat(if (exitOnly) 2 else 3) { cycle ->
                if (journey && !exitOnly && cycle == 1) {
                    val mini = compose.onNodeWithTag("mini-player", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    compose.onRoot().performTouchInput {
                        swipe(Offset(mini.center.x, mini.center.y), Offset(mini.center.x, height * .2f), 300)
                    }
                } else compose.onNodeWithTag("mini-player", useUnmergedTree = true).performClick()
                var elapsed = 0L
                for (frame in listOf(16L, 48L, 96L, 160L, 240L, 400L, 800L)) {
                    compose.mainClock.advanceTimeBy(frame - elapsed)
                    elapsed = frame
                    compose.waitForIdle()
                    compose.runOnIdle { println("MOTION_FRAME dark=$dark pane=$pane cycle=$cycle elapsed=$frame holding=${motion.holding} position=${motion.position()} contentOffset=${motion.contentOffsetPx()} shown=$shown") }
                    compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/${if (journey) "journey" else "transition"}_${pane}_${dark}_${cycle}_open_$frame.png")
                }
                while (motion.holding && elapsed < 2400L) {
                    compose.mainClock.advanceTimeBy(32L)
                    elapsed += 32L
                    compose.waitForIdle()
                    compose.runOnIdle { println("OPEN_SETTLE dark=$dark pane=$pane cycle=$cycle elapsed=$elapsed holding=${motion.holding} position=${motion.position()} contentOffset=${motion.contentOffsetPx()}") }
                }
                compose.runOnIdle { assertTrue(shown); assertFalse("Opening did not settle within2400ms", motion.holding) }
                if (!journey && pane != LastPlayerScreen.MAIN) {
                    compose.runOnIdle { assertEquals(LastPlayerScreen.MAIN, AppSettings.lastPlayerScreen.value) }
                }
                if (exitOnly) {
                    diagnostic("before exit $cycle", motion)
                    if (cycle == 0) compose.runOnIdle { back.onBackPressed() }
                    else {
                        val bounds = compose.onNodeWithTag("full-player").fetchSemanticsNode().boundsInRoot
                        val button = compose.onNode(hasContentDescription(closeLabel) and hasAnyAncestor(hasTestTag("full-player"))).fetchSemanticsNode().boundsInRoot
                        compose.onNodeWithTag("full-player").performTouchInput {
                            swipe(Offset(button.center.x - bounds.left, button.center.y - bounds.top), Offset(button.center.x - bounds.left, height * .85f), 350)
                        }
                    }
                } else if (journey) {
                    if (cycle == 0) {
                        visibleText("Lyrics").assertIsDisplayed().performClick()
                        var sectionElapsed = 0L
                        for (at in listOf(16L, 48L, 96L, 160L, 240L, 400L)) {
                            compose.mainClock.advanceTimeBy(at - sectionElapsed)
                            sectionElapsed = at
                            compose.waitForIdle()
                            compose.onNodeWithTag("full-player").captureRoboImage("build/outputs/roborazzi/journey_${dark}_section_open_$at.png")
                        }
                        settleFrames()
                        compose.onNodeWithTag("full-player").captureRoboImage("build/outputs/roborazzi/journey_${dark}_lyrics.png")
                        visibleText("First line of the song").assertIsDisplayed()
                        compose.runOnIdle { assertEquals(LastPlayerScreen.LYRICS, AppSettings.lastPlayerScreen.value) }
                        visibleText("Queue").performClick()
                        settleFrames()
                        compose.onNodeWithTag("full-player").captureRoboImage("build/outputs/roborazzi/journey_${dark}_queue.png")
                        compose.runOnIdle { assertEquals(LastPlayerScreen.QUEUE, AppSettings.lastPlayerScreen.value) }
                        diagnostic("before manual touch", motion)
                        repeat(1) {
                            compose.onNodeWithTag("player-section-header", useUnmergedTree = true).performTouchInput {
                                swipe(Offset(width * .5f, height * .3f), Offset(width * .5f, height * 2.6f), 650)
                            }
                            settleFrames()
                            diagnostic("after manual touch $it", motion)
                        }
                        compose.onNodeWithTag("full-player").captureRoboImage("build/outputs/roborazzi/journey_${dark}_manual_return_main.png")
                        visibleText("Midnight Rain").assertIsDisplayed()
                        compose.runOnIdle { assertEquals(LastPlayerScreen.MAIN, AppSettings.lastPlayerScreen.value) }
                        compose.onNodeWithTag("full-player").performTouchInput {
                            swipe(Offset(width * .9f, height * .25f), Offset(width * .9f, height * .85f), 650)
                        }
                        settleFrames()
                        compose.runOnIdle { assertTrue("content drag at artwork top must not dismiss player", shown) }
                        diagnostic("content top remains open", motion)
                        compose.onNodeWithTag("full-player").captureRoboImage("build/outputs/roborazzi/journey_${dark}_content_top_stays_open.png")
                        compose.onNode(hasContentDescription(closeLabel) and hasAnyAncestor(hasTestTag("full-player"))).performClick()
                    } else if (cycle == 1) {
                        visibleText("Midnight Rain").assertIsDisplayed()
                        compose.runOnIdle {
                            assertEquals(LastPlayerScreen.MAIN, AppSettings.lastPlayerScreen.value)
                            back.onBackPressed()
                        }
                    } else {
                        val button = compose.onNode(hasContentDescription(closeLabel) and hasAnyAncestor(hasTestTag("full-player"))).fetchSemanticsNode().boundsInRoot
                        compose.onNodeWithTag("full-player", useUnmergedTree = true).performTouchInput {
                            swipe(Offset(button.center.x, button.center.y), Offset(button.center.x, height * .85f), 350)
                        }
                    }
                } else compose.runOnIdle { motion.close() }
                elapsed = 0L
                for (frame in listOf(16L, 48L, 96L, 160L, 240L, 400L, 800L)) {
                    compose.mainClock.advanceTimeBy(frame - elapsed)
                    elapsed = frame
                    compose.waitForIdle()
                    compose.runOnIdle { println("MOTION_FRAME dark=$dark pane=$pane cycle=$cycle elapsed=$frame holding=${motion.holding} position=${motion.position()} contentOffset=${motion.contentOffsetPx()} shown=$shown") }
                    compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/${if (journey) "journey" else "transition"}_${pane}_${dark}_${cycle}_close_$frame.png")
                }
                while (shown && elapsed < 2400L) {
                    compose.mainClock.advanceTimeBy(32L)
                    elapsed += 32L
                    compose.waitForIdle()
                    compose.runOnIdle { println("CLOSE_SETTLE dark=$dark pane=$pane cycle=$cycle elapsed=$elapsed holding=${motion.holding} position=${motion.position()} shown=$shown") }
                }
                compose.runOnIdle { assertFalse("Closing did not settle within2400ms", shown) }
                compose.onNodeWithText("LIBRARY HOST").assertIsDisplayed()
            }
        } catch (failure: Throwable) {
            println("PLAYER_JOURNEY_FAILURE dark=$dark pane=$pane journey=$journey shown=$shown")
            dumpTrees()
            runCatching { compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().indices.forEach { index ->
                compose.onAllNodes(isRoot(), useUnmergedTree = true)[index].captureRoboImage("build/outputs/roborazzi/failure_${dark}_${pane}_${journey}_root$index.png")
            }
            }
            throw failure
        } finally {
            AppSettings.lastPlayerScreen.value = oldPane
            PlayerPlatform.install(oldHost)
        }
    }
    private fun dumpTrees() {
        listOf(false, true).forEach { unmerged ->
            compose.onAllNodes(isRoot(), useUnmergedTree = unmerged).fetchSemanticsNodes().indices.forEach { index ->
                println("TREE unmerged=$unmerged root=$index\n" + compose.onAllNodes(isRoot(), useUnmergedTree = unmerged)[index].printToString(maxDepth = 50))
            }
        }
    }
    private fun diagnostic(label: String, motion: PlayerSheetMotion) {
        compose.runOnIdle { println("MOTION $label shownOffset=${motion.position()} contentOffset=${motion.contentOffsetPx()} held=${motion.held} sheetOffset=${runCatching { motion.sheet?.requireOffset() }.getOrNull()}") }
        println("CONTENT $label " + compose.onNodeWithTag("full-player").fetchSemanticsNode().boundsInRoot)
        dumpTrees()
    }
    private fun settleFrames() {
        repeat(12) { compose.mainClock.advanceTimeBy(100L); compose.waitForIdle() }
    }
    private fun visibleText(text: String): SemanticsNodeInteraction {
        val root = compose.onNodeWithTag("full-player").fetchSemanticsNode().boundsInRoot
        return compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag("full-player"))).filterToOne(SemanticsMatcher("inside player window") {
            val b = it.boundsInRoot
            b.width > 0f && b.height > 0f && root.contains(b.center)
        })
    }
    @Test fun pane_scroll_mini_pull_button_back_swipe_light() = cycle(false, LastPlayerScreen.MAIN, true)
    @Test fun pane_scroll_mini_pull_button_back_swipe_dark() = cycle(true, LastPlayerScreen.MAIN, true)
    @Test fun independent_back_and_header_swipe_light() = cycle(false, LastPlayerScreen.MAIN, exitOnly = true)
    @Test fun restored_queue_light() = cycle(false, LastPlayerScreen.QUEUE)
    @Test fun restored_lyrics_dark() = cycle(true, LastPlayerScreen.LYRICS)
    @Test fun main_light() = cycle(false, LastPlayerScreen.MAIN)
}
