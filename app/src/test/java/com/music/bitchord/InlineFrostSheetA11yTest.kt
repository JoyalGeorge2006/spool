package com.music.bitchord

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import dev.chrisbanes.haze.HazeState
import com.music.bitchord.ui.components.InlineFrostSheet
import com.music.bitchord.ui.components.hiddenBehindSheet
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Interaction and accessibility contract of the inline sheet, the checks a
 * window sheet got for free: the covered page leaves the semantics tree while
 * a sheet is up (TalkBack cannot reach it), the sheet carries its pane title,
 * a tap outside dismisses, and back dismisses - after which the page is
 * reachable again.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-mdpi")
class InlineFrostSheetA11yTest {
    @get:Rule val compose = createComposeRule()

    private var backPress: (() -> Unit)? = null

    // Existence is probed through fetchSemanticsNode, the helper every
    // other test in the tree uses (a member of SemanticsNodeInteraction in
    // ui-test 1.7.6, so no import exists or is needed). The catch is the
    // specific not-found assertion fetchSemanticsNode throws; anything
    // else still fails the test.
    private fun SemanticsNodeInteraction.exists(): Boolean = try {
        fetchSemanticsNode()
        true
    } catch (e: AssertionError) {
        false
    }

    // True composition removal only: for a composable that left the tree
    // entirely (the sheet after dismiss), never for hidden-but-present
    // nodes, which hiddenBehindSheet produces on purpose.
    private fun SemanticsNodeInteraction.assertRemoved() =
        org.junit.Assert.assertFalse("expected no matching semantics node", exists())

    private fun SemanticsNode.containsText(text: String): Boolean =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == text } ||
            children.any { it.containsText(text) }

    private fun scene() {
        compose.setContent {
            BitChordTheme {
                val backOwner = LocalOnBackPressedDispatcherOwner.current
                SideEffect { backPress = { backOwner?.onBackPressedDispatcher?.onBackPressed() } }
                val haze = remember { HazeState() }
                var open by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.hiddenBehindSheet(open)) { Text("page body") }
                    if (open) {
                        InlineFrostSheet(
                            onDismiss = { open = false },
                            hazeState = haze,
                            paneTitle = "Test sheet",
                        ) { Text("sheet body") }
                    }
                }
            }
        }
    }

    @Test
    fun coveredPageMarkedInvisibleToAccessibility() {
        scene()
        compose.waitForIdle()
        // hiddenBehindSheet marks the covered page invisibleToUser: the
        // nodes stay in the semantics tree but are excluded from what
        // accessibility services expose. Assert the marker and that it
        // wraps the page body; tree nonexistence would be the wrong claim.
        val hidden = SemanticsMatcher("marked invisible to user") {
            SemanticsProperties.InvisibleToUser in it.config
        }
        val hiddenNodes = compose.onAllNodes(hidden, useUnmergedTree = true).fetchSemanticsNodes()
        org.junit.Assert.assertTrue(
            "the covered page must carry the invisible-to-user marker",
            hiddenNodes.any { it.containsText("page body") },
        )
        compose.onNodeWithText("sheet body").assertIsDisplayed()
        // Throws when the pane-title carrier is missing: existence check.
        compose.onNode(
            SemanticsMatcher("carries the pane title") { SemanticsProperties.PaneTitle in it.config },
        ).fetchSemanticsNode()
    }

    @Test
    fun tapOutsideDismissesAndPageReturns() {
        scene()
        compose.waitForIdle()
        // The scrim above the sheet, well clear of the sheet's rounded top.
        compose.onRoot().performTouchInput { click(Offset(centerX, 30f)) }
        compose.waitForIdle()
        compose.onNodeWithText("sheet body").assertRemoved()
        compose.onNodeWithText("page body").assertIsDisplayed()
        org.junit.Assert.assertTrue(
            "no invisible-to-user marker may remain after dismiss",
            compose.onAllNodes(
                SemanticsMatcher("marked invisible to user") {
                    SemanticsProperties.InvisibleToUser in it.config
                },
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun backDismissesAndPageReturns() {
        scene()
        compose.waitForIdle()
        compose.runOnUiThread { backPress?.invoke() }
        compose.waitForIdle()
        compose.onNodeWithText("sheet body").assertRemoved()
        compose.onNodeWithText("page body").assertIsDisplayed()
    }
}
