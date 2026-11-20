package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            SettingsPageTitle(if (managing) "Manage accounts" else "Switch profile")
            if (accounts.isEmpty()) {
                SettingsGroup(header = "YouTube Music", eyebrow = true, topSpacing = 0.dp) {
                    SettingsRow(title = "No saved accounts", subtitle = "Add an account to choose a profile.")
                }
            }
            accounts.forEach { account ->
                SettingsGroup(header = account.email.ifBlank { account.name.ifBlank { "YouTube Music" } }, eyebrow = true) {
                    if (managing) {
                        SettingsRow(title = account.name.ifBlank { "YouTube Music account" },
                            subtitle = if (account.accountId == activeAccountId) "Current account" else "Saved account")
                        RowDivider()
                        DestructiveRow(label = "Remove account", onClick = { removing = account })
                    } else {
                        account.profiles.forEachIndexed { index, profile ->
                            if (index > 0) RowDivider()
                            val selected = account.accountId == activeAccountId && profile.profileId == activeProfileId
                            SettingsRow(title = profile.name, subtitle = profile.handle.ifBlank { if (profile.isBrandAccount) "Brand account" else "Personal profile" },
                                trailing = if (selected) ({ Row { Text("Selected", color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(6.dp)); Text("✓", color = MaterialTheme.colorScheme.onSurface) } }) else null,
                                onClick = { onSelect(account, profile) })
                        }
                        if (account.profiles.isEmpty()) SettingsRow(title = "Profiles unavailable", subtitle = "Return to Settings and try again.")
                    }
                }
            }
            SettingsGroup { SettingsRow(title = "Add account", onClick = onAddAccount) }
            Spacer(Modifier.height(24.dp))
        }
        removing?.let { candidate ->
            AlertDialog(onDismissRequest = { removing = null },
                title = { Text("Remove account?") },
                text = { Text("Remove ${candidate.email.ifBlank { candidate.name.ifBlank { "this account" } }} from Spool on this device? This removes its saved sign-in, not the Google account itself.") },
                confirmButton = { TextButton(onClick = { removing = null; onRemoveAccount(candidate) }) { Text("Remove") } },
                dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
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
            SettingsPageTitle("Profile")
            SettingsGroup(topSpacing = 0.dp) {
                SettingsRow(iconTile = false, monochromeBadge = true, showIconInDetail = true,
                    icon = SfIcons.Person,
                    title = profileName?.takeIf { signedIn && it.isNotBlank() }
                        ?: accountName?.takeIf { signedIn && it.isNotBlank() } ?: "Not signed in",
                    subtitle = accountEmail?.takeIf { signedIn && it.isNotBlank() }
                        ?: if (signedIn) "YouTube Music account" else "Add an account to listen with your profile")
            }
            SettingsGroup {
                SettingsRow(title = "Switch profile", enabled = signedIn, onClick = onSwitchProfile)
                RowDivider()
                SettingsRow(title = "Add account", onClick = onAddAccount)
                RowDivider()
                SettingsRow(title = "Manage account", enabled = signedIn, onClick = onManageAccounts)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
