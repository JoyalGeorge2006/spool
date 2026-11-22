package com.music.bitchord.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.SpotifyImportTrack
import com.music.bitchord.data.spotify.SpotifyImporter
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Overall deadline for one import: fetch + per-track matching. Per-call network
 * timeouts bound each request, not the operation, and a blocked dialog whose
 * dismiss paths all wait on `working` needs the operation itself to end. */
private const val IMPORT_TIMEOUT_MS = 5 * 60 * 1000L

private enum class SpotifyImportPane { MAIN, LANGUAGE, MANUAL, VISIBILITY }

private val IMPORT_LANGUAGES = listOf(
    "Tamil",
    "Telugu",
    "Kannada",
    "Malayalam",
    "Hindi",
    "English",
    "Multilingual",
    "No Preference",
    "Manual / Other",
)

/**
 * Paste a Spotify playlist link, match its songs on YouTube Music, keep the result.
 *
 * The "who can see it" row exists only for [signedIn]: it is a YouTube Music
 * setting, and a playlist kept on this device has no audience to choose.
 * Songs that matched nothing are listed once the playlist is made, instead of
 * being dropped without a word.
 *
 * The selected inline presentation shares one draft across its preference views.
 */
@Composable
fun SpotifyImportAlert(
    hazeState: HazeState,
    signedIn: Boolean,
    /** Called once the songs are matched; [privacy] is meaningful only when signed in. */
    onImported: (title: String, privacy: PlaylistPrivacy, songs: List<Song>) -> Unit,
    onDismiss: () -> Unit,
    // Capture hooks for the CI screenshots: they freeze the dialog in a state
    // the import flow would reach on its own. Production callers pass none.
    initialLink: String = "",
    initialLanguage: String = "No Preference",
    initialWorking: Boolean = false,
    initialStatus: String? = null,
    initialFailed: Boolean = false,
    initialUnmatched: List<SpotifyImportTrack>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf(initialLink) }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    var selectedLanguage by remember { mutableStateOf(initialLanguage) }
    var customLanguageInput by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(initialWorking) }
    var status by remember { mutableStateOf(initialStatus) }
    var failed by remember { mutableStateOf(initialFailed) }
    var unmatched by remember { mutableStateOf(initialUnmatched) }

    var pane by remember { mutableStateOf(SpotifyImportPane.MAIN) }

    fun start() {
        val id = SpotifyImporter.extractPlaylistId(link)
        if (id == null) {
            status = context.getString(R.string.spotify_import_invalid)
            failed = true
            return
        }
        val effectiveLanguage = if (selectedLanguage == "Manual / Other") {
            customLanguageInput.trim().ifBlank { null }
        } else {
            selectedLanguage
        }
        working = true
        failed = false
        status = context.getString(R.string.spotify_import_fetching)
        scope.launch {
            try {
                withTimeout(IMPORT_TIMEOUT_MS) {
                    val (name, tracks) = SpotifyImporter.fetchPlaylistTracks(id)
                    val (songs, missed) = SpotifyImporter.resolveToSongs(tracks, effectiveLanguage) { done, total ->
                        status = context.getString(R.string.spotify_import_matching, done, total)
                    }
                    if (songs.isEmpty()) error(context.getString(R.string.spotify_import_none))
                    onImported(name, privacy, songs)
                    if (missed.isEmpty()) onDismiss() else unmatched = missed
                }
            } catch (e: TimeoutCancellationException) {
                // Caught ahead of CancellationException: a deadline is an error
                // the user reads and retries from, never a stuck dialog.
                status = context.getString(R.string.spotify_import_timeout)
                failed = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = context.getString(R.string.spotify_import_failed, e.message.orEmpty())
                failed = true
            }
            working = false
        }
    }

    val goBack: () -> Unit = {
        if (!working) {
            pane = when (pane) {
                SpotifyImportPane.MAIN -> { onDismiss(); SpotifyImportPane.MAIN }
                SpotifyImportPane.MANUAL -> SpotifyImportPane.LANGUAGE
                else -> SpotifyImportPane.MAIN
            }
        }
    }
    // Keep the existing full import lock, including hardware Back.
    BackHandler(onBack = goBack)
    val scheme = MaterialTheme.colorScheme
    val secondary = scheme.onSurface.copy(alpha = 0.85f)
    val panelTitle = when (pane) {
        SpotifyImportPane.MAIN -> stringResource(R.string.spotify_import_title)
        SpotifyImportPane.VISIBILITY -> stringResource(R.string.who_can_see_it)
        else -> stringResource(R.string.spotify_import_language_preference)
    }
    val panelSubtitle = when (pane) {
        SpotifyImportPane.MAIN -> stringResource(if (signedIn) R.string.spotify_import_description else R.string.spotify_import_description_local)
        SpotifyImportPane.LANGUAGE -> stringResource(R.string.panel_language_choices_description)
        SpotifyImportPane.MANUAL -> "Manual / Other"
        SpotifyImportPane.VISIBILITY -> stringResource(R.string.panel_visibility_description)
    }
    ApprovedSettingsPanel(
        hazeState = hazeState, title = panelTitle, subtitle = panelSubtitle,
        onDismiss = { if (!working) onDismiss() }, onBack = goBack,
        backEnabled = !working, dragEnabled = !working,
        footer = {
            if (pane != SpotifyImportPane.MAIN) {
                ApprovedPanelAction(stringResource(R.string.done), { pane = SpotifyImportPane.MAIN })
            } else if (unmatched != null) {
                ApprovedPanelAction(stringResource(R.string.done), onDismiss)
            } else {
                if (link.isBlank() && !working) Text(stringResource(R.string.panel_paste_playlist_link),
                    modifier = Modifier.fillMaxWidth(), color = secondary, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall)
                ApprovedPanelAction(stringResource(R.string.spotify_import_button), ::start,
                    enabled = link.isNotBlank() && !working)
                ApprovedPanelAction(stringResource(R.string.cancel), onDismiss,
                    enabled = !working, style = ApprovedActionStyle.SECONDARY)
            }
        },
    ) {
        when (pane) {
            SpotifyImportPane.LANGUAGE -> {
                IMPORT_LANGUAGES.forEach { language ->
                    ApprovedChoiceRow(label = language, selected = language == selectedLanguage, onClick = {
                        selectedLanguage = language
                        if (language == "Manual / Other") pane = SpotifyImportPane.MANUAL
                    })
                }
            }
            SpotifyImportPane.MANUAL -> {
                ApprovedFieldLabel(stringResource(R.string.panel_matching_language))
                ApprovedPanelTextField(value = customLanguageInput, onValueChange = { customLanguageInput = it },
                    placeholder = stringResource(R.string.spotify_import_language_hint), enabled = !working,
                    container = scheme.surfaceContainerHigh, contentColor = scheme.onSurface, placeholderColor = secondary,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done))
                Text(stringResource(R.string.panel_language_manual_description), color = secondary,
                    style = MaterialTheme.typography.bodyMedium)
            }
            SpotifyImportPane.VISIBILITY -> {
                if (signedIn) PlaylistPrivacy.entries.forEach { option ->
                    ApprovedChoiceRow(option.label, option == privacy, { privacy = option })
                }
            }
            SpotifyImportPane.MAIN -> {
                val missed = unmatched
                if (missed != null) {
                    Text(stringResource(R.string.spotify_import_unmatched, missed.size), color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.fillMaxWidth().heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        missed.forEach { track ->
                            Text(if (track.artist.isBlank()) track.title else "${track.title} · ${track.artist}",
                                color = secondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    if (status != null) Text(status.orEmpty(),
                        color = if (failed) scheme.error else scheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium)
                    ApprovedFieldLabel(stringResource(R.string.panel_playlist_link))
                    ApprovedPanelTextField(value = link, onValueChange = {
                        link = it
                        if (failed) { status = null; failed = false }
                    }, placeholder = stringResource(R.string.spotify_import_hint), enabled = !working,
                        container = scheme.surfaceContainerHigh, contentColor = scheme.onSurface, placeholderColor = secondary,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (link.isNotBlank() && !working) start() }))
                    ApprovedPreferenceRow(stringResource(R.string.spotify_import_language_preference),
                        if (selectedLanguage == "Manual / Other" && customLanguageInput.isNotBlank()) customLanguageInput else selectedLanguage,
                        { pane = SpotifyImportPane.LANGUAGE }, enabled = !working)
                    if (signedIn) ApprovedPreferenceRow(stringResource(R.string.who_can_see_it), privacy.label,
                        { pane = SpotifyImportPane.VISIBILITY }, enabled = !working)
                    Text(stringResource(R.string.panel_choices_import_hint), color = secondary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
