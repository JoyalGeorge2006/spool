package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search

/**
 * The v6 building blocks, shared by every screen the pass touches: flat cream
 * (or ink) fields, cards in surface white, hairlines, pill buttons, chips and
 * the green copy badges. Colours all come from the theme, so light and dark
 * fall out of the one file.
 */

/**
 * The approved page-title style: 22sp semibold Figtree, one tight line — the
 * heading every page's own name sets in, in place of the oversized display
 * style the upstream pages carried.
 */
@Composable
fun v6PageTitleStyle(): TextStyle =
    MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        letterSpacing = (-0.25).sp,
    )

/** A page's own name - "Listen", "Library", "Search", "Saved", "Settings". */
@Composable
fun V6PageTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = v6PageTitleStyle(),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
    )
}

/** A shelf heading with its quiet trailing action ("All", "Recent", "Refresh"). */
@Composable
fun V6SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .padding(horizontal = PAGE_GUTTER, vertical = 10.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** The flat group card rows live in: surface on background, 20dp corners, no shadow. */
@Composable
fun V6Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .padding(horizontal = PAGE_GUTTER)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

/** The separator between rows inside a [V6Card], inset past the artwork. */
@Composable
fun V6Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 82.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** A filter chip: ink-filled when picked, flat gray when not. */
@Composable
fun V6Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

/** A v6 capsule button: primary ink, secondary flat gray. */
@Composable
fun V6PillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(
                // keep the token's own alpha (secondaryContainer is a 6% tint);
                // the disabled dim multiplies it instead of replacing it
                (if (primary) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.secondaryContainer)
                    .let { c -> c.copy(alpha = c.alpha * (if (enabled) 1f else 0.4f)) },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W700,
            color = if (primary) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
    }
}

/** The green "MP3" copy badge; [accent] recolours it (the blue "New"). */
@Composable
fun V6Badge(label: String, modifier: Modifier = Modifier, accent: Color? = null) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val green = accent ?: (if (dark) Color(0xFF64AF4C) else Color(0xFF198100))
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(green.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.W700,
            color = green,
            maxLines = 1,
        )
    }
}

/**
 * The rounded search field from Listen and Search. Read-only here: a tap opens
 * the real search page.
 */
@Composable
fun V6SearchEntry(hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = PAGE_GUTTER)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
