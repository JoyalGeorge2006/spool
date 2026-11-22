package com.music.bitchord.auth

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import com.music.bitchord.data.model.AccountChannel
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.components.SignInFailedPanel
import com.music.bitchord.ui.components.SignInProfileChoicePanel
import com.music.bitchord.ui.components.SignInSuccessPanel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/** The actual full-screen host used by MainActivity. Staging never saves an account. */
@Composable
internal fun SignInWebHost(
    mode: WebSessionMode,
    initialCookie: String?,
    stageWebSession: (CapturedSession, (MainViewModel.StagedSignIn?) -> Unit) -> Job?,
    onWebSession: (CapturedSession, WebSessionMode, (Boolean) -> Unit) -> Job?,
    commitStaged: (MainViewModel.StagedSignIn, AccountChannel?, (Boolean) -> Unit) -> Job?,
    onClose: () -> Unit,
    onFinished: () -> Unit,
    configureWebView: (WebView) -> Unit = {},
) {
    // Raised by "Use this channel", read by the browser as "take the
    // session from the page as it now stands". A counter rather than a
    // flag so a second tap, after a failed first one, is still a new
    // request rather than a value that was already true.
    var captureRequest by remember(mode) { mutableIntStateOf(0) }
    var captureFailed by remember(mode) { mutableStateOf(false) }
    var pageReady by remember(mode) { mutableStateOf(false) }
    var confirmingProfile by remember(mode) { mutableStateOf(false) }
    // Fewer-tap sign-in. A fresh sign-in reads the login by itself, but
    // nothing is saved until it is confirmed: a single-channel account
    // sees "Signing in as ..." for a moment (with a way out) and is then
    // saved; a multi-channel account must pick a channel first. Closing,
    // Cancel and "Not you?" before that point leave saved accounts alone.
    val autoSignIn = mode == WebSessionMode.SIGN_IN
    var attempt by remember(mode) { mutableIntStateOf(0) }
    var pageLoading by remember(mode) { mutableStateOf(false) }
    var pageLoadFailed by remember(mode) { mutableStateOf(false) }
    var manualFallback by remember(mode) { mutableStateOf(false) }
    var staged by remember(mode) { mutableStateOf<MainViewModel.StagedSignIn?>(null) }
    var pickedChannelKey by remember(mode) { mutableStateOf<String?>(null) }
    var saving by remember(mode) { mutableStateOf(false) }
    val attemptGate = remember(mode, attempt) { com.music.bitchord.auth.SignInAttempt() }
    var signInJob by remember(mode) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DisposableEffect(attemptGate) {
        onDispose { attemptGate.close(); signInJob?.cancel() }
    }
    val closeSignIn = {
        attemptGate.close()
        signInJob?.cancel()
        onClose()
    }
    BackHandler(onBack = closeSignIn)

    val finishSignIn = onFinished
    val restartSignIn: () -> Unit = {
        attemptGate.close()
        signInJob?.cancel()
        staged = null
        pickedChannelKey = null
        saving = false
        captureFailed = false
        confirmingProfile = false
        pageReady = false
        pageLoading = false
        pageLoadFailed = false
        manualFallback = false
        captureRequest = 0
        attempt++
    }
    val commitSignIn = { channelKey: String? ->
        staged?.let { found ->
            saving = true
            signInJob = commitStaged(
                found, found.channels.firstOrNull { it.key == channelKey },
            ) { accepted ->
                if (!attemptGate.active) return@commitStaged
                saving = false
                if (accepted) finishSignIn() else {
                    staged = null
                    captureFailed = true
                }
            }
        }
        Unit
    }
    LaunchedEffect(pageReady, attempt) {
        manualFallback = false
        if (autoSignIn && pageReady) {
            delay(8_000L)
            manualFallback = true
        }
    }
    // One channel: a short, interruptible "Signing in as ..." then save.
    // Several: wait for an explicit pick instead (see the panel).
    LaunchedEffect(staged) {
        val found = staged ?: return@LaunchedEffect
        if (needsProfileChoice(found.channels.size)) return@LaunchedEffect
        delay(1_200L)
        commitSignIn(null)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = closeSignIn) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (mode) {
                            WebSessionMode.SIGN_IN -> stringResource(R.string.sign_in_youtube_music)
                            WebSessionMode.SWITCH_CHANNEL -> stringResource(R.string.choose_profile)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (pageReady && !(autoSignIn && !captureFailed && !manualFallback)) {
                        Text(
                            text = if (captureFailed) {
                                 stringResource(R.string.profile_unavailable)
                             } else {
                                 stringResource(R.string.switch_profile_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                if (pageReady && (!autoSignIn || captureFailed || manualFallback) &&
                    staged == null) {
                    TextButton(onClick = {
                        captureFailed = false
                        confirmingProfile = true
                        captureRequest++
                    }, enabled = !confirmingProfile) {
                        Text(
                            if (confirmingProfile) stringResource(R.string.checking)
                            else stringResource(R.string.use_this_profile),
                        )
                    }
                }
            }
            if (autoSignIn && (pageLoading || confirmingProfile) && staged == null && !captureFailed) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            key(attempt) {
            YtMusicLoginScreen(
                mode = mode,
                autoCapture = autoSignIn,
                onLoading = { if (attemptGate.active) pageLoading = it },
                onLoadFailed = {
                    if (attemptGate.active) {
                        attemptGate.close()
                        signInJob?.cancel()
                        staged = null
                        confirmingProfile = false
                        captureFailed = false
                        pageReady = false
                        pageLoading = false
                        pageLoadFailed = true
                    }
                },
                initialCookie = initialCookie,
                configureWebView = configureWebView,
                captureRequest = captureRequest,
                onPageReady = { if (attemptGate.active && !pageLoadFailed) pageReady = it },
                onCaptureUnavailable = {
                    if (attemptGate.active) {
                        confirmingProfile = false
                        captureFailed = true
                    }
                },
                onCaptured = captured@{ session ->
                    if (!attemptGate.active || pageLoadFailed) return@captured
                    signInJob?.cancel()
                    if (mode == WebSessionMode.SIGN_IN) {
                        // Verify and list channels; save nothing yet.
                        confirmingProfile = true
                        signInJob = stageWebSession(session) { found ->
                            if (!attemptGate.active) return@stageWebSession
                            confirmingProfile = false
                            if (found == null) captureFailed = true else staged = found
                        }
                    } else {
                        signInJob = onWebSession(session, mode) { accepted ->
                            if (!attemptGate.active) return@onWebSession
                            confirmingProfile = false
                            if (accepted) onClose() else captureFailed = true
                        }
                    }
                },
            )
            }
            // Sign-in states are drawn over the page, never beside it.
            val found = staged
            if (pageLoadFailed || (autoSignIn && (found != null || captureFailed))) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when {
                        pageLoadFailed -> SignInFailedPanel(
                            onRetry = restartSignIn,
                            onCancel = closeSignIn,
                            loadFailed = true,
                        )
                        found != null && needsProfileChoice(found.channels.size) ->
                            SignInProfileChoicePanel(
                                channels = found.channels,
                                selectedKey = pickedChannelKey,
                                busy = saving,
                                onSelect = { if (!saving) pickedChannelKey = it.key },
                                onContinue = { if (!saving) commitSignIn(pickedChannelKey) },
                            )
                        found != null -> SignInSuccessPanel(
                            name = found.channels.singleOrNull()?.name ?: found.name.ifBlank { found.email },
                            email = found.email,
                            // Nothing is saved yet, so this is a clean way out
                            // for a new and a refreshed account alike.
                            onNotYou = if (saving) null else {
                                {
                                    BrowserSession.clearGoogleCookies()
                                    restartSignIn()
                                }
                            },
                        )
                        else -> SignInFailedPanel(
                            onRetry = restartSignIn,
                            onCancel = closeSignIn,
                        )
                    }
                }
            }
            }
        }
    }

}
