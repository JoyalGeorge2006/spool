package com.music.bitchord

import android.net.Uri
import android.view.View
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.auth.YtMusicLoginScreen
import com.music.bitchord.ui.components.SignInFailedPanel
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SignInLoadFailureTest {
    @get:Rule val compose = createComposeRule()
    private fun request(main: Boolean) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse("https://accounts.google.com/ServiceLogin")
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }
    @Test fun main_document_error_hides_webview_and_rejects_finish_and_manual_capture() {
        V6ScreenshotTest().installUiHost()
        var view: WebView? = null
        var failed = 0
        var captured = 0
        var ready = false
        var loading = true
        val capture = mutableIntStateOf(0)
        compose.setContent { BitChordTheme(darkTheme = false) {
            YtMusicLoginScreen(WebSessionMode.SIGN_IN,
                onCaptured = { captured++ }, captureRequest = capture.intValue,
                configureWebView = { view = it }, onLoadFailed = { failed++ },
                onPageReady = { ready = it }, onLoading = { loading = it })
        } }
        compose.waitForIdle()
        compose.runOnIdle {
            val web = requireNotNull(view)
            web.webViewClient.onReceivedError(web, request(false), null)
            assertEquals(0, failed)
            assertEquals(View.VISIBLE, web.visibility)
            web.webViewClient.onReceivedError(web, request(true), null)
            assertEquals(1, failed)
            assertFalse(ready)
            assertFalse(loading)
            assertEquals(View.INVISIBLE, web.visibility)
            web.webViewClient.onPageFinished(web, web.url)
            capture.intValue++
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, captured); assertFalse(ready); assertFalse(loading) }
    }
    @Test fun main_document_server_error_is_recoverable_but_subresource_and_access_pages_stay_visible() {
        V6ScreenshotTest().installUiHost()
        lateinit var web: WebView
        var failed = 0
        var captured = 0
        val capture = mutableIntStateOf(0)
        compose.setContent { BitChordTheme(darkTheme = false) {
            YtMusicLoginScreen(WebSessionMode.SIGN_IN, onCaptured = { captured++ },
                captureRequest = capture.intValue, configureWebView = { web = it }, onLoadFailed = { failed++ })
        } }
        compose.waitForIdle()
        fun response(code: Int) = WebResourceResponse("text/html", "UTF-8", code, "Fixture", emptyMap(), null)
        compose.runOnIdle {
            web.webViewClient.onReceivedHttpError(web, request(false), response(503))
            assertEquals(0, failed)
            assertEquals(View.VISIBLE, web.visibility)
            web.webViewClient.onReceivedHttpError(web, request(true), response(403))
            assertEquals(0, failed)
            assertEquals(View.VISIBLE, web.visibility)
            web.webViewClient.onReceivedHttpError(web, request(true), response(503))
            assertEquals(1, failed)
            assertEquals(View.INVISIBLE, web.visibility)
            web.webViewClient.onReceivedError(web, request(true), null)
            assertEquals("duplicate callbacks must not start another failed attempt", 1, failed)
            web.webViewClient.onPageFinished(web, web.url)
            capture.intValue++
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, captured) }
    }
    private fun panel(dark: Boolean) {
        V6ScreenshotTest().installUiHost()
        var retries = 0
        var cancels = 0
        compose.setContent { BitChordTheme(darkTheme = dark) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Row(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Sign in to YouTube Music", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
                }
                SignInFailedPanel(onRetry = { retries++ }, onCancel = { cancels++ },
                    loadFailed = true, modifier = Modifier.weight(1f))
            }
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Couldn't load sign-in").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/signin_load_failure_${if (dark) "dark" else "light"}.png")
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, cancels) }
    }
    @Test fun load_failure_light() = panel(false)
    @Test fun load_failure_dark() = panel(true)
}
