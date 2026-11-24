package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.MusicRegion
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import java.util.Locale

/** "India · Malayalam, Tamil" — what the Find Your Mood pill and the settings row show. */
fun musicRegionSummary(state: MusicRegion.State, maxLanguages: Int = 3): String {
    val names = MusicRegion.activeLanguages(state).map { it.label }
    if (names.isEmpty()) return MusicRegion.displayCountry(MusicRegion.countryOf(state))
    val shown = names.take(maxLanguages).joinToString(", ")
    val more = if (names.size > maxLanguages) " +${names.size - maxLanguages}" else ""
    return "${MusicRegion.displayCountry(MusicRegion.countryOf(state))} \u00b7 $shown$more"
}

/**
 * The music country and languages. The country is detected unless the
 * listener turns detection off and picks one; the languages offered are the
 * ones people listen to in that country, and any number can be ticked.
 */
@Composable
fun MusicRegionDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    pageSourceKey: Any? = null,
) {
    val context = LocalContext.current
    val state by AppSettings.musicRegion.collectAsStateWithLifecycle()
    var choosingCountry by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AppSettings.refreshMusicCountry(context) }

    val country = MusicRegion.countryOf(state)
    val countryName = MusicRegion.displayCountry(country)

    com.music.bitchord.ui.screens.SpoolSettingsSurface {
        if (choosingCountry) {
            val countries = remember {
                Locale.getISOCountries().map { it to MusicRegion.displayCountry(it) }.sortedBy { it.second }
            }
            ApprovedSettingsPanel(
                hazeState = hazeState, pageSourceKey = pageSourceKey,
                title = stringResource(R.string.music_region_country),
                onDismiss = onDismiss, onBack = { choosingCountry = false },
                modifier = modifier, scrollBody = false,
                footer = {
                    ApprovedPanelAction(stringResource(R.string.cancel), { choosingCountry = false },
                        style = ApprovedActionStyle.SECONDARY)
                },
            ) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(countries, key = { it.first }) { (code, name) ->
                        MusicRegionRow(name, selected = code == country) {
                            AppSettings.setMusicCountry(code)
                            choosingCountry = false
                        }
                    }
                }
            }
        } else {
            ApprovedSettingsPanel(
                hazeState = hazeState, pageSourceKey = pageSourceKey,
                title = stringResource(R.string.music_region),
                subtitle = stringResource(R.string.music_region_description),
                onDismiss = onDismiss, modifier = modifier,
                footer = { ApprovedPanelAction(stringResource(R.string.done), onDismiss) },
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { AppSettings.setMusicRegionAuto(!state.auto) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.music_region_detect), fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        val detected = state.detectedCountry?.let { MusicRegion.displayCountry(it) }
                        Text(
                            if (detected != null) stringResource(R.string.music_region_detect_summary, detected)
                            else stringResource(R.string.music_region_detect_none),
                            fontSize = 12.5.sp, lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Switch(checked = state.auto, onCheckedChange = AppSettings::setMusicRegionAuto)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { choosingCountry = true }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.music_region_country), fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(countryName, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Text(stringResource(R.string.music_region_languages), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                val active = MusicRegion.activeLanguages(state).map { it.id }.toSet()
                val offered = MusicRegion.languagesFor(country)
                if (offered.isEmpty()) {
                    Text(stringResource(R.string.music_region_uncovered), fontSize = 13.sp, lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 4.dp))
                } else {
                    if (MusicRegion.needsConfirmation(state)) {
                        Text(stringResource(R.string.music_region_confirm), fontSize = 13.sp, lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        offered.forEach { language ->
                            androidx.compose.material3.FilterChip(
                                selected = language.id in active,
                                onClick = { AppSettings.toggleMusicLanguage(language.id) },
                                label = { Text(language.label) },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.music_region_languages_hint), fontSize = 12.5.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun MusicRegionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
    }
}
