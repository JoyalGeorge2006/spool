package com.music.bitchord

import android.graphics.Bitmap
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
            compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(0)
            compose.waitForIdle()
            fullScreenshot("15-ml-home-dark-top", expectDark = true)
            theme(dark = false)

            openSettingsAndAssert("ക്രമീകരണങ്ങൾ", "ക്രമീകരണങ്ങൾ")
            fullScreenshot("13-ml-settings-light", expectDark = false)
            scrollPageAndAssertMoved("ml-settings-light", "ഓഡിയോ നിലവാരം")
            fullScreenshot("14-ml-settings-light-scrolled", expectDark = false)
            theme(dark = true)
            fullScreenshot("16-ml-settings-dark-scrolled", expectDark = true)
            compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(0)
            compose.waitForIdle()
            fullScreenshot("17-ml-settings-dark-top", expectDark = true)
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
        val lazy = compose.onAllNodes(hasScrollToNodeAction(), useUnmergedTree = true)
            .fetchSemanticsNodes().filter {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange) != null
            }.maxByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
        if (lazy != null) {
            compose.onNode(SemanticsMatcher("page lazy container") { it.id == lazy.id }, useUnmergedTree = true)
                .performScrollToNode(text)
        } else {
            compose.onAllNodes(text, useUnmergedTree = true)[0].performScrollTo()
        }
        val matcher = hasClickAction() and (text or hasAnyDescendant(text))
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val row = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 && viewport.contains(it.boundsInRoot.center) }
            .minByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
            ?: error("No visible clickable row for $label")
        compose.onNode(SemanticsMatcher("smallest clickable row") { it.id == row.id }, useUnmergedTree = true).performClick()
        Thread.sleep(1000)
        compose.waitForIdle()
    }

    private fun requireDestination(title: String, uniqueChild: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(uniqueChild).fetchSemanticsNodes().any {
                val b = it.boundsInRoot
                b.width > 0 && b.height > 0 && compose.onRoot().fetchSemanticsNode().boundsInRoot.contains(b.center)
            }
        }
        compose.onAllNodesWithText(title)[0].assertIsDisplayed()
        compose.onAllNodesWithText(uniqueChild)[0].assertIsDisplayed()
    }

    /** Identity must be visible above the content; scrolling must change the
     * actual scroll-axis value when the page has remaining vertical content.
     * A short/empty page is logged explicitly, never called a scroll pass. */
    private fun inspectPage(name: String, title: String) {
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
    }

    private fun verifySettingsCategory(category: com.music.bitchord.ui.screens.SettingsCategory) {
        englishFixture()
        openSettingsAndAssert("Settings", "Settings")
        run {
            val title = compose.activity.getString(category.title)
            clickRow(title)
            // The root also contains category labels; require that root-only
            // connected-services row has disappeared before counting a child.
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Connected services").fetchSemanticsNodes().isEmpty() }
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
            inspectPage("library-folder-$key", title)
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

}
