@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.music.bitchord

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.listentogether.PartyPreview
import com.music.bitchord.data.listentogether.PartyPreviewMember
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.InlineFrostSheet
import com.music.bitchord.ui.components.PageFrostSourceKey
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.screens.JoinConfirmSheet
import com.music.bitchord.ui.screens.ListenTogetherScreen
import com.music.bitchord.ui.screens.PartyServerEditor
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runtime coverage for the repaired party frost wiring (party-sheets-local-source
 * + party-server-editor-keyed patches): the four party sheets over their page,
 * open/dismiss/re-entry, focus restore, Blur effects OFF, and two representative
 * repaired overlays (the root PartyServerEditor and one keyed AddonEditorAlert).
 *
 * What this does NOT prove: pixel-level blur. Haze samples at draw time and the
 * semantics tree cannot see it, so every assertion here is structural/a11y —
 * the same tree TalkBack walks. Solid-vs-frost rendering stays on the manual
 * emulator matrix.
 *
 * Documented gaps, both need a server and neither is faked here:
 *  - The full-screen path to [PartySheet.Confirm] runs ListenTogether.previewParty
 *    over HTTPS; usesCleartextTraffic=false blocks a loopback fixture and no
 *    network is faked, so the Confirm sheet is covered by composing its real
 *    content inside the same container composable the screen mounts
 *    ([confirmSheetContent_insideRealFrostContainer]).
 *  - The Create -> Invite handover needs a signed-in account for
 *    ListenTogether.createParty; no synthetic auth session is seeded. The Invite
 *    sheet itself is covered via seeded state ([inviteSheet_seededParty]).
 *
 * Synthetic fixtures: the seeded party state below (code Q7ZK2W, member
 * "Synthetic Host") and the dead-loopback idle server (127.0.0.1:9, refuses
 * fast so health probes never reach the real default server). Both are reset
 * after every test. Production code, callbacks and networking are untouched.
 */
@OptIn(ExperimentalHazeApi::class)
@RunWith(AndroidJUnit4::class)
class PartySheetsRuntimeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any): String = context.getString(id, *args)

    private var originalBlur = false

    @Before
    fun setUp() {
        originalBlur = AppSettings.blurEffects.value
        context.getSharedPreferences(LT_PREFS, 0).edit().clear().commit()
        seedField<ListenTogether.State>("_state", ListenTogether.State())
        // Dead loopback: refuses fast, keeps idle health probes off the network.
        seedField<String>("_effectiveIdleServer", "http://127.0.0.1:9")
    }

    @After
    fun tearDown() {
        seedField<ListenTogether.State>("_state", ListenTogether.State())
        seedField<String>("_effectiveIdleServer", ListenTogether.defaultServer)
        AppSettings.setBlurEffects(originalBlur)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> seedField(name: String, value: T) {
        val field = ListenTogether::class.java.getDeclaredField(name)
        field.isAccessible = true
        (field.get(ListenTogether) as MutableStateFlow<T>).value = value
    }

    private fun mountScreen(
        signedIn: Boolean = true,
        inviteCode: String? = null,
        onEditServer: () -> Unit = {},
    ) {
        rule.setContent {
            BitChordTheme {
                ListenTogetherScreen(
                    signedIn = signedIn,
                    inviteCode = inviteCode,
                    onSignIn = {},
                    contentPadding = PaddingValues(0.dp),
                    onEditServer = onEditServer,
                )
            }
        }
    }

    /**
     * Real screenshot artifact, saved where StatusStripEmulatorTest saves its
     * shots (getExternalFilesDir, adb-pullable). The tag says what the pixels
     * can be: blurOFF with the switch off, blurON only where glass can render
     * (API 31+), blurON-noglass where the switch is on but Haze draws the
     * solid fallback - that last one is solid pixels honestly labeled, not
     * blur proof. The settle pause lets Haze finish sampling its backdrop
     * before the grab.
     */
    private fun capture(name: String) {
        rule.waitForIdle()
        Thread.sleep(600)
        val tag = when {
            !AppSettings.blurEffects.value -> "blurOFF"
            isGlassSupported() -> "blurON"
            else -> "blurON-noglass"
        }
        val dir = File(context.getExternalFilesDir(null), "party-shots").apply { mkdirs() }
        val out = File(dir, "$name-$tag.png")
        FileOutputStream(out).use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        android.util.Log.i("PartyShots", "captured ${out.absolutePath}")
        System.out.println("PartyShots: ${out.absolutePath}")
    }

    /** Page content is invisibleToUser while a sheet is up; sheet text is the only copy left. */
    private fun assertSheetCoversPage(sheetMarker: String) {
        rule.onNodeWithText(sheetMarker).assertExists()
        rule.onNodeWithText(str(R.string.listen_together_landing_title)).assertDoesNotExist()
    }

    @Test
    fun createSheet_opensCoversPage_backRestoresAndRefocuses() {
        AppSettings.setBlurEffects(true)
        mountScreen()
        val createAction = str(R.string.listen_together_create_action)
        rule.onNodeWithText(createAction).performClick()

        assertSheetCoversPage(str(R.string.listen_together_create_sheet_subtitle))
        capture("01-create-sheet")

        Espresso.pressBack()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText(str(R.string.listen_together_landing_title)).fetchSemanticsNodes().isNotEmpty()
        }
        // The button the sheet opened from takes focus back (restoreFocusTo).
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText(createAction).fetchSemanticsNodes()
                .any { it.config.getOrNull(SemanticsProperties.Focused) == true }
        }
    }

    @Test
    fun joinSheet_openDismissReopen() {
        AppSettings.setBlurEffects(true)
        mountScreen()
        val joinAction = str(R.string.listen_together_join_action)
        val sheetMarker = str(R.string.listen_together_code_hint)

        rule.onNodeWithText(joinAction).performClick()
        assertSheetCoversPage(sheetMarker)
        capture("02-joincode-sheet")

        Espresso.pressBack()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText(sheetMarker).fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithText(str(R.string.listen_together_landing_title)).fetchSemanticsNodes().isNotEmpty()
        }

        // Re-entry mounts a fresh sheet the same way; this one with Blur off.
        AppSettings.setBlurEffects(false)
        rule.onNodeWithText(joinAction).performClick()
        assertSheetCoversPage(sheetMarker)
        capture("02-joincode-sheet")
    }

    @Test
    fun inviteSheet_seededParty() {
        // SYNTHETIC membership: no server was ever contacted for this party.
        val host = PartyMember(
            memberId = "synthetic-member-1",
            userId = "synthetic-user-1",
            displayName = "Synthetic Host",
            isHost = true,
            connected = true,
        )
        seedField("_state", ListenTogether.State(code = "Q7ZK2W", you = host, members = listOf(host)))
        AppSettings.setBlurEffects(true)
        mountScreen()

        rule.onNodeWithText(str(R.string.share)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.listen_together_invite_title)).assertExists()
        rule.onNodeWithText(str(R.string.listen_together_share_link)).assertExists()
        rule.onNodeWithText(str(R.string.copy_code)).assertExists()
        capture("03-invite-sheet")

        Espresso.pressBack()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText(str(R.string.listen_together_invite_title)).fetchSemanticsNodes().isEmpty()
        }

        // Same sheet, Blur effects off.
        AppSettings.setBlurEffects(false)
        rule.onNodeWithText(str(R.string.share)).performScrollTo().performClick()
        rule.onNodeWithText(str(R.string.listen_together_invite_title)).assertExists()
        capture("03-invite-sheet")

        Espresso.pressBack()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText(str(R.string.listen_together_invite_title)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun confirmSheetContent_insideRealFrostContainer() {
        // Wiring replica, not the screen's callsite: previewParty is HTTPS and
        // cleartext loopback is blocked, so the Confirm sheet's real content is
        // mounted inside the same InlineFrostSheet + page-scoped HazeState pair
        // the screen uses. The preview itself is SYNTHETIC.
        var dismissed = false
        rule.setContent {
            BitChordTheme {
                val sheetHaze = remember { HazeState() }
                Box(Modifier.fillMaxSize().hazeSource(sheetHaze, zIndex = -1f)) {}
                InlineFrostSheet(
                    onDismiss = { dismissed = true },
                    hazeState = sheetHaze,
                    paneTitle = str(R.string.listen_together),
                ) {
                    JoinConfirmSheet(
                        preview = PartyPreview(
                            code = "Q7ZK2W",
                            hostName = "Synthetic Host",
                            memberCount = 3,
                            maxMembers = 5,
                            members = listOf(PartyPreviewMember(displayName = "Synthetic Host", isHost = true)),
                        ),
                        avatarUrl = null,
                        nickname = "Tester",
                        onNicknameChange = {},
                        busy = false,
                        error = null,
                        onJoin = {},
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }

        rule.onNodeWithText(str(R.string.listen_together_join_question, "Synthetic Host")).assertExists()
        AppSettings.setBlurEffects(true)
        capture("04-confirm-sheet-REPLICA")
        AppSettings.setBlurEffects(false)
        rule.waitForIdle()
        capture("04-confirm-sheet-REPLICA")
        rule.onNodeWithText(str(R.string.listen_together_not_now)).performClick()
        rule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun blurOff_createSheetStillCoversAndRestores() {
        AppSettings.setBlurEffects(false)
        mountScreen()
        rule.onNodeWithText(str(R.string.listen_together_create_action)).performClick()
        assertSheetCoversPage(str(R.string.listen_together_create_sheet_subtitle))
        capture("01-create-sheet")
        Espresso.pressBack()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText(str(R.string.listen_together_landing_title)).fetchSemanticsNodes().isNotEmpty()
        }
        // Pixels are captured, not asserted on: blur quality is a human read
        // of the artifacts, the assertions stay on the a11y structure.
    }

    @Test
    fun partyServerEditor_keyedOverlay_opensAndDismisses() {
        // Same mounting MainActivity uses: keyed page source at the root,
        // editor as its sibling with pageSourceKey passed through.
        var editing by mutableStateOf(false)
        rule.setContent {
            BitChordTheme {
                val appHaze = remember { HazeState() }
                Box(Modifier.fillMaxSize().hazeSource(appHaze, key = PageFrostSourceKey)) {
                    ListenTogetherScreen(
                        signedIn = true,
                        onSignIn = {},
                        contentPadding = PaddingValues(0.dp),
                        onEditServer = { editing = true },
                    )
                }
                if (editing) {
                    PartyServerEditor(
                        hazeState = appHaze,
                        pageSourceKey = PageFrostSourceKey,
                        onDismiss = { editing = false },
                    )
                }
            }
        }

        AppSettings.setBlurEffects(true)
        val serverRow = str(R.string.listen_together_custom_server)
        rule.onNodeWithText(serverRow).performScrollTo().performClick()
        // Row plus the editor's title: two nodes with the same label.
        rule.onAllNodesWithText(serverRow).assertCountEquals(2)
        capture("05-party-server-editor")

        rule.onNodeWithText(str(R.string.cancel)).performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText(serverRow).fetchSemanticsNodes().size == 1
        }

        // Blur off: same open, same shot, solid material.
        AppSettings.setBlurEffects(false)
        rule.onNodeWithText(serverRow).performScrollTo().performClick()
        rule.onAllNodesWithText(serverRow).assertCountEquals(2)
        capture("05-party-server-editor")
        rule.onNodeWithText(str(R.string.cancel)).performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText(serverRow).fetchSemanticsNodes().size == 1
        }
    }

    @Test
    fun representative_keyedEditorOverlay_rendersActions() {
        // Representative of the keyed editor overlays repaired in the editors
        // split: AddonEditorAlert bound to a keyed page source by pageSourceKey.
        rule.setContent {
            BitChordTheme {
                val appHaze = remember { HazeState() }
                Box(Modifier.fillMaxSize().hazeSource(appHaze, key = PageFrostSourceKey)) {}
                com.music.bitchord.ui.components.AddonEditorAlert(
                    hazeState = appHaze,
                    pageSourceKey = PageFrostSourceKey,
                    title = "Synthetic editor",
                    description = "Synthetic fixture - no server behind this field.",
                    urlValue = "",
                    onUrlChange = {},
                    urlPlaceholder = "https://",
                    status = null,
                    statusIsGood = false,
                    testing = false,
                    canSubmit = false,
                    onTest = {},
                    onSave = {},
                    onRemove = null,
                    onDismiss = {},
                )
            }
        }

        rule.onNodeWithText("Synthetic editor").assertExists()
        rule.onNodeWithText(str(R.string.test)).assertExists()
        rule.onNodeWithText(str(R.string.save)).assertExists()
        AppSettings.setBlurEffects(true)
        capture("06-addon-editor-overlay-REPLICA")
        AppSettings.setBlurEffects(false)
        rule.waitForIdle()
        capture("06-addon-editor-overlay-REPLICA")
    }

    private companion object {
        const val LT_PREFS = "bitchord_listen_together"
    }
}
