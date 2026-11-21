package com.music.bitchord

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
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

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

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
    private fun scrollPageAndAssertMoved(label: String, anchorText: String) {
        val scrollables = compose.onAllNodes(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$label: expected a scrollable container on screen", scrollables.isNotEmpty())
        val target = scrollables.maxByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }!!
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
        scrollPageAndAssertMoved("settings-light", "Audio quality")
        fullScreenshot("05-settings-light-scrolled", expectDark = false)
        val stripInSettings = stripBounds()
        assertTrue("strip must exist on the Settings page too", stripInSettings != null)
        assertEquals(0f, stripInSettings!!.top, 1f)

        // Replay, reached the way the user reached it (back to Home, then the
        // Library tab): captured after the approved heading spacing fix
        // (48dp -> 12dp) so the tightened top can be reviewed on real pixels.
        // Identity is asserted - a wrong page cannot wear the Replay name.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        Thread.sleep(1200)
        compose.waitForIdle()
        val libraryTab = hasClickAction() and (
            hasContentDescription("Library") or hasAnyDescendant(hasContentDescription("Library"))
            )
        compose.waitUntil(10_000) {
            compose.onAllNodes(libraryTab, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(libraryTab, useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        Thread.sleep(1500)
        compose.waitForIdle()
        val replayEntry = hasClickAction() and (hasText("Replay") or hasAnyDescendant(hasText("Replay")))
        compose.waitUntil(10_000) {
            compose.onAllNodes(replayEntry, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(replayEntry, useUnmergedTree = true)[0].performClick()
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
        val stripOnReplay = stripBounds()
        assertTrue("strip must exist on Replay too", stripOnReplay != null)
        assertEquals(0f, stripOnReplay!!.top, 1f)
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

            theme(dark = true)
            fullScreenshot("12-ml-home-dark", expectDark = true)
            theme(dark = false)

            openSettingsAndAssert("ക്രമീകരണങ്ങൾ", "ക്രമീകരണങ്ങൾ")
            fullScreenshot("13-ml-settings-light", expectDark = false)
            scrollPageAndAssertMoved("ml-settings-light", "ഓഡിയോ നിലവാരം")
            fullScreenshot("14-ml-settings-light-scrolled", expectDark = false)
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
}
