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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
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
        guarded -> stringResource(R.string.update_status_separate_apk)
        download is AppUpdateChecker.DownloadState.Downloading -> stringResource(R.string.update_downloading)
        download is AppUpdateChecker.DownloadState.Waiting -> stringResource(R.string.update_waiting)
        download is AppUpdateChecker.DownloadState.Preparing -> download.message
        download is AppUpdateChecker.DownloadState.Verifying -> stringResource(R.string.update_verifying)
        download is AppUpdateChecker.DownloadState.Installing -> stringResource(R.string.update_installing)
        download is AppUpdateChecker.DownloadState.Ready -> stringResource(R.string.update_ready)
        download is AppUpdateChecker.DownloadState.Failed -> stringResource(R.string.update_needs_attention)
        download is AppUpdateChecker.DownloadState.Fallback -> stringResource(R.string.update_fallback_needed)
        download is AppUpdateChecker.DownloadState.Cancelled -> stringResource(R.string.update_stopped)
        state is AppUpdateChecker.CheckState.Checking -> stringResource(R.string.update_checking)
        state is AppUpdateChecker.CheckState.Failed -> stringResource(R.string.update_check_failed_status)
        info != null -> stringResource(R.string.update_available_status, info.version)
        state is AppUpdateChecker.CheckState.UpToDate -> stringResource(R.string.update_up_to_date)
        else -> stringResource(R.string.update_not_checked)
    }
    val last = if (lastChecked == 0L) stringResource(R.string.update_never) else DateFormat.format("d MMM yyyy, h:mm a", Date(lastChecked)).toString()
    SpoolSettingsSurface {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(contentPadding)) {
            SettingsPageTitle(stringResource(R.string.app_updates))
            SettingsGroup(header = stringResource(R.string.status), eyebrow = true, topSpacing = 0.dp) {
                SettingsRow(title = stringResource(R.string.status), subtitle = status)
                RowDivider()
                SettingsRow(title = stringResource(R.string.update_current_version), subtitle = BuildConfig.VERSION_NAME)
                RowDivider()
                SettingsRow(title = stringResource(R.string.update_last_checked), subtitle = last)
            }
            if (state is AppUpdateChecker.CheckState.Failed && !guarded && download is AppUpdateChecker.DownloadState.Idle) {
                SettingsGroup(header = stringResource(R.string.update_check_failed_header), eyebrow = true) {
                    SettingsRow(title = stringResource(R.string.try_again), subtitle = state.message, onClick = onCheck)
                }
            }
            if (!guarded && (info != null || download !is AppUpdateChecker.DownloadState.Idle)) {
                SettingsGroup(header = stringResource(R.string.update_update_header), eyebrow = true,
                    footer = stringResource(R.string.update_footer)) {
                    when (download) {
                        is AppUpdateChecker.DownloadState.Downloading -> {
                            SettingsRow(title = stringResource(R.string.update_downloading), subtitle = if(download.total>0)
                                stringResource(R.string.update_download_progress, mb(download.bytes), mb(download.total), (download.fraction * 100).toInt()) else stringResource(R.string.update_download_progress_bg, (download.fraction * 100).toInt()))
                            LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is AppUpdateChecker.DownloadState.Waiting -> {
                            SettingsRow(title=stringResource(R.string.update_waiting),subtitle=download.message)
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is AppUpdateChecker.DownloadState.Preparing -> {
                            SettingsRow(title=download.message,subtitle=stringResource(R.string.update_preparing_subtitle))
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is AppUpdateChecker.DownloadState.Verifying -> {
                            SettingsRow(title=stringResource(R.string.update_verifying),subtitle=stringResource(R.string.update_verifying_subtitle))
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                        }
                        is AppUpdateChecker.DownloadState.Installing -> {
                            SettingsRow(title=stringResource(R.string.update_installing),subtitle=download.message,subtitleMaxLines=Int.MAX_VALUE)
                            SettingsRow(title=stringResource(R.string.update_continue_installation),onClick=onInstall)
                        }
                        is AppUpdateChecker.DownloadState.Ready -> SettingsRow(title=stringResource(R.string.update_install),subtitle=download.message.ifBlank { stringResource(R.string.update_ready_subtitle) },onClick=onInstall)
                        is AppUpdateChecker.DownloadState.Fallback -> {
                            SettingsRow(title=stringResource(R.string.update_smaller_unavailable),subtitle=download.message,subtitleMaxLines=Int.MAX_VALUE)
                            RowDivider()
                            SettingsRow(title=stringResource(R.string.update_download_full),subtitle=info?.sizeBytes?.let { mb(it) },onClick={confirmFull=true})
                        }
                        is AppUpdateChecker.DownloadState.Failed -> {
                            SettingsRow(title=stringResource(R.string.update_failed),subtitle=download.message,subtitleMaxLines=Int.MAX_VALUE)
                            RowDivider()
                            if(info!=null)SettingsRow(title=stringResource(R.string.update_retry),onClick=onDownload)
                            else SettingsRow(title=stringResource(R.string.update_check_now),onClick=onCheck)
                        }
                        else -> {
                            val selected=offeredSize ?: info?.sizeBytes
                            SettingsRow(title=stringResource(if(offerChecking) R.string.update_checking_installed else R.string.update_download_action),
                                subtitle=selected?.let { stringResource(if(it!=info?.sizeBytes) R.string.update_size_patch else R.string.update_size_full, mb(it)) },
                                onClick=onDownload,enabled=info!=null && !offerChecking)
                            if(offeredSize!=null && offeredSize!=info?.sizeBytes)SettingsRow(title=stringResource(R.string.update_download_full_instead),subtitle=info?.sizeBytes?.let { mb(it) },onClick={confirmFull=true})
                        }
                    }
                    RowDivider()
                    SettingsRow(title=stringResource(R.string.update_continue_using),subtitle=stringResource(R.string.update_continue_using_subtitle),onClick=onContinue)
                    if(busy || download is AppUpdateChecker.DownloadState.Failed || download is AppUpdateChecker.DownloadState.Installing) {
                        RowDivider()
                        DestructiveRow(label=stringResource(R.string.update_stop),onClick={confirmStop=true})
                    }
                }
                if(info!=null)SettingsGroup(header=stringResource(R.string.update_whats_new),eyebrow=true) {
                    SettingsRow(title=stringResource(R.string.update_whats_new_title, info.version),subtitle=info.notes?.ifBlank { stringResource(R.string.update_no_notes) } ?: stringResource(R.string.update_no_notes),subtitleMaxLines=Int.MAX_VALUE)
                }
            }
            SettingsGroup(header = stringResource(R.string.updates_header), eyebrow = true) {
                SettingsRow(title = stringResource(R.string.update_check_now), enabled = !guarded && state !is AppUpdateChecker.CheckState.Checking,
                    onClick = onCheck)
                RowDivider()
                SettingsRow(title = stringResource(R.string.update_check_auto), subtitle = stringResource(R.string.update_check_auto_subtitle), enabled = !guarded,
                    trailing = { IosSwitch(checked = auto, onCheckedChange = onAuto, enabled = !guarded) })
            }
            SettingsGroup(header = stringResource(R.string.update_about_header), eyebrow = true) {
                SettingsRow(title = stringResource(R.string.update_how_install), subtitle = if (guarded)
                    stringResource(R.string.update_how_install_guarded)
                    else stringResource(R.string.update_how_install_normal), subtitleMaxLines = Int.MAX_VALUE)
                RowDivider()
                SettingsRow(title = stringResource(R.string.update_source), subtitle = "GitHub Releases")
            }
            Text(stringResource(R.string.spool_version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
        }
        if(confirmStop)AlertDialog(onDismissRequest={confirmStop=false},
            title={Text(stringResource(R.string.update_stop_q))},text={Text(stringResource(R.string.update_stop_body))},
            confirmButton={TextButton(onClick={confirmStop=false;onCancel()}) { Text(stringResource(R.string.update_stop),color=MaterialTheme.colorScheme.error) }},
            dismissButton={TextButton(onClick={confirmStop=false}) { Text(stringResource(R.string.update_keep)) }})
        if(confirmFull)AlertDialog(onDismissRequest={confirmFull=false},
            title={Text(stringResource(R.string.update_full_q))},text={Text(stringResource(R.string.update_full_body, info?.sizeBytes?.let { mb(it) } ?: stringResource(R.string.update_full_fallback)))},
            confirmButton={TextButton(onClick={confirmFull=false;onFullDownload()}) { Text(stringResource(R.string.update_download_full)) }},
            dismissButton={TextButton(onClick={confirmFull=false}) { Text(stringResource(R.string.listen_together_not_now)) }})
    }
}

private fun mb(bytes: Long)=String.format(Locale.getDefault(),"%.2f MB",bytes/1_000_000.0)
