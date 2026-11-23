package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PaletteMode
import com.music.bitchord.R
import dev.chrisbanes.haze.HazeState

/** Only the player's overflow opts in; ordinary held-row menus keep their layout. */
internal val LocalSpoolPlayerMenu = staticCompositionLocalOf { false }

/** Distinguishes the main menu's material from its embedded sleep-timer page. */
internal val LocalSpoolPlayerMainPage = staticCompositionLocalOf<MutableState<Boolean>?> { null }

/** Inline overflow frame sampling the source in its own window. */
@Composable
fun SpoolPlayerOverflow(
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
    /** Focused again when the sheet leaves composition - the opener, usually. */
    restoreFocusTo: FocusRequester? = null,
    /** Keep separately hosted dialogs on their previous source until approved. */
    childHazeState: HazeState? = hazeState,
    content: @Composable () -> Unit,
) {
    val mainPage = remember { mutableStateOf(true) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette by AppSettings.paletteMode.collectAsStateWithLifecycle()
    val themedPage = !mainPage.value || !dark || palette == PaletteMode.MATERIAL_YOU
    InlineFrostSheet(
        onDismiss = onDismiss,
        hazeState = hazeState,
        childHazeState = childHazeState,
        frostStyle = if (mainPage.value) null else sleepMenuFrostStyle(
            MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface,
        ),
        restoreFocusTo = restoreFocusTo,
        paneTitle = stringResource(R.string.more),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = if (themedPage) MaterialTheme.colorScheme.surface else Color(0xFF1B1B1B),
        contentColor = if (themedPage) MaterialTheme.colorScheme.onSurface else Color(0xFFF1F1F1),
        maxHeightFraction = 0.86f,
    ) {
        CompositionLocalProvider(
            LocalSpoolPlayerMenu provides true,
            LocalSpoolPlayerMainPage provides mainPage,
        ) {
            Box(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.86f)) {
                content()
            }
        }
    }
}
