package com.music.bitchord.ui.components

import com.music.bitchord.R

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Same UIAlertController shape as [UpdateAvailableDialog] — frosted card,
 * hairline rules, full-width stacked actions — but with a text field for the
 * one bit of input this alert needs.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ListenBrainzTokenAlert(
    hazeState: HazeState,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ApprovedSettingsPanel(
        hazeState = hazeState, title = stringResource(R.string.listenbrainz_token),
        subtitle = stringResource(R.string.listenbrainz_token_description), onDismiss = onDismiss,
        footer = {
            ApprovedPanelAction(stringResource(R.string.save), onSave)
            ApprovedPanelAction(stringResource(R.string.cancel), onDismiss, style = ApprovedActionStyle.SECONDARY)
        },
    ) {
        ApprovedFieldLabel(stringResource(R.string.api_token))
        ApprovedPanelTextField(value = tokenInput, onValueChange = onTokenInputChange,
            placeholder = stringResource(R.string.api_token),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }))
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun LastfmLoginAlert(
    hazeState: HazeState,
    usernameInput: String,
    onUsernameInputChange: (String) -> Unit,
    passwordInput: String,
    onPasswordInputChange: (String) -> Unit,
    error: String?,
    loading: Boolean,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    ApprovedSettingsPanel(
        hazeState = hazeState, title = stringResource(R.string.lastfm_login),
        subtitle = stringResource(R.string.lastfm_login_description), onDismiss = onDismiss,
        backEnabled = !loading, dragEnabled = !loading,
        footer = {
            ApprovedPanelAction(
                if (loading) stringResource(R.string.signing_in) else stringResource(R.string.sign_in),
                onSignIn, enabled = !loading && usernameInput.isNotBlank() && passwordInput.isNotBlank())
            ApprovedPanelAction(stringResource(R.string.cancel), onDismiss,
                enabled = !loading, style = ApprovedActionStyle.SECONDARY)
        },
    ) {
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        ApprovedFieldLabel(stringResource(R.string.username))
        ApprovedPanelTextField(value = usernameInput, onValueChange = onUsernameInputChange,
            placeholder = stringResource(R.string.username), enabled = !loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        ApprovedFieldLabel(stringResource(R.string.password))
        ApprovedPanelTextField(value = passwordInput, onValueChange = onPasswordInputChange,
            placeholder = stringResource(R.string.password), enabled = !loading, isPassword = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (usernameInput.isNotBlank() && passwordInput.isNotBlank()) onSignIn()
            }))
    }
}

/**
 * Manual token entry, for when the in-app login can't run — a WebView an OEM
 * has broken, or a token lifted from a desktop client.
 *
 * [error] carries back what the verification attempt said, because a token that
 * was mistyped or has expired is indistinguishable from one that works until
 * Discord is asked about it.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun DiscordTokenAlert(
    hazeState: HazeState,
    tokenInput: String,
    onTokenInputChange: (String) -> Unit,
    error: String?,
    loading: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ApprovedSettingsPanel(
        hazeState = hazeState, title = stringResource(R.string.discord_token),
        subtitle = stringResource(R.string.panel_discord_description), onDismiss = onDismiss,
        backEnabled = !loading,
        footer = {
            ApprovedPanelAction(if (loading) stringResource(R.string.checking) else stringResource(R.string.save),
                onSave, enabled = !loading && tokenInput.isNotBlank())
            ApprovedPanelAction(stringResource(R.string.cancel), onDismiss,
                enabled = !loading, style = ApprovedActionStyle.SECONDARY)
        },
    ) {
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        ApprovedFieldLabel(stringResource(R.string.token))
        ApprovedPanelTextField(value = tokenInput, onValueChange = onTokenInputChange,
            placeholder = stringResource(R.string.token), enabled = !loading, isPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (tokenInput.isNotBlank()) onSave() }))
        Spacer(Modifier.height(24.dp))
        ApprovedFieldLabel(stringResource(R.string.panel_account_access))
        Text(stringResource(R.string.panel_discord_warning), color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * One free-text presence field — an activity name, a button label.
 *
 * [message] is where the caller explains the field, including which `{...}`
 * variables it accepts, since that is the only place a user would find out.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TextValueAlert(
    hazeState: HazeState,
    title: String,
    message: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    /** False greys Save out — for a field that isn't worth saving empty. */
    saveEnabled: Boolean = true,
    /** A third action between Save and Cancel, for a value that can be cleared. */
    onRemove: (() -> Unit)? = null,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            PillTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = placeholder,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
            )
        }
        AlertRule()
        AlertAction(
            label = stringResource(R.string.save),
            emphasised = true,
            onClick = onSave,
            enabled = saveEnabled,
        )
        if (onRemove != null) {
            AlertRule()
            AlertAction(label = stringResource(R.string.remove), emphasised = false, onClick = onRemove)
        }
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

/**
 * One field of a server editor: the value, its change handler, the hint, and
 * how the keyboard behaves on it. The last field always gets Done-to-save
 * while the rest advance with Next, which is what every editor below already
 * did by hand.
 */
data class EditorField(
    val value: String,
    val onChange: (String) -> Unit,
    val placeholder: String,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val isPassword: Boolean = false,
    val label: String? = null,
)

/**
 * The shape every server editor in this app shares: a centered title, the
 * last test result in place of the description, the fields, then Test above
 * Save above Cancel — plus an optional destructive row between Save and
 * Cancel for an entry that can also be removed.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ServerEditorAlert(
    hazeState: HazeState,
    title: String,
    description: String,
    fields: List<EditorField>,
    /** What the last test said, or null before one has been run. */
    status: String?,
    statusIsGood: Boolean,
    testing: Boolean,
    /** Whether there is enough typed in to be worth testing or saving. */
    canSubmit: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
    removeLabel: String? = null,
    /** Offered only when there is something stored to remove. */
    onRemove: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    adaptiveSurface: Boolean = false,
    approvedPanel: Boolean = false,
    supportingText: String? = null,
    pageSourceKey: Any? = null,
) {
    if (approvedPanel) {
        ApprovedSettingsPanel(
            hazeState = hazeState, pageSourceKey = pageSourceKey, title = title, subtitle = description,
            onDismiss = { if (!testing) onDismiss() }, backEnabled = !testing, dragEnabled = !testing,
            footer = {
                ApprovedPanelAction(if (testing) stringResource(R.string.testing) else stringResource(R.string.test),
                    onTest, enabled = canSubmit && !testing, style = ApprovedActionStyle.SECONDARY)
                ApprovedPanelAction(stringResource(R.string.save), onSave, enabled = canSubmit && !testing)
                if (onRemove != null && removeLabel != null) {
                    ApprovedPanelAction(removeLabel, onRemove, enabled = !testing, style = ApprovedActionStyle.DESTRUCTIVE)
                }
                ApprovedPanelAction(stringResource(R.string.cancel), onDismiss, enabled = !testing,
                    style = ApprovedActionStyle.SECONDARY)
            },
        ) {
            if (status != null) Text(status, color = if (statusIsGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            fields.forEachIndexed { index, field ->
                ApprovedFieldLabel(field.label ?: field.placeholder)
                ApprovedPanelTextField(value = field.value, onValueChange = field.onChange,
                    placeholder = field.placeholder, enabled = !testing, isPassword = field.isPassword,
                    keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType,
                        imeAction = if (index == fields.lastIndex) ImeAction.Done else ImeAction.Next),
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit && !testing) onSave() }))
            }
            if (supportingText != null) Text(supportingText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val fullHeight = adaptiveSurface && LocalDensity.current.fontScale >= 1.5f
    EditorAlertFrame(hazeState, { if (!testing) onDismiss() }, fullHeight, form = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = if (fullHeight) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (fullHeight) TextAlign.Start else TextAlign.Center,
            )
            Text(
                text = status ?: description,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = when {
                    status == null -> MaterialTheme.colorScheme.onSurface
                    statusIsGood -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
                textAlign = if (fullHeight) TextAlign.Start else TextAlign.Center,
            )
            fields.forEachIndexed { index, field ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                PillTextField(
                    value = field.value,
                    onValueChange = field.onChange,
                    placeholder = field.placeholder,
                    enabled = !testing,
                    isPassword = field.isPassword,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = field.keyboardType,
                        imeAction = if (index == fields.lastIndex) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (canSubmit && !testing) onSave() },
                    ),
                )
            }
        }
    }, actions = {
        if (fullHeight) Spacer(Modifier.height(6.dp)) else AlertRule()
        // Above Save rather than beside it: an address is worth checking before
        // it is stored, and a row of three cramped buttons is what the Material
        // dialog did badly.
        EditorAlertAction(
            fullHeight = fullHeight,
            label = if (testing) stringResource(R.string.testing) else stringResource(R.string.test),
            emphasised = false,
            onClick = onTest,
            enabled = canSubmit && !testing,
        )
        if (fullHeight) Spacer(Modifier.height(6.dp)) else AlertRule()
        EditorAlertAction(
            fullHeight = fullHeight,
            label = stringResource(R.string.save),
            emphasised = true,
            onClick = onSave,
            enabled = canSubmit && !testing,
        )
        if (onRemove != null && removeLabel != null) {
            if (fullHeight) Spacer(Modifier.height(6.dp)) else AlertRule()
            EditorAlertAction(
            fullHeight = fullHeight,
                label = removeLabel,
                emphasised = false,
                destructive = true,
                onClick = onRemove,
                enabled = !testing,
            )
        }
        if (fullHeight) Spacer(Modifier.height(6.dp)) else AlertRule()
        EditorAlertAction(
            fullHeight = fullHeight,
            label = stringResource(R.string.cancel),
            emphasised = false,
            onClick = onDismiss,
            enabled = !testing,
        )
    })
}

/**
 * One field of a hosted server editor: the saved value it opens with, the
 * hint, and how the keyboard behaves on it.
 */
data class FieldConfig(
    val initial: String,
    val placeholder: String,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val isPassword: Boolean = false,
    val label: String? = null,
)

/**
 * A server editor that owns its own state: field values, the last test
 * result and the testing flag. The caller hands over fixed field configs
 * plus what test and save mean for those values, and gets back the same
 * frosted card as [ServerEditorAlert] — without the dozen remembered states
 * each host otherwise repeats.
 *
 * The configs are fixed per call site, so remembering once per opening is
 * sound; the host leaves composition on dismiss, which is when the next
 * opening starts over from the saved values.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ServerEditorHost(
    hazeState: HazeState,
    title: String,
    description: String,
    fields: List<FieldConfig>,
    canSubmit: (List<String>) -> Boolean,
    testFailedRes: Int,
    onTest: suspend (List<String>) -> Result<Unit>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    adaptiveSurface: Boolean = false,
    approvedPanel: Boolean = false,
    pageSourceKey: Any? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states = remember { fields.map { mutableStateOf(it.initial) } }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsGood by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val values = states.map { it.value }
    ServerEditorAlert(
        adaptiveSurface = adaptiveSurface,
        approvedPanel = approvedPanel,
        pageSourceKey = pageSourceKey,
        hazeState = hazeState,
        title = title,
        description = description,
        fields = fields.mapIndexed { index, field ->
            EditorField(
                value = states[index].value,
                onChange = { states[index].value = it; status = null },
                placeholder = field.placeholder,
                keyboardType = field.keyboardType,
                isPassword = field.isPassword,
                label = field.label,
            )
        },
        status = status,
        statusIsGood = statusIsGood,
        testing = testing,
        canSubmit = canSubmit(values),
        onTest = {
            testing = true
            status = null
            scope.launch {
                onTest(values).fold(
                    onSuccess = {
                        status = context.getString(R.string.connected)
                        statusIsGood = true
                    },
                    onFailure = {
                        status = context.getString(
                            testFailedRes,
                            it.message ?: context.getString(R.string.failed),
                        )
                        statusIsGood = false
                    },
                )
                testing = false
            }
        },
        onSave = { onSave(values) },
        onDismiss = onDismiss,
    )
}

/**
 * Add or edit a source that has an address — the addon editor.
 *
 * The same frosted card every other alert in this app uses, rather than the
 * Material `AlertDialog` this replaced. That one put a filled `OutlinedTextField`
 * and a row of cramped text buttons in the middle of a screen where nothing
 * else looks like that, and it read as a stock widget dropped into somebody
 * else's design.
 *
 * One field and up to four stacked actions. There is deliberately no name
 * field: an addon states its own name in its manifest, so asking the user to
 * invent one is asking for information the addon is about to supply anyway —
 * and a blank field would leave the row showing a bare hostname next to a
 * perfectly good published name.
 *
 * [status] is the one thing here that no other alert in the file needs:
 * testing an address has *three* outcomes rather than the usual two, and "it
 * answered, but not with something this app can use" is the one worth reading
 * — so a result replaces the description in place, coloured by [statusIsGood],
 * the way [LastfmLoginAlert] surfaces a failed sign-in.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AddonEditorAlert(
    hazeState: HazeState,
    title: String,
    description: String,
    urlValue: String,
    onUrlChange: (String) -> Unit,
    urlPlaceholder: String,
    /** What the last test said, or null before one has been run. */
    status: String?,
    statusIsGood: Boolean,
    testing: Boolean,
    /** Whether there is enough typed in to be worth testing or saving. */
    canSubmit: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
    /** Offered only for a source already stored — there is nothing to remove otherwise. */
    onRemove: (() -> Unit)?,
    /**
     * What [onRemove] is called, for the callers that are not removing a source.
     *
     * The party server's address wears this same card — one field, an address
     * to test, four stacked actions — and "Remove source" would be the one line
     * on it still talking about addons.
     */
    removeLabel: String? = null,
    onDismiss: () -> Unit,
    adaptiveSurface: Boolean = false,
    approvedPanel: Boolean = false,
    pageSourceKey: Any? = null,
) {
    ServerEditorAlert(
        adaptiveSurface = adaptiveSurface,
        approvedPanel = approvedPanel,
        pageSourceKey = pageSourceKey,
        supportingText = if (approvedPanel) stringResource(R.string.panel_addon_help) else null,
        hazeState = hazeState,
        title = title,
        description = if (approvedPanel) stringResource(R.string.panel_addon_description) else description,
        fields = listOf(
            EditorField(
                value = urlValue,
                onChange = onUrlChange,
                placeholder = urlPlaceholder,
                keyboardType = KeyboardType.Uri,
                label = if (approvedPanel) stringResource(R.string.panel_addon_url) else null,
            ),
        ),
        status = status,
        statusIsGood = statusIsGood,
        testing = testing,
        canSubmit = canSubmit,
        onTest = onTest,
        onSave = onSave,
        removeLabel = removeLabel ?: stringResource(R.string.remove_source),
        onRemove = onRemove,
        onDismiss = onDismiss,
    )
}

/**
 * A name clash mid-upload: the server already holds a file under the name a
 * track would land as.
 *
 * Three stacked outcomes rather than a yes/no — overwriting destroys the
 * server copy, so the non-destructive answer leads as the emphasised action.
 * [showApplyToAll] is only true mid-batch, where one answer can carry every
 * clash still queued behind this one.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun WebDavConflictAlert(
    hazeState: HazeState,
    fileName: String,
    showApplyToAll: Boolean,
    applyToAll: Boolean,
    onApplyToAllChange: (Boolean) -> Unit,
    onOverwrite: () -> Unit,
    onKeepBoth: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    pageSourceKey: Any? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val batch = showApplyToAll && applyToAll
    ApprovedSettingsPanel(
        hazeState = hazeState,
        pageSourceKey = pageSourceKey,
        title = stringResource(R.string.conflict_panel_title),
        subtitle = stringResource(R.string.upload_to_webdav),
        onDismiss = onDismiss,
        onBack = onDismiss,
        footer = {
            Text(
                stringResource(if (batch) R.string.conflict_close_many else R.string.conflict_close_one),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface.copy(alpha = 0.85f),
            )
            ApprovedPanelAction(stringResource(R.string.webdav_keep_both), onKeepBoth)
            ApprovedPanelAction(stringResource(R.string.webdav_overwrite), onOverwrite,
                style = ApprovedActionStyle.DESTRUCTIVE)
            ApprovedPanelAction(stringResource(if (batch) R.string.conflict_skip_many else R.string.conflict_skip_one),
                onSkip, style = ApprovedActionStyle.SECONDARY)
        },
    ) {
        Text(fileName, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        Text(stringResource(if (batch) R.string.conflict_scope_many else R.string.conflict_exists),
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface.copy(alpha = 0.85f))
        Spacer(Modifier.height(24.dp))
        ConflictOutcomeDescription(stringResource(R.string.webdav_keep_both),
            stringResource(if (batch) R.string.conflict_keep_many else R.string.conflict_keep_one))
        Spacer(Modifier.height(12.dp))
        ConflictOutcomeDescription(stringResource(R.string.webdav_overwrite),
            stringResource(if (batch) R.string.conflict_overwrite_many else R.string.conflict_overwrite_one),
            destructive = true)
        Spacer(Modifier.height(12.dp))
        ConflictOutcomeDescription(stringResource(R.string.webdav_skip),
            stringResource(if (batch) R.string.conflict_skip_detail_many else R.string.conflict_skip_detail_one))
        if (showApplyToAll) {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .toggleable(value = applyToAll, role = Role.Switch, onValueChange = onApplyToAllChange)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.webdav_apply_to_all), color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (batch) FontWeight.SemiBold else FontWeight.Normal)
                    Text(stringResource(if (batch) R.string.conflict_scope_many else R.string.conflict_toggle_hint),
                        color = scheme.onSurface.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = applyToAll, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun ConflictOutcomeDescription(label: String, detail: String, destructive: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = if (destructive) scheme.error else scheme.onSurface)
        Text(detail, modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface.copy(alpha = 0.85f))
    }
}

/**
 * A two-action confirmation using the same frosted UIAlertController shell as
 * the addon editor. Kept here so warnings opened from settings do not fall back
 * to a visually unrelated Material dialog.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ConfirmationAlert(
    hazeState: HazeState,
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    pageSourceKey: Any? = null,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss, pageSourceKey = pageSourceKey) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        AlertRule()
        AlertAction(label = confirmLabel, emphasised = true, onClick = onConfirm)
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

/**
 * [ConfirmationAlert] with nothing to confirm: a title, an explanation and
 * one OK. For telling the listener why something didn't happen, in the same
 * frosted shell as the rest, rather than in a system dialog that looks like
 * it belongs to another app.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun InfoAlert(
    hazeState: HazeState,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    /**
     * Opens the alert in a window of its own, above every other window.
     *
     * Needed whenever what prompted it can be on screen in another window: the
     * full player and the track menu are both bottom-sheet dialogs, and an
     * alert drawn in the activity's window opens *behind* them — found only
     * once the player was closed, long after the tap it was answering.
     */
    inOwnWindow: Boolean = false,
    pageSourceKey: Any? = null,
) {
    if (inOwnWindow) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            // The scaffold draws its own flat scrim; the platform's would double it.
            SideEffect { window?.setDimAmount(0f) }
            WindowStatusFrost(windowAnchored = true, window = window) {
                InfoAlertFrame(hazeState, title, description, onDismiss, frosted = false)
            }
        }
    } else {
        InfoAlertFrame(hazeState, title, description, onDismiss, frosted = true, pageSourceKey = pageSourceKey)
    }
}

@Composable
private fun InfoAlertFrame(
    hazeState: HazeState,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    frosted: Boolean,
    pageSourceKey: Any? = null,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss, frosted = frosted, pageSourceKey = pageSourceKey) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        AlertRule()
        AlertAction(label = stringResource(android.R.string.ok), emphasised = true, onClick = onDismiss)
    }
}

/**
 * Single-select list, ticked like [LyricsSourcesDialog] rather than with radio
 * buttons — same reasoning: a column of Material radios would be the one
 * Material thing left on an otherwise Apple-shaped alert.
 *
 * Picking commits immediately and closes, so there is no Save action to reach
 * for; Cancel is the only one, and it's the dismiss.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun <T> ChoiceAlert(
    hazeState: HazeState,
    title: String,
    message: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    detail: (T) -> String? = { null },
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertScaffold(hazeState = hazeState, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (message != null) {
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        options.forEach { option ->
            AlertRule()
            ChoiceRow(
                label = label(option),
                detail = detail(option),
                checked = option == selected,
                onClick = { onSelect(option) },
            )
        }
        AlertRule()
        AlertAction(label = stringResource(R.string.cancel), emphasised = false, onClick = onDismiss)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    detail: String?,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            // iOS washes the whole row instead of drawing a ripple inside it.
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** Only opted-in large-text editors use this full-height form and fixed action region. */
@Composable
private fun EditorAlertFrame(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    fullHeight: Boolean,
    form: @Composable () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    if (!fullHeight) {
        AlertScaffold(hazeState, onDismiss, scrollContent = true) {
            form()
            actions()
        }
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { form() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), content = actions)
    }
}

@Composable
private fun EditorAlertAction(
    label: String,
    emphasised: Boolean,
    onClick: () -> Unit,
    fullHeight: Boolean,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    if (!fullHeight) {
        AlertAction(label, emphasised, onClick, destructive = destructive, enabled = enabled)
    } else {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
                color = (if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    .copy(alpha = if (enabled) 1f else 0.38f),
                textAlign = TextAlign.Center)
        }
    }
}

/** The scrim + frosted card frame shared by every UIAlertController-style dialog. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun AlertScaffold(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    /**
     * False for an alert in a window of its own, where [hazeState] has nothing
     * to blur — Haze reads only its own window. The card is then the plain
     * surface the reduced-blur setting uses; see [InfoAlert]'s `inOwnWindow`.
     */
    frosted: Boolean = true,
    scrollContent: Boolean = false,
    pageSourceKey: Any? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(ALERT_CORNER)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(ALERT_WIDTH)
                .then(if (scrollContent) Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp - 32).coerceAtLeast(160).dp) else Modifier)
                .frostedCard(if (frosted) hazeState else null, MaterialTheme.colorScheme.surface, shape, pageSourceKey = pageSourceKey)
                .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            content = content,
        )
    }
}
