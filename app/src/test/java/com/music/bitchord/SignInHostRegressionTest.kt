package com.music.bitchord

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.auth.SignInWebHost
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shipped full host, WebView callbacks, real local AuthStore; no Google/network login. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SignInHostRegressionTest {
    @get:Rule val compose = createComposeRule()
    private fun mainRequest() = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse("https://accounts.google.com/ServiceLogin")
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }
    private fun probe(dark: Boolean, exit: String, mode: WebSessionMode = WebSessionMode.SIGN_IN) {
        V6ScreenshotTest().installUiHost()
        val store = AuthStore(ApplicationProvider.getApplicationContext())
        store.cookie = "fixture-cookie-only"
        val sessions = store.sessions
        store.activeAccountId = sessions.single().accountId
        store.activeProfileId = sessions.single().activeProfileId
        val original = listOf(store.cookie, store.sessions.toString(), store.activeAccountId, store.activeProfileId)
        var shown by mutableStateOf(true)
        var stages = 0
        var commits = 0
        var closes = 0
        val views = mutableListOf<WebView>()
        lateinit var back: OnBackPressedDispatcher
        compose.setContent { BitChordTheme(darkTheme = dark) {
            back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            if (shown) SignInWebHost(mode = mode, initialCookie = null,
                stageWebSession = { _, _ -> stages++; null },
                onWebSession = { _, _, _ -> commits++; store.cookie = "unexpected-update"; null },
                commitStaged = { _, _, _ -> commits++; store.cookie = "unexpected-commit"; null },
                onClose = { closes++; shown = false }, onFinished = { fail("failed page must not finish sign-in") },
                configureWebView = { views += it })
            else Text("Returned safely")
        } }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, views.size)
            views.single().webViewClient.onReceivedError(views.single(), mainRequest(), null)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Couldn't load sign-in").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/signin_actual_host_${dark}_${exit}_${mode}.png")
        val old = views.single()
        val oldClient = old.webViewClient
        compose.onNodeWithText("Try again").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, views.size)
            assertNotSame(old, views.last())
            // Late callbacks from the disposed old page cannot re-enable/save.
            oldClient.onPageStarted(old, "https://music.youtube.com/", null)
            oldClient.onPageFinished(old, "https://music.youtube.com/")
            views.last().webViewClient.onReceivedError(views.last(), mainRequest(), null)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Couldn't load sign-in").assertIsDisplayed()
        when (exit) {
            "Close" -> compose.onNodeWithContentDescription("Close").performClick()
            "Cancel" -> compose.onNodeWithText("Cancel").performClick()
            "Back" -> compose.runOnIdle { back.onBackPressed() }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Returned safely").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, closes)
            assertEquals(0, stages)
            assertEquals(0, commits)
            assertEquals(original, listOf(store.cookie, store.sessions.toString(), store.activeAccountId, store.activeProfileId))
        }
    }
    @Test fun actual_host_light_retry_close_preserves_saved_account() = probe(false, "Close")
    @Test fun actual_host_dark_retry_back_preserves_saved_account() = probe(true, "Back")
    @Test fun actual_host_dark_retry_cancel_preserves_saved_account() = probe(true, "Cancel")
    @Test fun channel_picker_error_retry_close_preserves_saved_account() = probe(false, "Close", WebSessionMode.SWITCH_CHANNEL)
}
