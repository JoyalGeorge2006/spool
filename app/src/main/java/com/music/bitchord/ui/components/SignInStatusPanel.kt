package com.music.bitchord.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.AccountChannel

/*
 * The sign-in sheet's own states, drawn with the same flat tokens as the rest
 * of spool: the theme's background / surface / secondaryContainer / outline,
 * the capsule button of the welcome pages, 12dp hairline cards like the
 * account selector, and no animation beyond what the host adds.
 */

/** "Signed in as <name>", shown briefly after a validated sign-in. */
@Composable
fun SignInSuccessPanel(
    name: String,
    email: String,
    onNotYou: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.signin_success_title, name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (email.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onNotYou != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.signin_not_you),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onNotYou)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Capture or validation failed. Nothing was saved. */
@Composable
fun SignInFailedPanel(
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    loadFailed: Boolean = false,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(if (loadFailed) R.string.signin_load_failed_title else R.string.signin_failed_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (loadFailed) R.string.signin_load_failed_body else R.string.signin_failed_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        SignInPill(stringResource(R.string.signin_try_again), primary = true, onClick = onRetry)
        Spacer(Modifier.height(12.dp))
        SignInPill(stringResource(R.string.signin_cancel), primary = false, onClick = onCancel)
    }
}

/** The account has several YouTube channels: nothing is assumed, the listener picks. */
@Composable
fun SignInProfileChoicePanel(
    channels: List<AccountChannel>,
    selectedKey: String?,
    onSelect: (AccountChannel) -> Unit,
    onContinue: () -> Unit,
    busy: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.signin_choose_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(channels, key = { it.key }) { channel ->
                val selected = channel.key == selectedKey
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
                            shape,
                        )
                        .clickable(enabled = !busy) { onSelect(channel) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        if (!channel.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (channel.subtitle.isNotBlank()) {
                            Text(
                                text = channel.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SignInPill(stringResource(if (busy) R.string.checking else R.string.signin_continue), primary = true, enabled = !busy && selectedKey != null, onClick = onContinue)
    }
}

/** The welcome pages' capsule, minus its press animation. */
@Composable
private fun SignInPill(label: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
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
