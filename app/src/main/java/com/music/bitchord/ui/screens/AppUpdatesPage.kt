package com.music.bitchord.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.IosSwitch
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

@Composable
internal fun AppUpdatesPage(contentPadding: PaddingValues, onContinue: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppUpdateChecker.checkState.collectAsState()
    val info by AppUpdateChecker.available.collectAsState()
    val download by AppUpdateChecker.download.collectAsState()
    val offer by com.music.bitchord.data.UpdateDownloads.offer.collectAsState()
    val choosing by com.music.bitchord.data.UpdateDownloads.offerChecking.collectAsState()
    LaunchedEffect(info) { info?.let { com.music.bitchord.data.UpdateDownloads.prepareOffer(context,it) } }
    val auto by AppSettings.checkUpdatesAutomatically.collectAsState()
    val checked by AppSettings.updatesLastChecked.collectAsState()
    AppUpdatesContent(contentPadding, state, info, download, auto, checked,
        AppUpdateChecker.externallyProvided,
        onCheck = { scope.launch { AppUpdateChecker.check() } },
        onAuto = AppSettings::setCheckUpdatesAutomatically,
        onDownload = { scope.launch { AppUpdateChecker.downloadApk(context) } },
        onInstall = { scope.launch {
            if(AppUpdateChecker.download.value is AppUpdateChecker.DownloadState.Installing) {
                com.music.bitchord.data.UpdateInstaller.continueConfirmation(context);return@launch
            }
            (AppUpdateChecker.download.value as? AppUpdateChecker.DownloadState.Ready)?.let {
                AppUpdateChecker.installApk(context, it.file)
            }
        } }, onCancel = AppUpdateChecker::cancelDownload, onContinue = onContinue,
        onFullDownload = { scope.launch { AppUpdateChecker.useFullDownload(context) } },
        offeredSize = offer?.takeIf { it.first==info?.version }?.second, offerChecking=choosing)
}

@Composable
internal fun AppUpdatesContent(
    contentPadding: PaddingValues,
    state: AppUpdateChecker.CheckState,
    info: AppUpdateChecker.UpdateInfo?,
    download: AppUpdateChecker.DownloadState,
    auto: Boolean,
    lastChecked: Long,
    guarded: Boolean,
    onCheck: () -> Unit,
    onAuto: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit = {},
    onFullDownload: () -> Unit = onDownload,
    offeredSize: Long? = null,
    offerChecking: Boolean = false,
) {
    var confirmStop by remember { mutableStateOf(false) }
    var confirmFull by remember { mutableStateOf(false) }
    val busy = download is AppUpdateChecker.DownloadState.Downloading || download is AppUpdateChecker.DownloadState.Waiting ||
        download is AppUpdateChecker.DownloadState.Preparing || download is AppUpdateChecker.DownloadState.Verifying
    val status = when {
        guarded -> "Separate APK updates"
        download is AppUpdateChecker.DownloadState.Downloading -> "Downloading update"
        download is AppUpdateChecker.DownloadState.Waiting -> "Waiting to download"
        download is AppUpdateChecker.DownloadState.Preparing -> download.message
        download is AppUpdateChecker.DownloadState.Verifying -> "Checking update"
        download is AppUpdateChecker.DownloadState.Installing -> "Android installation"
        download is AppUpdateChecker.DownloadState.Ready -> "Ready to install"
        download is AppUpdateChecker.DownloadState.Failed -> "Update needs attention"
        download is AppUpdateChecker.DownloadState.Fallback -> "Full download needed"
        download is AppUpdateChecker.DownloadState.Cancelled -> "Update stopped"
        state is AppUpdateChecker.CheckState.Checking -> "Checking for updates"
        state is AppUpdateChecker.CheckState.Failed -> "Couldn't check for updates"
        info != null -> "Update available ${info.version}"
        state is AppUpdateChecker.CheckState.UpToDate -> "You're up to date"
        else -> "Not checked yet"
    }
    val last = if (lastChecked == 0L) "Never" else DateFormat.format("d MMM yyyy, h:mm a", Date(lastChecked)).toString()
    SpoolSettingsSurface {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(contentPadding)) {
            SettingsPageTitle("App updates")
            SettingsGroup(header = "Status", eyebrow = true, topSpacing = 0.dp) {
                SettingsRow(title = "Status", subtitle = status)
                RowDivider()
                SettingsRow(title = "Current version", subtitle = BuildConfig.VERSION_NAME)
                RowDivider()
                SettingsRow(title = "Last checked", subtitle = last)
            }
            if (state is AppUpdateChecker.CheckState.Failed && !guarded && download is AppUpdateChecker.DownloadState.Idle) {
                SettingsGroup(header = "Check failed", eyebrow = true) {
                    SettingsRow(title = "Try again", subtitle = state.message, onClick = onCheck)
                }
            }
            if (!guarded && (info != null || download !is AppUpdateChecker.DownloadState.Idle)) {
                SettingsGroup(header = "Update", eyebrow = true,
                    footer = "Downloads continue in the background on Wi-Fi or mobile data, including metered data. Roaming is off. Back does not stop an update. Android asks before installing.") {
                    when (download) {
                        is AppUpdateChecker.DownloadState.Downloading -> {
                            SettingsRow(title = "Downloading update", subtitle = if(download.total>0)
                                "${mb(download.bytes)} of ${mb(download.total)} · ${(download.fraction * 100).toInt()}%" else "${(download.fraction * 100).toInt()}% · Continues in the background")
                            LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is AppUpdateChecker.DownloadState.Waiting -> {
                            SettingsRow(title="Waiting to download",subtitle=download.message)
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is AppUpdateChecker.DownloadState.Preparing -> {
                            SettingsRow(title=download.message,subtitle="You can keep using Spool. Android may defer preparation in the background.")
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is AppUpdateChecker.DownloadState.Verifying -> {
                            SettingsRow(title="Checking update",subtitle="Checking the complete APK, app identity and signing key")
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is AppUpdateChecker.DownloadState.Installing -> {
                            SettingsRow(title="Android installation",subtitle=download.message,subtitleMaxLines=Int.MAX_VALUE)
                            SettingsRow(title="Continue installation",onClick=onInstall)
                        }
                        is AppUpdateChecker.DownloadState.Ready -> SettingsRow(title="Install update",subtitle=download.message.ifBlank { "Download verified. Android will ask for confirmation." },onClick=onInstall)
                        is AppUpdateChecker.DownloadState.Fallback -> {
                            SettingsRow(title="Smaller download unavailable",subtitle=download.message,subtitleMaxLines=Int.MAX_VALUE)
                            RowDivider()
                            SettingsRow(title="Download full update",subtitle=info?.sizeBytes?.let { mb(it) },onClick={confirmFull=true})
                        }
                        is AppUpdateChecker.DownloadState.Failed -> {
                            SettingsRow(title="Update failed",subtitle=download.message,subtitleMaxLines=Int.MAX_VALUE)
                            RowDivider()
                            if(info!=null)SettingsRow(title="Retry download",onClick=onDownload)
                            else SettingsRow(title="Check for updates",onClick=onCheck)
                        }
                        else -> {
                            val selected=offeredSize ?: info?.sizeBytes
                            SettingsRow(title=if(offerChecking)"Checking installed version" else "Download update",
                                subtitle=selected?.let { "${mb(it)}${if(it!=info?.sizeBytes) " patch. Full APK is reconstructed on this phone." else " full APK"}" },
                                onClick=onDownload,enabled=info!=null && !offerChecking)
                            if(offeredSize!=null && offeredSize!=info?.sizeBytes)SettingsRow(title="Download full update instead",subtitle=info?.sizeBytes?.let { mb(it) },onClick={confirmFull=true})
                        }
                    }
                    RowDivider()
                    SettingsRow(title="Continue using Spool",subtitle="Leaves this page without stopping the update",onClick=onContinue)
                    if(busy || download is AppUpdateChecker.DownloadState.Failed || download is AppUpdateChecker.DownloadState.Installing) {
                        RowDivider()
                        DestructiveRow(label="Stop update",onClick={confirmStop=true})
                    }
                }
                if(info!=null)SettingsGroup(header="What's new",eyebrow=true) {
                    SettingsRow(title="Spool ${info.version}",subtitle=info.notes?.ifBlank { "No release notes provided." } ?: "No release notes provided.",subtitleMaxLines=Int.MAX_VALUE)
                }
            }
            SettingsGroup(header = "Updates", eyebrow = true) {
                SettingsRow(title = "Check for updates", enabled = !guarded && state !is AppUpdateChecker.CheckState.Checking,
                    onClick = onCheck)
                RowDivider()
                SettingsRow(title = "Check automatically", subtitle = "When you open Spool", enabled = !guarded,
                    trailing = { IosSwitch(checked = auto, onCheckedChange = onAuto, enabled = !guarded) })
            }
            SettingsGroup(header = "About updates", eyebrow = true) {
                SettingsRow(title = "How updates install", subtitle = if (guarded)
                    "This build receives separate APK updates. In-app downloading stays off until the update channel and install-over path are verified. Install over the existing app; do not uninstall or clear data."
                    else "Spool downloads the update, checks its integrity and signing key, then opens Android's installer. Your sign-in, downloads and settings are meant to stay in place; this must be verified on a real phone.", subtitleMaxLines = Int.MAX_VALUE)
                RowDivider()
                SettingsRow(title = "Update source", subtitle = "GitHub Releases")
            }
            Text("spool ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
        }
        if(confirmStop)AlertDialog(onDismissRequest={confirmStop=false},
            title={Text("Stop this update?")},text={Text("This stops preparation and removes this attempt's download. Spool and your music stay unchanged. Android may already be handling an installation you confirmed.")},
            confirmButton={TextButton(onClick={confirmStop=false;onCancel()}) { Text("Stop update",color=MaterialTheme.colorScheme.error) }},
            dismissButton={TextButton(onClick={confirmStop=false}) { Text("Keep updating") }})
        if(confirmFull)AlertDialog(onDismissRequest={confirmFull=false},
            title={Text("Download the full update?")},text={Text("This needs another ${info?.sizeBytes?.let { mb(it) } ?: "full APK download"}. Data used by a failed patch is additional. Wi-Fi or metered mobile data may be used.")},
            confirmButton={TextButton(onClick={confirmFull=false;onFullDownload()}) { Text("Download full update") }},
            dismissButton={TextButton(onClick={confirmFull=false}) { Text("Not now") }})
    }
}

private fun mb(bytes: Long)=String.format(Locale.getDefault(),"%.2f MB",bytes/1_000_000.0)
