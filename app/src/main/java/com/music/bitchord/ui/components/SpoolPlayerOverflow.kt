package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Only the player's overflow opts in; ordinary held-row menus keep their layout. */
internal val LocalSpoolPlayerMenu = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoolPlayerOverflow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = if (dark) Color(0xFF1C1A20) else Color.White,
        contentColor = if (dark) Color(0xFFF2EFF6) else Color(0xFF14121A),
    ) {
        CompositionLocalProvider(LocalSpoolPlayerMenu provides true) {
            Box(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.86f)) {
                content()
            }
        }
    }
}
