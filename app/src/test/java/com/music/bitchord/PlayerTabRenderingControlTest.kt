@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.music.bitchord.ui.player.PlayerPaneTab
import com.music.bitchord.ui.player.PlayerPaneTabRow
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/** Control only: same production tab row and theme without the player/pager,
 * prewarmed lyrics or artwork layers. Existing whole-player captures remain
 * authoritative for the production path; these controls cannot replace them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="ml-rIN-w390dp-h844dp-mdpi")
class PlayerTabRenderingControlTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(dark: Boolean, english: Boolean=false) {
        val old = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(if(english)"en-US" else "ml-IN"))
        var selected by mutableStateOf<PlayerPaneTab?>(PlayerPaneTab.UP_NEXT)
        try {
            compose.setContent {
                BitChordTheme(darkTheme=dark) {
                    Column(Modifier.width(390.dp).height(780.dp)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                        .testTag("isolated-player-tabs")) {
                        Spacer(Modifier.height(64.dp))
                        PlayerPaneTabRow(selected=selected,onSelect={ selected=it })
                    }
                }
            }
            for (pane in listOf(PlayerPaneTab.UP_NEXT,PlayerPaneTab.LYRICS,PlayerPaneTab.RELATED,null)) {
                compose.runOnIdle { selected=pane }
                compose.waitForIdle()
                compose.onNodeWithTag("isolated-player-tabs").captureRoboImage(
                    "build/outputs/roborazzi/control_tabs_${if(english)"en" else "ml"}_${dark}_${pane?.name ?: "MAIN"}.png",
                    roborazziOptions=RoborazziOptions(taskType=RoborazziTaskType.Record))
            }
        } finally { Locale.setDefault(old) }
    }
    @Test fun light()=capture(false)
    @Test fun dark()=capture(true)
    @Test @Config(sdk=[34], qualifiers="en-rUS-w390dp-h844dp-mdpi")
    fun englishDark()=capture(true,english=true)
}
