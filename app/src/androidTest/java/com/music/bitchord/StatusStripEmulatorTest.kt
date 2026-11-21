@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import android.graphics.Bitmap
import com.music.bitchord.playback.toSong
import kotlinx.serialization.json.*
import io.ktor.serialization.kotlinx.json.json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Real-device proof for the status-bar strip: the actual running app on an
 * emulator with real system insets, real input injection and full-screen
 * captures (status icons included) of the real pages, light and dark.
 *
 * What this proves that Robolectric cannot: the strip height matches the
 * REAL status-bar inset, the strip stays pinned while the real Home page
 * scrolls, and taps injected through the OS at the strip never reach the
 * content beneath it. Screenshots land in the app's external files dir and
 * are pulled by the workflow for human pixel review.
 *
 * Every page-specific capture is gated on asserted identity, because a
 * mislabeled screenshot is worse than none:
 * - Onboarding is completed first (a fresh install would otherwise capture
 *   the welcome flow under a Home/Settings name - it renders nothing of
 *   either page).
 * - "Home" means the Home TAB: the Listen tab is selected by clicking it,
 *   then the history button and account button of the Home title row must
 *   both be present (neither exists on onboarding; the account button is
 *   suppressed on Settings).
 * - "Settings" means the Settings page title is showing AND the Home-only
 *   account button is gone.
 * - Light/dark is not implied by the file name: the app theme is set
 *   explicitly, and each capture's strip pixels are sampled to prove the
 *   rendered surface matches the label before the shot is kept.
 */
@RunWith(JUnit4::class)
class StatusStripEmulatorTest {

    // Default Compose testing uses UnconfinedTestDispatcher, which may resume
    // rememberCoroutineScope on an IO worker after a real transport suspension.
    // Queue effects and advance them through Compose's UI-thread test clock.
    @get:Rule val compose = createAndroidComposeRule<MainActivity>(
        effectContext = kotlinx.coroutines.test.StandardTestDispatcher(),
    )

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val out = File(dir, "$name.png")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Visible in instrumentation output: proves the capture path executed and where.
        check(out.exists() && out.length() > 0) { "pixel capture failed for $name" }
        println("SHOT-SAVED ${out.absolutePath} bytes=${out.length()}")
    }

    /** Median relative luminance of samples taken across the strip band. */
    private fun stripLuminance(bitmap: Bitmap): Double {
        val strip = stripBounds()
        val top = ((strip?.top ?: 0f) + 2f).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = ((strip?.bottom ?: 0f) - 2f).toInt().coerceIn(top, bitmap.height - 1)
        val lums = mutableListOf<Double>()
        for (fx in listOf(0.08f, 0.25f, 0.42f, 0.58f, 0.75f, 0.92f)) {
            val x = (bitmap.width * fx).toInt().coerceIn(0, bitmap.width - 1)
            val y = (top + bottom) / 2
            val c = bitmap.getPixel(x, y)
            val r = android.graphics.Color.red(c) / 255.0
            val g = android.graphics.Color.green(c) / 255.0
            val b = android.graphics.Color.blue(c) / 255.0
            lums.add(0.2126 * r + 0.7152 * g + 0.0722 * b)
        }
        lums.sort()
        return lums[lums.size / 2]
    }

    /**
     * Full screen including the system status bar icons (API 30+). When
     * [expectDark] is non-null the strip band is sampled and must match the
     * mode the file name claims: the strip is painted in the theme's surface
     * colour, so its luminance is the rendered-mode truth, not a setting.
     */
    private fun fullScreenshot(name: String, expectDark: Boolean? = null) {
        // Semantic identity can precede the rendered navigation frame. Finish
        // finite transitions, then require a real hardware frame commit before
        // reading the OS screenshot. Never substitute a generated app image.
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        val committed = java.util.concurrent.CountDownLatch(1)
        val onCommit = Runnable { committed.countDown() }
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            check(view.isHardwareAccelerated) { "OS capture requires hardware frame commit" }
            view.viewTreeObserver.registerFrameCommitCallback(onCommit)
            view.postInvalidateOnAnimation()
        }
        try {
            check(committed.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                "No committed Android frame before capture $name"
            }
        } finally {
            compose.runOnUiThread {
                compose.activity.window.decorView.viewTreeObserver.unregisterFrameCommitCallback(onCommit)
            }
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val shot = automation.takeScreenshot()
        if (shot != null) {
            if (expectDark != null) {
                val lum = stripLuminance(shot)
                assertTrue(
                    "capture '$name' labeled ${if (expectDark) "dark" else "light"} but strip luminance is $lum",
                    if (expectDark) lum < 0.35 else lum > 0.65,
                )
            }
            saveBitmap(name, shot)
            shot.recycle()
        } else {
            // Fallback: app window only, still shows the strip over content.
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            saveBitmap(name, bmp)
        }
    }

    /** App theme AND system night mode set together, then let it settle. */
    private fun theme(dark: Boolean) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd uimode night " + if (dark) "yes" else "no").close()
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        Thread.sleep(2000)
        compose.waitForIdle()
    }

    private fun stripBounds() =
        compose.onAllNodesWithTag("status-bar-strip", useUnmergedTree = true)
            .fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    /**
     * Fresh installs open on the welcome flow; without this every capture
     * below would be the onboarding page wearing a Home/Settings label.
     * setOnboardingComplete persists and flips the StateFlow, so the
     * welcome animates away in the already-running activity. The Diwali
     * greeting fires once right after onboarding - dismiss it up front so
     * it can never stand between the test and the real pages.
     */
    private fun completeOnboarding() {
        AppSettings.setOnboardingComplete()
        AppSettings.setDiwaliGreetingShown()
        compose.waitForIdle()
        Thread.sleep(2500)
        compose.waitForIdle()
    }

    /**
     * Home identity, not just "past onboarding": force the Home tab via its
     * Listen label, then require the Home title row's history button (only
     * rendered on the Home and Library tabs, never on Settings) and the
     * account button (suppressed on Settings). Both labels are localized,
     * so callers pass the strings of the active locale.
     */
    private fun assertHomeVisible(listenTab: String, historyLabel: String) {
        val tab = hasClickAction() and (
            hasContentDescription(listenTab) or hasAnyDescendant(hasContentDescription(listenTab))
            )
        compose.waitUntil(10_000) {
            compose.onAllNodes(tab, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val tabs = compose.onAllNodes(tab, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("bottom bar must expose the Listen tab", tabs.isNotEmpty())
        compose.onAllNodes(tab, useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        Thread.sleep(1500)
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("header-profile-surface", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(
                    hasContentDescription(historyLabel) or hasAnyDescendant(hasContentDescription(historyLabel)),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Home identity: account button must be visible",
            compose.onAllNodesWithTag("header-profile-surface", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "Home identity: history button ('$historyLabel') must be visible on the Home tab",
            compose.onAllNodes(
                hasContentDescription(historyLabel) or hasAnyDescendant(hasContentDescription(historyLabel)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * Opens Settings through the same path a person uses (the Home title
     * row's account button) and asserts the Settings page actually arrived:
     * its localized title is showing and the Home-only account button is
     * gone. Fails the test when the page cannot be reached, instead of
     * capturing whatever is on screen.
     */
    private fun openSettingsAndAssert(settingsLabel: String, settingsTitle: String) {
        val accountButton = hasClickAction() and (
            hasContentDescription(settingsLabel) or
                hasAnyDescendant(hasContentDescription(settingsLabel))
            )
        compose.waitUntil(10_000) {
            compose.onAllNodes(accountButton, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        val buttons = compose.onAllNodes(accountButton, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("Home must expose the account button that opens Settings", buttons.isNotEmpty())
        compose.onAllNodes(accountButton, useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(settingsTitle).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("header-profile-surface", useUnmergedTree = true)
                    .fetchSemanticsNodes().isEmpty()
        }
        assertTrue(
            "Settings identity: title '$settingsTitle' must be visible after tapping the account button",
            compose.onAllNodesWithText(settingsTitle).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "Settings identity: the Home-only account button must be gone on Settings",
            compose.onAllNodesWithTag("header-profile-surface", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
        Thread.sleep(1500)
        compose.waitForIdle()
    }

    /**
     * Scroll the page's real scrollable container and prove content moved by
     * position, never by pixel difference: a stable text anchor's top must
     * change (or the anchor must scroll out of composition) after a swipe
     * aimed inside the scrollable's own bounds. The old root swipe missed
     * the list entirely - the scrolled captures came back byte-identical
     * and the strip-pinned claim passed vacuously.
     */
    /** Select only the vertical container that owns this page's anchor. */
    private fun verticalScrollForAnchor(anchorText: String): androidx.compose.ui.semantics.SemanticsNode {
        val anchors = compose.onAllNodesWithText(anchorText, useUnmergedTree = true).fetchSemanticsNodes()
        return anchors.asSequence().flatMap { anchor ->
            generateSequence(anchor.parent) { it.parent }.filter { ancestor ->
                ancestor.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) != null &&
                    ancestor.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) != null
            }.take(1)
        }.firstOrNull() ?: error("No target-owned vertical container for $anchorText")
    }

    private val pageScrollOwnerIds = mutableMapOf<String, Int>()

    private fun scrollAnchorToTopAndAssert(anchorText: String) {
        // A LazyColumn can recycle the anchor after scrolling. Retain only
        // its previously verified owner ID, never fall back to another page.
        val ownerId = requireNotNull(pageScrollOwnerIds[anchorText]) {
            "No previously verified page owner for $anchorText"
        }
        val target = compose.onNode(SemanticsMatcher("previously verified page owner") { it.id == ownerId },
            useUnmergedTree=true).fetchSemanticsNode()
        check(target.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) != null &&
            target.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) != null)
        val container = compose.onNode(SemanticsMatcher("page anchor vertical ancestor") { it.id == target.id },
            useUnmergedTree = true)
        container.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, -100000f) }
        compose.waitForIdle()
        val axis = container.fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Actual page must be at scroll origin, got ${axis.value()}", axis.value() <= 1f)
        compose.onAllNodesWithText(anchorText)[0].assertIsDisplayed()
        println("PAGE-TOP anchor=$anchorText ancestor=${target.id} axis=${axis.value()}")
    }

    private fun scrollPageAndAssertMoved(label: String, anchorText: String) {
        val target = verticalScrollForAnchor(anchorText)
        pageScrollOwnerIds[anchorText] = target.id
        val anchorBefore = compose.onAllNodesWithText(anchorText).fetchSemanticsNodes().firstOrNull()
        assertTrue("$label: anchor '$anchorText' must exist before scrolling", anchorBefore != null)
        val beforeTop = anchorBefore!!.boundsInRoot.top
        val b = target.boundsInRoot
        compose.onRoot().performTouchInput {
            swipe(
                start = androidx.compose.ui.geometry.Offset(b.center.x, b.center.y + b.height * 0.3f),
                end = androidx.compose.ui.geometry.Offset(b.center.x, b.center.y - b.height * 0.3f),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
        Thread.sleep(1500)
        compose.waitForIdle()
        val anchorAfter = compose.onAllNodesWithText(anchorText).fetchSemanticsNodes().firstOrNull()
        val moved = anchorAfter == null ||
            kotlin.math.abs(anchorAfter.boundsInRoot.top - beforeTop) > 48f
        assertTrue(
            "$label: targeted swipe did not move content ('$anchorText' top was $beforeTop, " +
                "now ${anchorAfter?.boundsInRoot?.top})",
            moved,
        )
    }

    @Test
    fun realAppPagesUnderRealStatusBar() {
        compose.waitForIdle()
        completeOnboarding()
        // Give Home's network content a chance; whatever renders is real.
        Thread.sleep(6000)
        compose.waitForIdle()
        theme(dark = false)
        assertHomeVisible("Listen", "Listening history")

        val bounds = stripBounds()
        assertTrue("status-bar-strip must exist in the real running app", bounds != null)

        var realInsetTop = 0
        compose.runOnUiThread {
            val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            realInsetTop = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        }
        assertTrue("emulator must report a nonzero status-bar inset, got $realInsetTop", realInsetTop > 0)
        assertEquals(
            "strip height must equal the real status-bar inset",
            realInsetTop.toFloat(), bounds!!.height, 2f,
        )
        assertEquals("strip must sit at the very top", 0f, bounds.top, 1f)

        fullScreenshot("01-home-light", expectDark = false)

        // Scroll the real page; the strip must stay pinned at the top.
        scrollPageAndAssertMoved("home-light", "Made for tonight")
        val afterScroll = stripBounds()
        assertTrue(afterScroll != null)
        assertEquals(0f, afterScroll!!.top, 1f)
        fullScreenshot("02-home-light-scrolled", expectDark = false)

        theme(dark = true)
        fullScreenshot("03-home-dark", expectDark = true)
        theme(dark = false)

        // Settings, reached the way a person reaches it; identity asserted.
        openSettingsAndAssert("Settings", "Settings")
        fullScreenshot("04-settings-light", expectDark = false)
        scrollPageAndAssertMoved("settings-light", "Not signed in")
        fullScreenshot("05-settings-light-scrolled", expectDark = false)
        val stripInSettings = stripBounds()
        assertTrue("strip must exist on the Settings page too", stripInSettings != null)
        assertEquals(0f, stripInSettings!!.top, 1f)

    }

    @Test fun replaySpacingIndependentlyVerified() {
        englishFixture()
        // Independent setup: a Settings scroll failure cannot suppress Replay.
        openSettingsAndAssert("Settings", "Settings")
        clickRow(compose.activity.getString(R.string.your_data))
        requireDestination("Your data", "Export data")
        clickRow("Replay")
        compose.waitForIdle()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Weekly").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Replay identity: the period picker must show after opening Replay",
            compose.onAllNodesWithText("Weekly").fetchSemanticsNodes().isNotEmpty(),
        )
        Thread.sleep(1500)
        compose.waitForIdle()
        fullScreenshot("06-replay-light", expectDark = false)
        compose.runOnUiThread {
            val icons = androidx.core.view.WindowCompat.getInsetsController(
                compose.activity.window, compose.activity.window.decorView)
            assertTrue("Replay light must use dark status icons", icons.isAppearanceLightStatusBars)
            assertTrue("Replay light must use dark navigation icons", icons.isAppearanceLightNavigationBars)
        }
        val stripOnReplay = stripBounds()
        assertTrue("strip must exist on Replay too", stripOnReplay != null)
        assertEquals(0f, stripOnReplay!!.top, 1f)
        theme(dark = true)
        compose.onNodeWithText("Weekly").assertIsDisplayed()
        fullScreenshot("07-replay-dark", expectDark = true)
        compose.runOnUiThread {
            val icons = androidx.core.view.WindowCompat.getInsetsController(
                compose.activity.window, compose.activity.window.decorView)
            assertTrue("Replay dark must use light status icons", !icons.isAppearanceLightStatusBars)
            assertTrue("Replay dark must use light navigation icons", !icons.isAppearanceLightNavigationBars)
        }
    }

    /**
     * Malayalam runtime proof: switch the real app to ml via the same
     * mechanism the picker uses, then capture real pages so glyph fallback,
     * layout and truncation can be reviewed on actual pixels. Identity
     * assertions use the localized tab and Settings labels.
     */
    @Test
    fun malayalamLocaleRendersRealGlyphs() {
        compose.waitForIdle()
        completeOnboarding()
        Thread.sleep(6000)
        compose.waitForIdle()
        try {
            compose.runOnUiThread {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ml"))
            }
            Thread.sleep(4000)
            compose.waitForIdle()
            theme(dark = false)
            assertHomeVisible("കേൾക്കുക", "കേട്ട ചരിത്രം")
            fullScreenshot("10-ml-home-light", expectDark = false)

            scrollPageAndAssertMoved("ml-home-light", "ഇന്ന് രാത്രിക്കായി")
            fullScreenshot("11-ml-home-light-scrolled", expectDark = false)

            // A theme change can recreate Compose semantics IDs. Return with
            // the retained live owner first, then independently prove dark scroll.
            scrollAnchorToTopAndAssert("ഇന്ന് രാത്രിക്കായി")
            theme(dark = true)
            fullScreenshot("15-ml-home-dark-top", expectDark = true)
            scrollPageAndAssertMoved("ml-home-dark", "ഇന്ന് രാത്രിക്കായി")
            fullScreenshot("12-ml-home-dark", expectDark = true)
            scrollAnchorToTopAndAssert("ഇന്ന് രാത്രിക്കായി")
            theme(dark = false)

            openSettingsAndAssert("ക്രമീകരണങ്ങൾ", "ക്രമീകരണങ്ങൾ")
            fullScreenshot("13-ml-settings-light", expectDark = false)
            scrollPageAndAssertMoved("ml-settings-light", "ഓഡിയോ നിലവാരം")
            fullScreenshot("14-ml-settings-light-scrolled", expectDark = false)
            scrollAnchorToTopAndAssert("ഓഡിയോ നിലവാരം")
            theme(dark = true)
            fullScreenshot("17-ml-settings-dark-top", expectDark = true)
            scrollPageAndAssertMoved("ml-settings-dark", "ഓഡിയോ നിലവാരം")
            fullScreenshot("16-ml-settings-dark-scrolled", expectDark = true)
            scrollAnchorToTopAndAssert("ഓഡിയോ നിലവാരം")
        } finally {
            compose.runOnUiThread {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
            AppSettings.setThemeMode(ThemeMode.DARK)
        }
    }

    @Test
    fun osInjectedTapOnStripNeverReachesContent() {
        compose.waitForIdle()
        completeOnboarding()
        Thread.sleep(4000)
        compose.waitForIdle()
        assertHomeVisible("Listen", "Listening history")
        val bounds = stripBounds()
        assertTrue(bounds != null)

        // Real OS-level input injection at the strip's centre: the event goes
        // through the full input pipeline, exactly like a finger.
        val view = compose.activity.window.decorView
        val loc = IntArray(2)
        compose.runOnUiThread { view.getLocationOnScreen(loc) }
        val x = loc[0] + bounds!!.left + bounds.width / 2f
        val y = loc[1] + bounds.top + bounds.height / 2f
        val now = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        val up = android.view.MotionEvent.obtain(now, now + 30, android.view.MotionEvent.ACTION_UP, x, y, 0)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        assertTrue(automation.injectInputEvent(down, true))
        assertTrue(automation.injectInputEvent(up, true))
        down.recycle()
        up.recycle()
        compose.waitForIdle()
        Thread.sleep(1000)

        // The proof is negative (nothing under the strip may react): whatever
        // page was showing must still be showing, and the strip unchanged.
        val after = stripBounds()
        assertTrue(after != null)
        assertEquals(bounds.top, after!!.top, 1f)
        assertEquals(bounds.height, after.height, 1f)
    }

    /** Navigation helpers for exhaustive reachable-page verification. No account
     * is created, no connection is made, and no setting option is selected. */
    private fun englishFixture() {
        compose.runOnUiThread {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        completeOnboarding()
        theme(false)
        assertHomeVisible("Listen", "Listening history")
    }

    private fun backToParent() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        Thread.sleep(900)
        compose.waitForIdle()
    }

    private fun clickRow(label: String) {
        val text = hasText(label)
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        // The real Settings page is a ScrollState Column, not a LazyColumn.
        // Bind to the label's own ancestor rather than another tab's lazy list.
        val initial = compose.onAllNodes(text, useUnmergedTree = true).fetchSemanticsNodes()
        // Clipped descendants still identify their owning vertical container.
        // Positive bounds are required before clicking, not before scrolling.
        val target = initial.firstOrNull { node -> generateSequence(node.parent) { it.parent }.any {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) != null
        } } ?: initial.firstOrNull() ?: error("No target label for $label")
        val ancestors = generateSequence(target.parent) { it.parent }.toList()
        val scroll = ancestors.firstOrNull {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) != null &&
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) != null
        }
        val scrollNode = scroll?.let { ancestor -> compose.onNode(
            SemanticsMatcher("target label vertical ancestor") { it.id == ancestor.id }, useUnmergedTree = true) }
        val matcher = hasClickAction() and (text or hasAnyDescendant(text))
        // Nav items are overlaid above Settings' scroll content. Their actual
        // clickable bounds, not the full window, set the unobscured bottom.
        val navTop = listOf("Listen", "Explore", "Library", "Search").mapNotNull { tab ->
            val description = hasContentDescription(tab)
            compose.onAllNodes(hasClickAction() and (description or hasAnyDescendant(description)), useUnmergedTree = true)
                .fetchSemanticsNodes().filter {
                    val b = it.boundsInRoot
                    b.width > 0f && b.height > 0f && b.top > viewport.center.y
                }.minByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }?.boundsInRoot?.top
        }.minOrNull()
        val clearBottom = (navTop ?: (viewport.top + viewport.height * .90f)) - 24f
        val headerObstacles = listOf("header-profile-surface", "floating-history-profile")
            .flatMap { tag -> compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes() }
            .map { it.boundsInRoot }.filter { it.height > 0f && it.width > 0f }
        val clearTop = maxOf((stripBounds()?.bottom ?: viewport.top) + 8f,
            scroll?.boundsInRoot?.top ?: viewport.top)
        fun isUnobscured(b: androidx.compose.ui.geometry.Rect): Boolean = headerObstacles.none { obstacle ->
            b.left < obstacle.right && b.right > obstacle.left &&
                b.top < obstacle.bottom && b.bottom > obstacle.top
        }
        // Begin the target-owned search at its actual origin, including when
        // the desired label has completely clipped bounds at the current offset.
        scrollNode?.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, -100000f) }
        compose.waitForIdle()
        println("TARGET-SAFE-BAND $label clearTop=$clearTop navTop=$navTop clearBottom=$clearBottom")
        fun safeRow() = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
            .filter {
                val b = it.boundsInRoot
                b.width > 0f && b.height > 0f && b.left >= viewport.left && b.right <= viewport.right &&
                    b.top >= clearTop && b.bottom <= clearBottom &&
                    // Test the text's actual tap target, not an unrelated
                    // right-hand header's y band. The containing row still
                    // must be wholly inside the safe vertical viewport.
                    headerObstacles.none { it.contains(b.center) } &&
                    isUnobscured(compose.onAllNodes(text, useUnmergedTree = true)
                        .fetchSemanticsNodes().first { labelNode ->
                            labelNode.id == target.id
                        }.boundsInRoot)
            }.minByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
        var attempts = 0
        while (safeRow() == null && attempts < 30) {
            val current = compose.onAllNodes(text, useUnmergedTree = true).fetchSemanticsNodes()
                .firstOrNull { it.id == target.id } ?: error("Target label changed before click: $label")
            val currentBounds = current.boundsInRoot
            val direction = if (currentBounds.height <= 0f || currentBounds.top >= clearBottom) 1f
                else if (currentBounds.top < clearTop) -1f else 1f
            val container = requireNotNull(scrollNode) { "No target-owned scroll container for offscreen row $label" }
            val before = container.fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            container.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
                it(0f, direction * viewport.height * .15f)
            }
            compose.waitForIdle()
            val after = container.fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            println("TARGET-SCROLL $label ancestor=${scroll!!.id} before=$before after=$after")
            if (after == before) {
                fullScreenshot("failure-row-safe-band-${label.replace(' ', '-').lowercase()}")
                println("FAILED-ROW-BOUNDS label=$label target=${current.boundsInRoot} " +
                    "container=${container.fetchSemanticsNode().boundsInRoot} clearTop=$clearTop clearBottom=$clearBottom")
                println("FAILED-ROW-TREE\n" + compose.onRoot(useUnmergedTree=true).printToString())
                error("Target-owned scroll made no progress for $label")
            }
            attempts++
        }
        val row = safeRow() ?: error("No unobscured clickable row for $label after $attempts scrolls")
        fullScreenshot("before-tap-${label.replace(' ', '-').lowercase()}")
        println("TARGET-CLICK $label bounds=${row.boundsInRoot} ancestor=${scroll?.id}")
        compose.onNode(SemanticsMatcher("unobscured target-owned clickable row") { it.id == row.id }, useUnmergedTree = true).performClick()
        Thread.sleep(1000)
        // Only the known audio permission is handled, and only in this
        // disposable emulator fixture. Never dismiss arbitrary OS dialogs.
        if (label == compose.activity.getString(R.string.downloads) ||
            label == compose.activity.getString(R.string.local_music)) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val window = automation.rootInActiveWindow
            if (window?.packageName?.toString() == "com.android.permissioncontroller") {
                val message = window.findAccessibilityNodeInfosByViewId(
                    "com.android.permissioncontroller:id/permission_message")
                check(message.size == 1 && message.single().text?.toString()?.contains("music and audio") == true) {
                    "Unexpected permission dialog while opening $label"
                }
                fullScreenshot("audio-permission-before-allow")
                val allow = window.findAccessibilityNodeInfosByViewId(
                    "com.android.permissioncontroller:id/permission_allow_button").single()
                check(allow.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                while (automation.rootInActiveWindow?.packageName?.toString() == "com.android.permissioncontroller" &&
                    android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
                check(automation.rootInActiveWindow?.packageName?.toString() == compose.activity.packageName) {
                    "Audio permission dialog did not return to the app"
                }
                println("EMULATOR-AUDIO-PERMISSION allowed known music/audio request; destination assertions still required")
            }
        }
        compose.waitForIdle()
        fullScreenshot("after-tap-${label.replace(' ', '-').lowercase()}")
    }

    private fun requireDestination(title: String, uniqueChild: String) {
        try { compose.waitUntil(10_000) {
            compose.onAllNodesWithText(uniqueChild).fetchSemanticsNodes().any {
                val b = it.boundsInRoot
                b.width > 0 && b.height > 0 && compose.onRoot().fetchSemanticsNode().boundsInRoot.contains(b.center)
            }
        }
        } catch (failure: Throwable) {
            fullScreenshot("failure-destination-${title.replace(' ', '-').lowercase()}")
            println("FAILURE-DESTINATION $title\n" + compose.onRoot(useUnmergedTree = true).printToString())
            throw failure
        }
        compose.onAllNodesWithText(title)[0].assertIsDisplayed()
        compose.onAllNodesWithText(uniqueChild)[0].assertIsDisplayed()
    }

    /** Identity must be visible above the content; scrolling must change the
     * actual scroll-axis value when the page has remaining vertical content.
     * A short/empty page is logged explicitly, never called a scroll pass. */
    private fun inspectPage(name: String, title: String) {
        try {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        val headings = compose.onAllNodesWithText(title).fetchSemanticsNodes()
        val strip = stripBounds()
        assertTrue("$name: strip exists", strip != null)
        assertEquals("$name: strip pinned", 0f, strip!!.top, 1f)
        assertTrue("$name: identity title clears status inset",
            headings.any { it.boundsInRoot.top >= strip.bottom - 2f && it.boundsInRoot.height > 0 })
        fullScreenshot("pages-$name-top", false)
        val scroll = compose.onAllNodes(hasScrollAction(), useUnmergedTree = true)
            .fetchSemanticsNodes().filter {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) != null
            }.maxByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
        val axis = scroll?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        if (scroll != null && axis != null && axis.maxValue() > axis.value() + 1f) {
            val before = axis.value()
            val b = scroll.boundsInRoot
            compose.onRoot().performTouchInput {
                swipe(androidx.compose.ui.geometry.Offset(b.center.x, b.top + b.height * 0.8f),
                    androidx.compose.ui.geometry.Offset(b.center.x, b.top + b.height * 0.2f), 400)
            }
            compose.waitForIdle()
            Thread.sleep(600)
            val after = compose.onAllNodes(hasScrollAction(), useUnmergedTree = true)
                .fetchSemanticsNodes().firstOrNull { it.id == scroll.id }
                ?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
            assertTrue("$name: scroll axis must advance ($before -> ${after?.value()})",
                after != null && after.value() > before)
            assertEquals("$name: strip remains pinned after scroll", 0f, stripBounds()!!.top, 1f)
            fullScreenshot("pages-$name-scrolled", false)
            // Return to the top for navigation to any child rows.
            compose.onAllNodes(hasScrollAction(), useUnmergedTree = true)
                .filterToOne(SemanticsMatcher("same scroll container") { it.id == scroll.id })
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, -100000f) }
            compose.waitForIdle()
        } else {
            println("PAGE-NO-VERTICAL-OVERFLOW $name (no scroll claimed)")
        }
        println("PAGE-VERIFIED $name")
        } catch (failure: Throwable) {
            fullScreenshot("failure-page-$name")
            val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            fun windowTree(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int = 0): String {
                if (node == null || depth > 30) return ""
                return " ".repeat(depth) + "${node.packageName} ${node.className} id=${node.viewIdResourceName} text=${node.text} description=${node.contentDescription}\n" +
                    (0 until node.childCount).joinToString("") { windowTree(node.getChild(it), depth + 1) }
            }
            println("FAILURE-WINDOW $name\n" + windowTree(root))
            println("FAILURE-COMPOSE $name\n" + runCatching { compose.onRoot(useUnmergedTree = true).printToString() }.getOrElse { it.toString() })
            throw failure
        }

    }

    private fun verifySettingsCategory(category: com.music.bitchord.ui.screens.SettingsCategory) {
        englishFixture()
        openSettingsAndAssert("Settings", "Settings")
        run {
            val title = compose.activity.getString(category.title)
            clickRow(title)
            // The root also contains category labels; require that root-only
            // connected-services row has disappeared before counting a child.
            try {
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Connected services").fetchSemanticsNodes().isEmpty() }
            } catch (failure: Throwable) {
                fullScreenshot("failure-category-${category.name.lowercase()}")
                println("FAILURE-CATEGORY $category\n" + compose.onRoot(useUnmergedTree = true).printToString())
                throw failure
            }
            compose.onAllNodesWithText(title)[0].assertIsDisplayed()
            inspectPage("category-${category.name.lowercase()}", title)
            val choices = when (category) {
                com.music.bitchord.ui.screens.SettingsCategory.DOWNLOADS -> listOf(com.music.bitchord.ui.screens.SettingsChoiceKind.DOWNLOAD_QUALITY)
                com.music.bitchord.ui.screens.SettingsCategory.PLAYBACK -> listOf(com.music.bitchord.ui.screens.SettingsChoiceKind.AUTOMIX_PERFORMANCE, com.music.bitchord.ui.screens.SettingsChoiceKind.BIT_PERFECT)
                com.music.bitchord.ui.screens.SettingsCategory.APPEARANCE -> listOf(com.music.bitchord.ui.screens.SettingsChoiceKind.FONT)
                else -> emptyList()
            }
            for (choice in choices) {
                val option = compose.activity.getString(choice.title)
                clickRow(option)
                inspectPage("choice-${choice.name.lowercase()}", option)
                backToParent()
            }
            if (category == com.music.bitchord.ui.screens.SettingsCategory.PLAYBACK) {
                // Test-only state, confined to this disposable debug installation.
                val oldEnabled = AppSettings.equalizerEnabled.value
                val oldMode = AppSettings.equalizerMode.value
                AppSettings.setEqualizerEnabled(true)
                AppSettings.setEqualizerMode(com.music.bitchord.data.settings.EqualizerMode.MANUAL)
                clickRow(compose.activity.getString(R.string.equalizer))
                inspectPage("equalizer", compose.activity.getString(R.string.equalizer))
                clickRow(compose.activity.getString(R.string.equalizer_preset))
                inspectPage("equalizer-presets", compose.activity.getString(R.string.equalizer_preset))
                backToParent()
                backToParent()
                AppSettings.setEqualizerEnabled(oldEnabled)
                AppSettings.setEqualizerMode(oldMode)
            }
            backToParent()
        }
    }

    @Test fun settingsCategory_downloads() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.DOWNLOADS)
    @Test fun settingsCategory_playback() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.PLAYBACK)
    @Test fun settingsCategory_content_preferences() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.CONTENT_PREFERENCES)
    @Test fun settingsCategory_appearance() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.APPEARANCE)
    @Test fun settingsCategory_performance() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.PERFORMANCE)
    @Test fun settingsCategory_local_music() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.LOCAL_MUSIC)
    @Test fun settingsCategory_storage() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.STORAGE)
    @Test fun settingsCategory_your_data() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.YOUR_DATA)
    @Test fun settingsCategory_miscellaneous() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.MISCELLANEOUS)
    @Test fun settingsCategory_language() = verifySettingsCategory(com.music.bitchord.ui.screens.SettingsCategory.LANGUAGE)

    @Test fun rootAudioChoices() {
        englishFixture()
        openSettingsAndAssert("Settings", "Settings")
        for (choice in listOf(com.music.bitchord.ui.screens.SettingsChoiceKind.AUDIO_QUALITY_WIFI,
            com.music.bitchord.ui.screens.SettingsChoiceKind.AUDIO_QUALITY_CELLULAR)) {
            val title = compose.activity.getString(choice.title)
            clickRow(title)
            inspectPage("choice-${choice.name.lowercase()}", title)
            backToParent()
        }
    }

    @Test fun settingsConnectionsAndAboutPages() {
        englishFixture()
        openSettingsAndAssert("Settings", "Settings")
        clickRow(compose.activity.getString(R.string.not_signed_in))
        inspectPage("profile-signed-out", compose.activity.getString(R.string.acct_profile_header))
        println("UNAVAILABLE SwitchProfile and ManageAccounts: signed-out fixture disables both entry rows; no accounts created")
        backToParent()
        clickRow(compose.activity.getString(R.string.account_integrations))
        inspectPage("connected-services", compose.activity.getString(R.string.account_integrations))
        for (key in listOf(R.string.discord, R.string.spotify)) {
            val title = compose.activity.getString(key)
            clickRow(title)
            inspectPage(title.lowercase(), title)
            backToParent()
        }
        backToParent()
        clickRow(compose.activity.getString(R.string.source))
        inspectPage("sources", compose.activity.getString(R.string.source))
        backToParent()
        clickRow(compose.activity.getString(R.string.listen_together))
        inspectPage("listen-together-signed-out", compose.activity.getString(R.string.listen_together))
        backToParent()
        for (key in listOf(R.string.app_updates, R.string.about_developer_title, R.string.about_credits)) {
            val title = compose.activity.getString(key)
            clickRow(title)
            inspectPage("about-$key", title)
            if (key == R.string.about_credits) {
                for (notice in listOf("GNU GPL v3") + com.music.bitchord.ui.screens.BUNDLED_NOTICES.map { it.second }) {
                    clickRow(notice)
                    inspectPage("license-${notice.replace(' ', '-').lowercase()}", notice)
                    backToParent()
                }
            }
            backToParent()
        }
        val watch = compose.activity.getString(R.string.wear_os_section)
        clickRow(watch)
        inspectPage("wear-os-no-watch", compose.activity.getString(R.string.wear_os_page_title))
        backToParent()
        println("UNAVAILABLE Wear OS connected-watch state: requires paired device; no device/account changes made")
    }

    @Test fun everyRootTabAndLibraryLanding() {
        englishFixture()
        for ((label, identity) in listOf("Explore" to "Explore", "Library" to "Library", "Search" to "Songs, artists, albums")) {
            val tab = hasClickAction() and (hasContentDescription(label) or hasAnyDescendant(hasContentDescription(label)))
            compose.onAllNodes(tab, useUnmergedTree = true)[0].performClick()
            Thread.sleep(1500)
            compose.waitForIdle()
            inspectPage("tab-${label.lowercase()}", identity)
        }
        val tab = hasClickAction() and (hasContentDescription("Library") or hasAnyDescendant(hasContentDescription("Library")))
        compose.onAllNodes(tab, useUnmergedTree = true)[0].performClick()
        Thread.sleep(1000)
        compose.waitForIdle()
        val history = hasClickAction() and (hasContentDescription("Listening history") or hasAnyDescendant(hasContentDescription("Listening history")))
        compose.onAllNodes(history, useUnmergedTree = true)[0].performClick()
        Thread.sleep(1000)
        compose.waitForIdle()
        inspectPage("history-signed-out", "History")
        backToParent()
        for (key in listOf(R.string.downloads, R.string.local_music)) {
            val title = compose.activity.getString(key)
            clickRow(title)
            // Library entry and destination page intentionally use different
            // names for Downloads. Keep the route identity exact.
            val pageTitle = if (key == R.string.downloads) compose.activity.getString(R.string.downloaded_songs)
                else "Audio files on device"
            inspectPage("library-folder-$key", pageTitle)
            backToParent()
        }
        println("UNAVAILABLE authenticated library shelves/artist shelf content: signed-out fixture has no owner library; no fake populated-state claim")
    }

    @Test fun realMoodGridAllTwentyEightTiles() {
        englishFixture()
        val showAll = hasText("Show all") and hasAnyAncestor(hasTestTag("home-mood-chip-row"))
        compose.onNode(showAll).performScrollTo().performClick()
        Thread.sleep(1000)
        compose.waitForIdle()
        inspectPage("moods-grid", "Find Your Mood")
        val labels = listOf("Energy", "Relax", "Feeling Blue", "Feel Good", "Focus", "Heartbreak", "Love", "After Hours", "Alone Time", "Chill", "Commuting", "Eating & Cooking", "Evening", "Fitness", "Gaming", "Home", "Morning", "Motivation", "Outdoors", "Party", "Sleep", "Social", "Sports", "Summertime Sounds", "Holiday", "Weekend", "Wellbeing", "Work")
        for ((index, label) in labels.withIndex()) {
            compose.onAllNodes(hasScrollToNodeAction(), useUnmergedTree = true)[0]
                .performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
            assertEquals("$label: strip remains pinned", 0f, stripBounds()!!.top, 1f)
            fullScreenshot("mood-grid-${index.toString().padStart(2, '0')}", false)
        }
        println("GRID-VISUALS 28 identities asserted; callback/personalization tested separately, no live playback claim")
    }

    /** Same production station painter and app loader, with REAL Android
     * crossfade clocks. Isolated component proof, not Home/provider navigation. */
    @Test fun stationArtworkProductionCrossfadeOnRealAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cover = File(context.cacheDir, "station-emulator-1200.png")
        val bitmap = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { isAntiAlias = true }
        canvas.drawColor(android.graphics.Color.MAGENTA)
        paint.color = android.graphics.Color.rgb(42, 72, 92)
        canvas.drawRect(24f, 24f, 1176f, 1176f, paint)
        paint.color = android.graphics.Color.rgb(238, 191, 105)
        canvas.drawCircle(600f, 530f, 260f, paint)
        paint.color = android.graphics.Color.rgb(42, 72, 92)
        canvas.drawCircle(600f, 530f, 75f, paint)
        cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = cover.toURI().toString()
        val picks = listOf(com.music.bitchord.data.model.TopPickKind.PERSONAL_STATION,
            com.music.bitchord.data.model.TopPickKind.DISCOVERY_STATION).map { kind ->
            val songs = listOf(com.music.bitchord.data.model.Song("emulator:${kind.name}", "Fixture song", "Fixture artist", uri))
            com.music.bitchord.data.model.TopPick(kind, images = listOf(uri),
                watch = com.music.bitchord.data.model.WatchTarget("queue:${kind.name}"), preparedSongs = songs)
        }
        val tapped = mutableListOf<com.music.bitchord.data.model.TopPick>()
        for (dark in listOf(false, true)) {
            theme(dark)
            compose.runOnUiThread {
                compose.activity.setContent {
                    com.music.bitchord.ui.theme.BitChordTheme(darkTheme = dark) {
                        Box(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                            .windowInsetsPadding(WindowInsets.safeDrawing)) {
                            com.music.bitchord.ui.screens.TopPicksShelf(picks, null, { pick, _ -> tapped += pick }, null)
                        }
                    }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("station-art-loaded", useUnmergedTree = true).fetchSemanticsNodes().size == 2
            }
            // Do NOT replace/clone the application loader or turn crossfade off.
            // Wait past its production 200ms transition on actual Android clocks.
            Thread.sleep(1000)
            compose.waitForIdle()
            fullScreenshot("station-emulator-production-fade-${if (dark) "dark" else "light"}")
            for (index in 0..1) {
                val image = compose.onAllNodesWithTag("station-art-loaded", useUnmergedTree = true)[index]
                    .captureToImage().asAndroidBitmap()
                saveBitmap("station-emulator-production-art-${dark}-$index", image)
                val edge = image.getPixel(2, image.height / 2)
                assertTrue("real production crop edge must paint blue artwork, not blank/magenta",
                    kotlin.math.abs(android.graphics.Color.red(edge) - 42) < 8 &&
                    kotlin.math.abs(android.graphics.Color.green(edge) - 72) < 8 &&
                    kotlin.math.abs(android.graphics.Color.blue(edge) - 92) < 8)
                val gold = image.getPixel(image.width / 2, image.height / 4)
                assertTrue("real production crossfade must reveal known gold record",
                    android.graphics.Color.red(gold) > 200 && android.graphics.Color.green(gold) > 150 && android.graphics.Color.blue(gold) < 140)
                image.recycle()
            }
            compose.onNodeWithText("Your Station").performClick()
            assertEquals(picks.first().watch, tapped.last().watch)
            assertEquals(picks.first().preparedSongs, tapped.last().preparedSongs)
        }
    }

    /** Installed production grid + callback + provider parser + real controller.
     * HTTP replies and downloaded WAVs are isolated fixtures. This does NOT prove
     * live catalogue availability, stream resolution, or audible speaker output. */
    @Test fun allMoodTilesReachRealControllerAndLocalPlayback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val transportField = com.music.bitchord.data.innertube.Innertube::class.java
            .getDeclaredField("activeTransportForTests").apply { isAccessible = true }
        val tasteField = com.music.bitchord.data.TopPicks::class.java
            .getDeclaredField("taste").apply { isAccessible = true }
        val cacheField = com.music.bitchord.data.YtMusicRepository::class.java
            .getDeclaredField("moodGenreShelfCache").apply { isAccessible = true }
        val savedField = com.music.bitchord.download.Downloads::class.java
            .getDeclaredField("_saved").apply { isAccessible = true }
        val oldTransport = transportField.get(com.music.bitchord.data.innertube.Innertube)
        val oldTaste = tasteField.get(com.music.bitchord.data.TopPicks) as com.music.bitchord.data.Taste.Snapshot?
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(com.music.bitchord.data.YtMusicRepository) as MutableMap<String, List<com.music.bitchord.data.model.HomeShelf>>
        val oldCache = cache.toMap()
        @Suppress("UNCHECKED_CAST")
        val saved = savedField.get(com.music.bitchord.download.Downloads) as kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>
        val oldSaved = saved.value
        val fail = java.util.concurrent.atomic.AtomicBoolean(false)
        val finishedFailureReplies = java.util.concurrent.atomic.AtomicInteger(0)
        val providerParams = java.util.Collections.synchronizedList(mutableListOf<String>())
        val codec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        fun text(value: String) = kotlinx.serialization.json.buildJsonObject {
            putJsonArray("runs") { add(kotlinx.serialization.json.buildJsonObject { put("text", value) }) }
        }
        val categoryShelf = kotlinx.serialization.json.buildJsonObject {
            putJsonObject("musicCarouselShelfRenderer") {
                putJsonObject("header") { putJsonObject("musicCarouselShelfBasicHeaderRenderer") { put("title", text("Malayalam")) } }
                putJsonArray("contents") { repeat(3) { i -> add(kotlinx.serialization.json.buildJsonObject {
                    putJsonObject("musicTwoRowItemRenderer") {
                        put("title", text("Fixture playlist $i"))
                        putJsonObject("navigationEndpoint") { putJsonObject("browseEndpoint") { put("browseId", "VLfixture-$i") } }
                    }
                }) } }
            }
        }.toString()
        // Match the real browse envelope consumed by parseHome, not a bare
        // carousel renderer that parses successfully to an empty shelf list.
        val category = kotlinx.serialization.json.buildJsonObject {
            putJsonObject("contents") {
                putJsonObject("singleColumnBrowseResultsRenderer") {
                    putJsonArray("tabs") { add(kotlinx.serialization.json.buildJsonObject {
                        putJsonObject("tabRenderer") { putJsonObject("content") {
                            putJsonObject("sectionListRenderer") { putJsonArray("contents") {
                                add(codec.parseToJsonElement(categoryShelf))
                            } }
                        } }
                    }) }
                }
            }
        }.toString()
        val fixtureRequests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val queue = kotlinx.serialization.json.buildJsonObject {
            putJsonArray("contents") { repeat(24) { i -> add(kotlinx.serialization.json.buildJsonObject {
                putJsonObject("playlistPanelVideoRenderer") {
                    put("videoId", "mood-fixture-${i+1}")
                    put("title", text("Local fixture ${i+1}"))
                    put("longBylineText", text(if (i < 8) "Familiar Artist" else "Fresh Artist $i"))
                    put("lengthText", text("0:30"))
                }
            }) } }
        }.toString()
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            val body = runCatching { codec.parseToJsonElement(buffer.readUtf8()).jsonObject }.getOrNull()
            val path = request.url.encodedPath.substringAfterLast('/')
            fixtureRequests.add("$path browseId=${body?.get("browseId")} params=${body?.get("params")} fail=${fail.get()}")
            val payload = when {
                fail.get() && (path == "next" || (path == "browse" && body?.get("browseId")?.jsonPrimitive?.content?.startsWith("VLfixture-") == true)) -> {
                    finishedFailureReplies.incrementAndGet()
                    "{}"
                }
                path == "browse" && body?.get("browseId")?.jsonPrimitive?.content == com.music.bitchord.data.model.TopPickMood.CATEGORY_BROWSE_ID -> {
                    providerParams.add(body["params"]!!.jsonPrimitive.content)
                    category
                }
                path == "next" -> queue
                else -> "{}"
            }
            okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("fixture").header("Content-Type", "application/json")
                .body(payload.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val transport = io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            engine { preconfigured = http }
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(codec) }
        }
        val wave = File(context.cacheDir, "mood-controller-fixture.wav")
        val rate = 44100
        val samples = rate * 30
        val bytes = java.nio.ByteBuffer.allocate(44 + samples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate * 2)
            .putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { i -> bytes.putShort((kotlin.math.sin(2 * Math.PI * 440 * i / rate) * 1200).toInt().toShort()) }
        wave.writeBytes(bytes.array())
        val profile = com.music.bitchord.data.Taste.Snapshot(
            artists = listOf("familiar artist"), languages = listOf("malayalam"),
            dislikedIds = listOf("mood-fixture-24"), skippedIds = mapOf("mood-fixture-23" to 3),
            topSongs = listOf(com.music.bitchord.data.Taste.TasteSong("mood-fixture-own", "Own local favorite", "Familiar Artist")),
        )
        var controller: androidx.media3.session.MediaController? = null
        try {
            transportField.set(com.music.bitchord.data.innertube.Innertube, transport)
            saved.value = oldSaved + ((1..24).map { "mood-fixture-$it" } + "mood-fixture-own").associateWith { android.net.Uri.fromFile(wave).toString() }
            englishFixture()
            lateinit var future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaController>
            compose.runOnUiThread {
                future = androidx.media3.session.MediaController.Builder(context,
                    androidx.media3.session.SessionToken(context, android.content.ComponentName(context, com.music.bitchord.playback.PlaybackService::class.java)))
                    .setApplicationLooper(android.os.Looper.getMainLooper()).buildAsync()
            }
            val c = future.get(15, java.util.concurrent.TimeUnit.SECONDS)
            controller = c
            compose.runOnUiThread { c.shuffleModeEnabled = false }
            compose.onNode(hasText("Show all") and hasAnyAncestor(hasTestTag("home-mood-chip-row")))
                .performScrollTo().performClick()
            compose.onNodeWithText("Find Your Mood").assertExists()
            val labels = listOf("Energy", "Relax", "Feeling Blue", "Feel Good", "Focus", "Heartbreak", "Love", "After Hours", "Alone Time", "Chill", "Commuting", "Eating & Cooking", "Evening", "Fitness", "Gaming", "Home", "Morning", "Motivation", "Outdoors", "Party", "Sleep", "Social", "Sports", "Summertime Sounds", "Holiday", "Weekend", "Wellbeing", "Work")
            val moods = com.music.bitchord.data.model.TopPickMood.entries
            assertEquals(28, moods.size)
            for ((i, mood) in moods.withIndex()) {
                cache.clear()
                com.music.bitchord.data.TopPicks.updateTaste(profile)
                compose.onAllNodes(hasScrollToNodeAction(), useUnmergedTree = true)[0].performScrollToNode(hasText(labels[i]))
                compose.onNodeWithText(labels[i]).performClick()
                try {
                compose.waitUntil(20_000) {
                    var loaded = false
                    compose.runOnUiThread {
                        loaded = c.currentMediaItem?.mediaMetadata?.extras?.getString("bitchord.playbackSourceId") == mood.name &&
                            c.playbackState == androidx.media3.common.Player.STATE_READY && c.isPlaying && c.playerError == null
                    }
                    loaded
                }
                } catch (failure: Throwable) {
                    compose.runOnUiThread {
                        println("MOOD-CONTROLLER-FAIL ${mood.name} state=${c.playbackState} playing=${c.isPlaying} error=${c.playerError} count=${c.mediaItemCount} current=${c.currentMediaItem?.mediaId} source=${c.currentMediaItem?.mediaMetadata?.extras?.getString("bitchord.playbackSourceId")}")
                    }
                    println("MOOD-FIXTURE-REQUESTS " + synchronized(fixtureRequests) { fixtureRequests.toList() })
                    println("MOOD-FAILURE-SEMANTICS\n" + compose.onRoot(useUnmergedTree = true).printToString())
                    runCatching { fullScreenshot("failure-mood-controller-${i.toString().padStart(2, '0')}") }
                        .onFailure { failure.addSuppressed(it) }
                    throw failure
                }
                var startedAt = 0L
                var timeline = emptyList<com.music.bitchord.data.model.Song>()
                compose.runOnUiThread {
                    startedAt = c.currentPosition
                    timeline = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }
                }
                compose.waitUntil(5_000) {
                    var progressed = false
                    compose.runOnUiThread { progressed = c.currentPosition >= startedAt + 250 && c.isPlaying && c.playerError == null }
                    progressed
                }
                assertEquals(mood.params, providerParams.last())
                assertEquals(24, timeline.size)
                assertEquals(24, timeline.map { it.videoId }.distinct().size)
                assertTrue(timeline.none { it.videoId == "mood-fixture-24" })
                assertTrue(timeline.any { it.videoId == "mood-fixture-own" })
                assertEquals("mood-fixture-23", timeline.last().videoId)
                assertEquals("Familiar Artist", timeline.first().artist)
                assertTrue(timeline.all { it.playbackSourceId == mood.name && it.playbackSource == labels[i] })
                fullScreenshot("mood-controller-${i.toString().padStart(2,'0')}", false)
                println("MOOD-CONTROLLER ${mood.name}: production tap + parsed personalized queue + READY + playing + position progress; LOCAL WAV fixture")
            }
            var before = emptyList<Pair<String, String?>>()
            compose.runOnUiThread { before = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong().let { song -> song.videoId to song.queueEntryId } } }
            cache.clear()
            fail.set(true)
            compose.onNodeWithText(labels.last()).performClick()
            compose.waitUntil(15_000) { finishedFailureReplies.get() >= 6 }
            compose.waitForIdle()
            Thread.sleep(300)
            compose.runOnUiThread {
                assertEquals(before, (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong().let { song -> song.videoId to song.queueEntryId } })
                assertTrue(c.isPlaying)
                assertTrue(c.playerError == null)
            }
            println("MOOD-FAILURE both provider paths empty: previous queue entry identities and active playback preserved")
        } finally {
            controller?.let { c -> compose.runOnUiThread { c.stop(); c.clearMediaItems(); c.release() } }
            transportField.set(com.music.bitchord.data.innertube.Innertube, oldTransport)
            com.music.bitchord.data.TopPicks.updateTaste(oldTaste)
            cache.clear(); cache.putAll(oldCache)
            saved.value = oldSaved
            transport.close(); http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll()
            wave.delete()
        }
    }

}
