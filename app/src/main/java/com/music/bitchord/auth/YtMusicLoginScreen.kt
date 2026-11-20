package com.music.bitchord.auth

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.music.bitchord.data.DebugLog as Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal const val MUSIC_ORIGIN = "https://music.youtube.com"

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin" +
        "?ltmpl=music&service=youtube&prompt=select_account" +
    "&continue=https%3A%2F%2Fmusic.youtube.com%2F"

private const val TAG = "BitChord"

/**
 * In-app Google sign-in for YouTube Music, and the way to change which channel
 * it listens as.
 *
 * [WebSessionMode.SIGN_IN] loads the standard Google web login with
 * `continue=music.youtube.com`. The user authenticates directly against
 * accounts.google.com (2FA, passkeys etc. all work — it's the real page). When
 * Google redirects back to music.youtube.com the listener confirms the profile
 * shown by the live page. This deliberately leaves a multiple-channel account
 * enough time to choose its Personal or Brand identity before anything is
 * saved. The browser's local Google cookies are cleared on the way in without
 * visiting Google's logout endpoint, so adding an account cannot invalidate a
 * previously saved session.
 *
 * [WebSessionMode.SWITCH_CHANNEL] keeps those cookies and opens YouTube Music
 * itself, so the listener can use the avatar menu's own Accounts list — the one
 * screen that authoritatively knows which channels exist and which is which.
 * Nothing is taken automatically there: the session is read when they say so,
 * by raising [captureRequest].
 *
 * Either way what is read is the page's own `ytcfg`, not a guess made later
 * from a server-side fetch. The credential itself never passes through app code.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtMusicLoginScreen(
    mode: WebSessionMode,
    /** Cookie snapshot to install before opening an existing account's channel picker. */
    initialCookie: String? = null,
    onCaptured: (CapturedSession) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Raise to take the session from the page as it stands. Ignored at its
     * initial value, so arriving on the screen doesn't capture anything.
     */
    captureRequest: Int = 0,
    /** Told when a capture was asked for and there was no session to take. */
    onCaptureUnavailable: () -> Unit = {},
    /** True once a signed-in YouTube Music page is available for confirmation. */
    onPageReady: (Boolean) -> Unit = {},
    /** For a host that needs the WebView set up differently — the TV app, for its remote. */
    configureWebView: (WebView) -> Unit = {},
    /** Read the session by itself once the signed-in page's identity has settled (sign-in only). */
    autoCapture: Boolean = false,
    /** Whether a page is loading, for the host's progress indicator. */
    onLoading: (Boolean) -> Unit = {},
    /** Main document could not load. The host offers explicit Retry or Cancel. */
    onLoadFailed: () -> Unit = {},
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val pageGate = remember { SignInPageGate() }
    var pageGeneration = 0
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnUnavailable by rememberUpdatedState(onCaptureUnavailable)
    val currentOnPageReady by rememberUpdatedState(onPageReady)
    val currentOnLoading by rememberUpdatedState(onLoading)
    val currentOnLoadFailed by rememberUpdatedState(onLoadFailed)

    LaunchedEffect(captureRequest) {
        if (captureRequest == 0) return@LaunchedEffect
        val view = webView
        if (view == null) currentOnUnavailable()
        else {
            val token = pageGate.token()
            if (!pageGate.accepts(token)) return@LaunchedEffect
            captureFrom(view,
                { if (pageGate.accepts(token)) currentOnCaptured(it) },
                { if (pageGate.accepts(token)) currentOnUnavailable() },
            )
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            if (mode == WebSessionMode.SIGN_IN) {
                BrowserSession.clearGoogleCookies()
            } else initialCookie?.let(BrowserSession::installGoogleCookies)
            WebView(context).apply {
                if (mode == WebSessionMode.SIGN_IN) {
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                configureWebView(this)

                var handedOff = false
                var polling = false
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        // Only the host's explicit Retry creates a new attempt.
                        if (pageGate.loadFailed) return
                        pageGeneration = pageGate.next()
                        polling = false
                        currentOnPageReady(false)
                        currentOnLoading(true)
                    }

                    private fun failDocument(view: WebView?) {
                        if (pageGate.loadFailed) return
                        pageGate.failMainFrame()
                        polling = false
                        view?.stopLoading()
                        // Native WebView error content must not cover the host's retry panel.
                        view?.visibility = View.INVISIBLE
                        currentOnPageReady(false)
                        currentOnLoading(false)
                        currentOnLoadFailed()
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame != true) return
                        failDocument(view)
                    }

                    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                        // Keep access/authentication4xx explanations on the actual page.
                        // A broken image/script must not replace a usable login either.
                        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) in 500..599) {
                            failDocument(view)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (pageGate.loadFailed || view?.url != url) return
                        currentOnLoading(false)
                        // Reaching the Music origin only enables confirmation.
                        // A multi-channel login can still be waiting for the
                        // listener to choose an identity on this very page.
                        // Capturing automatically here is the race that used to
                        // create a fake "Personal" profile and close too soon.
                        currentOnPageReady(isMusicOriginUrl(url))
                        // Fresh sign-in only. A fixed delay is not "settled": the
                        // page's identity is read repeatedly and handed on only
                        // after the same signed-in identity comes back twice in a
                        // row. Anything not yet signed in just resets the count.
                        // After the reads run out the host's manual fallback is
                        // all that is left; nothing is ever guessed.
                        if (autoCapture && view != null && !polling &&
                            shouldAutoCapture(mode, url, handedOff)
                        ) {
                            polling = true
                            val capturedGeneration = pageGeneration
                            var tries = 0
                            var last: String? = null
                            var stable = 0
                            lateinit var poll: Runnable
                            poll = Runnable {
                                if (!pageGate.accepts(capturedGeneration) || !isMusicOriginUrl(view.url) || handedOff) { polling = false; return@Runnable }
                                tries++
                                readSession(view) { read ->
                                    if (!pageGate.accepts(capturedGeneration)) return@readSession
                                    val step = nextStability(last, stable, read?.let(::autoIdentityOf))
                                    last = step.first
                                    stable = step.second
                                    if (read != null && stable >= AUTO_STABLE_READS && !handedOff) {
                                        handedOff = true
                                        polling = false
                                        currentOnCaptured(read)
                                    } else if (tries < AUTO_POLL_MAX && !handedOff) {
                                        view.postDelayed(poll, AUTO_POLL_MS)
                                    } else polling = false
                                }
                            }
                            view.postDelayed(poll, AUTO_POLL_MS)
                        }
                    }
                }

                webView = this
                loadUrl(if (mode == WebSessionMode.SIGN_IN) LOGIN_URL else "$MUSIC_ORIGIN/")
            }
        },
        onRelease = { view ->
            pageGate.close()
            webView = null
            view.stopLoading()
            view.destroy()
        },
    )
}

/**
 * Takes the session from [view], if it is holding one.
 *
 * @return whether there was one to take. False means the cookie jar has no
 *   signing secret in it yet — the page is mid-login, or is not a YouTube page
 *   at all — and the caller should leave the screen open rather than saving
 *   something that cannot sign a request. See [AuthStore.hasApiSid].
 */
private fun captureFrom(
    view: WebView,
    onCaptured: (CapturedSession) -> Unit,
    onUnavailable: () -> Unit,
) {
    readSession(view) { session ->
        if (session == null) onUnavailable() else onCaptured(session)
    }
}

/** Reads the session from [view] without keeping anything; null when there is none to read yet. */
private fun readSession(view: WebView, onResult: (CapturedSession?) -> Unit) {
    val captureUrl = view.url
    if (!isMusicOriginUrl(captureUrl)) { onResult(null); return }
    val cookies = CookieManager.getInstance().getCookie(MUSIC_ORIGIN)
    if (cookies == null || !AuthStore.hasApiSid(cookies)) {
        onResult(null)
        return
    }
    CookieManager.getInstance().flush()

    view.evaluateJavascript(YTCFG_PROBE) { raw ->
        if (view.url != captureUrl || !isMusicOriginUrl(view.url)) { onResult(null); return@evaluateJavascript }
        val config = raw.parseConfig()
        val loggedIn = config?.get("loggedIn").let {
            it is JsonPrimitive && it.content == "true"
        }
        if (config == null || !loggedIn) {
            Log.w(TAG, "confirmation requested before the page exposed a signed-in identity")
            onResult(null)
            return@evaluateJavascript
        }
        val pageId = config.string("pageId")
        onResult(
            CapturedSession(
                cookie = cookies,
                // A delegated identity is the most specific answer the live
                // page can give. Otherwise normalise its DATASYNC_ID.
                dataSyncId = pageId ?: normalizeDataSyncId(config.string("dataSyncId")),
                pageId = pageId,
                authUser = config.string("authUser"),
                visitorData = config.string("visitorData"),
                clientVersion = config.string("clientVersion"),
                loggedIn = true,
            ),
        )
    }
}

/**
 * The identity of the page as the page itself has it.
 *
 * Returns an object rather than a string so the WebView serialises it — a
 * probe that stringified its own result would come back double-encoded. A page
 * without `ytcfg` (an error page, a redirect that hasn't landed) returns null,
 * which is a fine answer and not an error.
 */
private const val YTCFG_PROBE = """
(function () {
  try {
    if (!window.ytcfg || !window.ytcfg.get) return null;
    var get = function (key) {
      var value = window.ytcfg.get(key);
      return (value === undefined || value === null || value === '') ? null : String(value);
    };
    return {
      loggedIn: String(!!window.ytcfg.get('LOGGED_IN')),
      pageId: get('DELEGATED_SESSION_ID'),
      dataSyncId: get('DATASYNC_ID'),
      authUser: get('SESSION_INDEX'),
      visitorData: get('VISITOR_DATA'),
      clientVersion: get('INNERTUBE_CLIENT_VERSION')
    };
  } catch (e) {
    return null;
  }
})()
"""

private val json = Json { ignoreUnknownKeys = true }

/** The probe's result, or null for anything that isn't the object it promises. */
private fun String?.parseConfig(): JsonObject? =
    this?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
