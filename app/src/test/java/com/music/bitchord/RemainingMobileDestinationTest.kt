package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.components.*
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual secondary Settings destinations, bounded scroll; not installed navigation/IME proof. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class RemainingMobileDestinationTest {
    @get:Rule val compose = createComposeRule()
    private fun probe(page: String, dark: Boolean) {
        V6ScreenshotTest().installUiHost()
        val old = AppSettings.themeMode.value
        AppSettings.setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
        val name = "destination_${page}_${dark}"
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    BitChordTheme(darkTheme = dark) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            val haze = remember { dev.chrisbanes.haze.HazeState() }
                            val scope = rememberCoroutineScope()
                            val context = androidx.compose.ui.platform.LocalContext.current
                            when (page) {
                                "SIGNATURE_EDITOR" -> com.music.bitchord.ui.screens.SignatureApiEditor(haze, {})
                                "ADDON_EDITOR" -> com.music.bitchord.ui.screens.SourceEditorAlert(haze, com.music.bitchord.data.sources.SourceConfig(id = "fixture:addon", kind = com.music.bitchord.data.sources.SourceKind.ADDON), {}, {}, {}, scope)
                                "SOURCE_QUALITY" -> ChoiceAlert(hazeState = haze, title = context.getString(R.string.audio_quality), message = null, options = listOf("Lossless", "High", "Standard"), selected = "Lossless", label = { it }, onSelect = {}, onDismiss = {})
                                "WEBDAV_EDITOR", "SMB_EDITOR" -> {
                                    val smb = page == "SMB_EDITOR"
                                    ServerEditorHost(hazeState = haze,
                                        title = context.getString(if (smb) R.string.smb else R.string.webdav),
                                        description = context.getString(if (smb) R.string.smb_description else R.string.webdav_description),
                                        fields = (if (smb) listOf(R.string.smb_server_hint, R.string.smb_share_hint, R.string.smb_folder_hint, R.string.username, R.string.password) else listOf(R.string.webdav_server_url_hint, R.string.username, R.string.password)).map { FieldConfig("", context.getString(it)) },
                                        canSubmit = { false }, testFailedRes = R.string.webdav_test_failed,
                                        onTest = { Result.failure(IllegalStateException("Offline fixture: no network call")) }, onSave = {}, onDismiss = {})
                                }
                                "ALBUM", "ARTIST", "PLAYLIST", "LOCAL_FOLDER" -> {
                                    val type = when(page) { "ALBUM" -> com.music.bitchord.data.model.BrowseType.ALBUM; "ARTIST" -> com.music.bitchord.data.model.BrowseType.ARTIST; else -> com.music.bitchord.data.model.BrowseType.PLAYLIST }
                                    val songs = List(8) { Song("fixture:$it", "Track ${it+1}", "Fixture artist", null, durationText = "3:24") }
                                    val detail = com.music.bitchord.data.model.DetailPage(browseId = if(page == "LOCAL_FOLDER") "local:all" else "fixture:$page", title = "Fixture ${page.lowercase()}", subtitle = "Layout audit", thumbnailUrl = null, songs = com.music.bitchord.data.model.UiState.Success(songs), type = type)
                                    com.music.bitchord.ui.screens.DetailScreen(page = detail, currentSong = null, isPlaying = false, onSongClick = { _, _ -> }, onSongLongPress = {}, onSongSwipe = {}, onShuffle = {}, onSectionItemClick = {}, onArtistClick = { _, _ -> }, onAddSuggested = {}, contentPadding = PaddingValues(), folder = if(page == "LOCAL_FOLDER") com.music.bitchord.ui.screens.LocalFolder(state = remember { com.music.bitchord.ui.screens.LocalFolderState() }, sort = com.music.bitchord.data.settings.LocalMusicSort.TITLE_ASC, viewType = com.music.bitchord.data.settings.LibraryViewType.LIST, onOpenGroup = {}, onGroupLongPress = {}, tabs = com.music.bitchord.ui.screens.LocalFolderTab.DEVICE) else null)
                                }
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            val identity = when(page) {
                "ALBUM", "ARTIST", "PLAYLIST", "LOCAL_FOLDER" -> "Fixture ${page.lowercase()}"
                "WEBDAV_EDITOR" -> context.getString(R.string.webdav)
                "SMB_EDITOR" -> context.getString(R.string.smb)
                "SOURCE_QUALITY" -> context.getString(R.string.audio_quality)
                else -> null
            }
            if(identity != null) compose.onAllNodesWithText(identity).onFirst().assertExists()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_top.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
            val lists = compose.onAllNodes(hasScrollAction())
            if (lists.fetchSemanticsNodes().isNotEmpty()) {
                val list = lists.onFirst()
                var step = 0
                while (true) {
                    val node = list.fetchSemanticsNode()
                    val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
                    val before = range.value()
                    val max = range.maxValue()
                    println("SECONDARY_POSITION $name step=$step value=$before max=$max")
                    if (before >= max - 1f || step >= 8) break
                    check(step < 40) { "Secondary scroll did not reach end: $name $before/$max" }
                    list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, node.boundsInRoot.height * .65f) }
                    compose.waitForIdle()
                    val after = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                    check(after > before) { "Secondary scroll made no progress: $name $before -> $after" }
                    compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_position_${step.toString().padStart(2, '0')}.png", roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
                    step++
                }
            }
        } finally { AppSettings.setThemeMode(old) }
    }
    @Test fun signature_editor_false() = probe("SIGNATURE_EDITOR", false)
    @Test fun signature_editor_true() = probe("SIGNATURE_EDITOR", true)
    @Test fun addon_editor_false() = probe("ADDON_EDITOR", false)
    @Test fun addon_editor_true() = probe("ADDON_EDITOR", true)
    @Test fun source_quality_false() = probe("SOURCE_QUALITY", false)
    @Test fun source_quality_true() = probe("SOURCE_QUALITY", true)
    @Test fun webdav_editor_false() = probe("WEBDAV_EDITOR", false)
    @Test fun webdav_editor_true() = probe("WEBDAV_EDITOR", true)
    @Test fun smb_editor_false() = probe("SMB_EDITOR", false)
    @Test fun smb_editor_true() = probe("SMB_EDITOR", true)
    @Test fun album_false() = probe("ALBUM", false)
    @Test fun album_true() = probe("ALBUM", true)
    @Test fun artist_false() = probe("ARTIST", false)
    @Test fun artist_true() = probe("ARTIST", true)
    @Test fun playlist_false() = probe("PLAYLIST", false)
    @Test fun playlist_true() = probe("PLAYLIST", true)
    @Test fun local_folder_false() = probe("LOCAL_FOLDER", false)
    @Test fun local_folder_true() = probe("LOCAL_FOLDER", true)
}
