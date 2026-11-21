package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.auth.YouTubeProfile
import com.music.bitchord.ui.icons.SfIcons

/** Account pages stay in Settings; opening one never changes the active session. */
@Composable
internal fun AccountSettingsPage(
    managing: Boolean,
    accounts: List<GoogleAccountSession>,
    activeAccountId: String?,
    activeProfileId: String?,
    onSelect: (GoogleAccountSession, YouTubeProfile) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (GoogleAccountSession) -> Unit,
    contentPadding: PaddingValues,
) {
    var removing by remember { mutableStateOf<GoogleAccountSession?>(null) }
    SpoolSettingsSurface {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(contentPadding)) {
            SettingsPageTitle(stringResource(if (managing) R.string.manage_accounts else R.string.acct_switch_profile))
            if (accounts.isEmpty()) {
                SettingsGroup(header = "YouTube Music", eyebrow = true, topSpacing = 0.dp) {
                    SettingsRow(title = stringResource(R.string.acct_no_saved), subtitle = stringResource(R.string.acct_no_saved_subtitle))
                }
            }
            accounts.forEach { account ->
                SettingsGroup(header = account.email.ifBlank { account.name.ifBlank { "YouTube Music" } }, eyebrow = true) {
                    if (managing) {
                        SettingsRow(title = account.name.ifBlank { stringResource(R.string.youtube_music_account) },
                            subtitle = stringResource(if (account.accountId == activeAccountId) R.string.current_account else R.string.acct_saved_account))
                        RowDivider()
                        DestructiveRow(label = stringResource(R.string.acct_remove_account), onClick = { removing = account })
                    } else {
                        account.profiles.forEachIndexed { index, profile ->
                            if (index > 0) RowDivider()
                            val selected = account.accountId == activeAccountId && profile.profileId == activeProfileId
                            SettingsRow(title = profile.name, subtitle = profile.handle.ifBlank { stringResource(if (profile.isBrandAccount) R.string.brand_account else R.string.acct_personal_profile) },
                                trailing = if (selected) ({ Row { Text(stringResource(R.string.selected), color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(6.dp)); Text("✓", color = MaterialTheme.colorScheme.onSurface) } }) else null,
                                onClick = { onSelect(account, profile) })
                        }
                        if (account.profiles.isEmpty()) SettingsRow(title = stringResource(R.string.acct_profiles_unavailable), subtitle = stringResource(R.string.acct_profiles_unavailable_subtitle))
                    }
                }
            }
            SettingsGroup { SettingsRow(title = stringResource(R.string.add_account), onClick = onAddAccount) }
            Spacer(Modifier.height(24.dp))
        }
        removing?.let { candidate ->
            AlertDialog(onDismissRequest = { removing = null },
                title = { Text(stringResource(R.string.acct_remove_account_q)) },
                text = { Text(stringResource(R.string.acct_remove_body, candidate.email.ifBlank { candidate.name.ifBlank { stringResource(R.string.acct_this_account) } })) },
                confirmButton = { TextButton(onClick = { removing = null; onRemoveAccount(candidate) }) { Text(stringResource(R.string.remove)) } },
                dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** A profile summary is navigation, never a session change. */
@Composable
internal fun ProfileSettingsPage(
    signedIn: Boolean,
    profileName: String?,
    accountName: String?,
    accountEmail: String?,
    onSwitchProfile: () -> Unit,
    onAddAccount: () -> Unit,
    onManageAccounts: () -> Unit,
    contentPadding: PaddingValues,
) {
    SpoolSettingsSurface {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(contentPadding)) {
            SettingsPageTitle(stringResource(R.string.acct_profile_header))
            SettingsGroup(topSpacing = 0.dp) {
                SettingsRow(iconTile = false, monochromeBadge = true, showIconInDetail = true,
                    icon = SfIcons.Person,
                    title = profileName?.takeIf { signedIn && it.isNotBlank() }
                        ?: accountName?.takeIf { signedIn && it.isNotBlank() } ?: stringResource(R.string.not_signed_in),
                    subtitle = accountEmail?.takeIf { signedIn && it.isNotBlank() }
                        ?: stringResource(if (signedIn) R.string.youtube_music_account else R.string.acct_add_account_subtitle))
            }
            SettingsGroup {
                SettingsRow(title = stringResource(R.string.acct_switch_profile), enabled = signedIn, onClick = onSwitchProfile)
                RowDivider()
                SettingsRow(title = stringResource(R.string.add_account), onClick = onAddAccount)
                RowDivider()
                SettingsRow(title = stringResource(R.string.manage_accounts), enabled = signedIn, onClick = onManageAccounts)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
