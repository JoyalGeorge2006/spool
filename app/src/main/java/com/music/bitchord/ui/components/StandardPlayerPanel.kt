package com.music.bitchord.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.icons.SfIcons
import dev.chrisbanes.haze.HazeState

/** Shared divider-free Android player-panel shell. Uses the same inline sheet
 * gesture, inset, focus and blur gate as the existing menu and sleep timer.
 */
@Composable
internal fun StandardPlayerPanel(
    hazeState: HazeState,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    doneLabel: String? = null,
    additionalContrastInk: List<Color> = emptyList(),
    pressedOverlayAlpha: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    InlineFrostSheet(
        onDismiss = onDismiss,
        hazeState = hazeState,
        modifier = modifier,
        paneTitle = title,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        maxHeightFraction = 0.86f,
        frostStyle = playerPanelFrostStyle(
            scheme.surface, scheme.onSurface, additionalContrastInk, pressedOverlayAlpha,
        ),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            PlayerPanelHeader(title, subtitle, onDismiss)
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                content = content,
            )
            if (doneLabel != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    ApprovedPanelAction(label = doneLabel, onClick = onDismiss)
                }
            }
        }
    }
}

/** Matches the existing sleep-timer title/back/subtitle geometry. */
@Composable
internal fun PlayerPanelHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(SfIcons.ArrowBack, stringResource(R.string.back), tint = ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp, lineHeight = 21.sp,
                    letterSpacing = (-0.3).sp, fontWeight = FontWeight.SemiBold,
                ),
                color = ink,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 14.sp, lineHeight = 17.sp, color = ink.copy(alpha = 0.85f))
            }
        }
    }
}
