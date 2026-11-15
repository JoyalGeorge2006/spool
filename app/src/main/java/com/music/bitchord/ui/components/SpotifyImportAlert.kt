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

// The approved (22:06) neutral restyle: a solid card that follows the theme,
// tonal field, hairline chips whose selection is solid ink, and one full-width
// monochrome action. No accent hues anywhere in the dialog.
// Dark tones snapped to the approved mock's sampled pixels (card #1C1A20,
// field #27252B, hairline #444348, ink #F2EFF6).
private val IMPORT_CARD_DARK = Color(0xFF1C1A20)
private val IMPORT_FIELD_DARK = Color(0xFF27252B)
private val IMPORT_FIELD_LIGHT = Color(0xFFF1F1F1)
private val IMPORT_INK_DARK = Color(0xFFF2EFF6)
private val IMPORT_INK_LIGHT = Color(0xFF1C1A20)
private val IMPORT_HAIRLINE_DARK = Color(0xFF444348)
private val IMPORT_HAIRLINE_LIGHT = Color(0xFFD9D9D9)

/**
 * Paste a Spotify playlist link, match its songs on YouTube Music, keep the result.
 *
 * The "who can see it" row exists only for [signedIn]: it is a YouTube Music
 * setting, and a playlist kept on this device has no audience to choose.
 * Songs that matched nothing are listed once the playlist is made, instead of
 * being dropped without a word.
 *
 * [hazeState] is kept for the call site; the approved card is a solid fill,
 * so nothing here blurs any more.
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

    // Back is always consumed here, so a press mid-import cannot fall through
    // to the navigator and leave the page underneath while the dialog stays
    // up. While the import runs it does nothing - the scrim's contract. A
    // dismiss is a clean cancel: persistence only happens in onImported,
    // which runs in the caller's view model, so nothing partial survives.
    BackHandler { if (!working) onDismiss() }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val card = if (dark) IMPORT_CARD_DARK else Color.White
    val ink = if (dark) IMPORT_INK_DARK else IMPORT_INK_LIGHT
    val onInk = if (dark) IMPORT_INK_LIGHT else Color.White
    val fieldFill = if (dark) IMPORT_FIELD_DARK else IMPORT_FIELD_LIGHT
    val hairline = if (dark) IMPORT_HAIRLINE_DARK else IMPORT_HAIRLINE_LIGHT
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { if (!working) onDismiss() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 342.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(card)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.spotify_import_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = ink,
                textAlign = TextAlign.Center,
            )
            val missed = unmatched
            if (missed != null) {
                Text(
                    text = stringResource(R.string.spotify_import_unmatched, missed.size),
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = secondary,
                    textAlign = TextAlign.Center,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(fieldFill)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    missed.forEach { track ->
                        Text(
                            text = if (track.artist.isBlank()) track.title else "${track.title} · ${track.artist}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = secondary,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ImportAction(
                    label = stringResource(R.string.done),
                    enabled = true,
                    ink = ink,
                    onInk = onInk,
                    muted = secondary,
                    onClick = onDismiss,
                )
            } else {
                Text(
                    text = status ?: stringResource(
                        if (signedIn) R.string.spotify_import_description
                        else R.string.spotify_import_description_local,
                    ),
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = when {
                        status == null -> secondary
                        failed -> MaterialTheme.colorScheme.error
                        else -> ink
                    },
                    textAlign = TextAlign.Center,
                )
                ImportField(
                    value = link,
                    onValueChange = {
                        link = it
                        if (failed) {
                            status = null
                            failed = false
                        }
                    },
                    placeholder = stringResource(R.string.spotify_import_hint),
                    enabled = !working,
                    fill = fieldFill,
                    ink = ink,
                    hint = secondary,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (link.isNotBlank() && !working) start() }),
                )
                Text(
                    text = stringResource(R.string.spotify_import_language_preference),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W500),
                    color = secondary,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IMPORT_LANGUAGES.forEach { lang ->
                        ImportChip(
                            label = lang,
                            selected = lang == selectedLanguage,
                            enabled = !working,
                            ink = ink,
                            onInk = onInk,
                            hairline = hairline,
                            onClick = { selectedLanguage = lang },
                        )
                    }
                }
                if (selectedLanguage == "Manual / Other") {
                    Spacer(Modifier.height(10.dp))
                    ImportField(
                        value = customLanguageInput,
                        onValueChange = { customLanguageInput = it },
                        placeholder = stringResource(R.string.spotify_import_language_hint),
                        enabled = !working,
                        fill = fieldFill,
                        ink = ink,
                        hint = secondary,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    )
                }
                if (signedIn) {
                    Text(
                        text = stringResource(R.string.who_can_see_it),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W500),
                        color = secondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlaylistPrivacy.entries.forEach { option ->
                            Box(modifier = Modifier.weight(1f)) {
                                ImportChip(
                                    label = option.label,
                                    selected = option == privacy,
                                    enabled = !working,
                                    ink = ink,
                                    onInk = onInk,
                                    hairline = hairline,
                                    onClick = { privacy = option },
                                    fillWidth = true,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                ImportAction(
                    label = stringResource(R.string.spotify_import_button),
                    enabled = link.isNotBlank() && !working,
                    ink = ink,
                    onInk = onInk,
                    muted = secondary,
                    onClick = ::start,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(
                            enabled = !working,
                            role = Role.Button,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.W500),
                        color = secondary,
                    )
                }
            }
        }
    }
}

/** A selectable pill: hairline at rest, solid ink when chosen. */
@Composable
private fun ImportChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    ink: Color,
    onInk: Color,
    hairline: Color,
    onClick: () -> Unit,
    fillWidth: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (selected) ink else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else hairline, shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp, fontWeight = FontWeight.W500),
            color = if (selected) onInk else ink,
            maxLines = 1,
        )
    }
}

/** The one action the dialog asks for: full width, solid ink, quiet when disabled. */
@Composable
private fun ImportAction(
    label: String,
    enabled: Boolean,
    ink: Color,
    onInk: Color,
    muted: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (enabled) ink else ink.copy(alpha = 0.12f))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.W600),
            color = if (enabled) onInk else muted,
        )
    }
}

/** Tonal filled field, 48dp, 12dp corners - the approved field for this dialog. */
@Composable
private fun ImportField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    fill: Color,
    ink: Color,
    hint: Color,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    // The whole padded field is the target: a tap anywhere in it focuses the
    // text field, not only a tap that lands on the glyphs.
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { focusRequester.requestFocus() },
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = hint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, color = ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            cursorBrush = SolidColor(ink),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}
