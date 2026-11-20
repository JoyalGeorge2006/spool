package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import dev.chrisbanes.haze.HazeState

/** Only the player's overflow opts in; ordinary held-row menus keep their layout. */
internal val LocalSpoolPlayerMenu = staticCompositionLocalOf { false }

/**
 * The player overflow's sheet. Inline, so over a page it frosts through the
 * page's haze while Blur effects is on; over the player it is composed inside
 * the player's own window, which has no haze source — the caller passes null
 * there and the sheet keeps the same solid fill the window sheet drew.
 */
@Composable
fun SpoolPlayerOverflow(
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
    /** Focused again when the sheet leaves composition - the opener, usually. */
    restoreFocusTo: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    InlineFrostSheet(
        onDismiss = onDismiss,
        hazeState = hazeState,
        restoreFocusTo = restoreFocusTo,
        paneTitle = stringResource(R.string.more),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = if (dark) Color(0xFF1B1B1B) else Color.White,
        contentColor = if (dark) Color(0xFFF1F1F1) else Color(0xFF1B1B1B),
        maxHeightFraction = 0.86f,
    ) {
        CompositionLocalProvider(LocalSpoolPlayerMenu provides true) {
            Box(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.86f)) {
                content()
            }
        }
    }
}
