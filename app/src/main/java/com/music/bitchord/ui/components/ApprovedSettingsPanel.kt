package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.ui.icons.SfIcons
import dev.chrisbanes.haze.HazeState

/** Opt-in shell for the selected settings forms, not a replacement for every alert. */
@Composable
internal fun ApprovedSettingsPanel(
    hazeState: HazeState,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: () -> Unit = onDismiss,
    backEnabled: Boolean = true,
    dragEnabled: Boolean = true,
    scrollBody: Boolean = true,
    titleModifier: Modifier = Modifier,
    pageSourceKey: Any? = null,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    InlineFrostSheet(
        onDismiss = onDismiss,
        pageSourceKey = pageSourceKey,
        hazeState = hazeState,
        modifier = modifier,
        paneTitle = title,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        maxHeightFraction = 0.86f,
        dragEnabled = dragEnabled,
        // These existing settings hosts own hardware Back. Preserve their guards.
        handleBack = false,
        frostStyle = playerPanelFrostStyle(
            scheme.surface, scheme.onSurface,
            listOf(scheme.onSurface.copy(alpha = 0.85f), scheme.error, scheme.primary), pressedOverlayAlpha = 0.09f,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
        // Header and actions stay pinned at every viewport size. Only the body
        // scrolls, with a stable composition and state when the IME resizes it.
        val bodyScroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(SfIcons.ArrowBack, stringResource(R.string.back), tint = scheme.onSurface.copy(alpha = if (backEnabled) 1f else 0.38f))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, modifier = titleModifier, style = MaterialTheme.typography.titleMedium,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    if (subtitle != null) Text(subtitle, fontSize = 14.sp, lineHeight = 18.sp,
                        color = scheme.onSurface.copy(alpha = 0.85f))
                }
            }
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .then(if (scrollBody) Modifier.verticalScroll(bodyScroll) else Modifier)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = footer,
            )
        }
    }
    }
}

internal enum class ApprovedActionStyle { PRIMARY, SECONDARY, DESTRUCTIVE }

/** Opaque primary and visibly bounded secondary/disabled actions, using the active palette. */
@Composable
internal fun ApprovedPanelAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ApprovedActionStyle = ApprovedActionStyle.PRIMARY,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val primary = style == ApprovedActionStyle.PRIMARY
    val ink = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        style == ApprovedActionStyle.DESTRUCTIVE -> scheme.error
        primary -> scheme.onPrimary
        else -> scheme.onSurface
    }
    val fill = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.12f)
        primary -> scheme.primary
        else -> scheme.surface.copy(alpha = 0.12f)
    }
    val border = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.25f)
        style == ApprovedActionStyle.DESTRUCTIVE -> scheme.error
        primary -> scheme.primary
        else -> scheme.onSurface.copy(alpha = 0.5f)
    }
    Box(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
            .background(fill).border(1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ApprovedFieldLabel(label: String) {
    Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        style = MaterialTheme.typography.labelMedium)
}

@Composable
internal fun ApprovedPreferenceRow(label: String, value: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(value, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp)
    }
}

@Composable
internal fun ApprovedChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge)
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}
