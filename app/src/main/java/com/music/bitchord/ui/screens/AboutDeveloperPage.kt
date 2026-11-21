package com.music.bitchord.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.music.bitchord.BuildConfig

internal const val DEVELOPER_GITHUB = "https://github.com/JoyalGeorge2006"
internal const val DEVELOPER_X = "https://x.com/HUNTER_z1389"
internal const val DEVELOPER_LINKEDIN = "https://www.linkedin.com/in/joyal-george-2006miqu?utm_source=share_via&utm_content=profile&utm_medium=member_android"
internal const val DISCOVERY_LOOP_SITE = "https://www.discoveryloop.live"
internal const val DEVELOPER_CONTACT = "mailto:hello@discoveryloop.live"
internal const val SPOOL_SOURCE = "https://github.com/JoyalGeorge2006/spool"
internal const val UPSTREAM_AUTHOR = "https://github.com/kushagrasinghx"

@Composable
internal fun AboutDeveloperPage(
    contentPadding: PaddingValues,
    onCredits: () -> Unit,
    onOpenLink: ((String) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val openLink: (String) -> Unit = onOpenLink ?: { uri ->
        runCatching { uriHandler.openUri(uri) }.onFailure {
            Toast.makeText(context, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        }
    }
    SpoolSettingsSurface {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(contentPadding)) {
            SettingsPageTitle(stringResource(R.string.about_developer_title))
            SettingsGroup(header = stringResource(R.string.about_developer_header), eyebrow = true, topSpacing = 0.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    Image(painterResource(R.drawable.developer_profile),
                        contentDescription = stringResource(R.string.about_developer_photo_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Joyal George", style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp, fontWeight = FontWeight.W600),
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.about_developer_bio),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                RowDivider()
                SettingsRow(title = "GitHub", value = "JoyalGeorge2006", onClick = { openLink(DEVELOPER_GITHUB) })
                RowDivider()
                SettingsRow(title = "X", value = "@HUNTER_z1389", onClick = { openLink(DEVELOPER_X) })
                RowDivider()
                SettingsRow(title = "LinkedIn", value = "Joyal George", onClick = { openLink(DEVELOPER_LINKEDIN) })
                RowDivider()
                SettingsRow(title = stringResource(R.string.about_contact), value = "hello@discoveryloop.live", onClick = { openLink(DEVELOPER_CONTACT) })
            }
            SettingsGroup(header = "Discovery Loop", eyebrow = true) {
                SettingsRow(title = "Discovery Loop", subtitle = stringResource(R.string.about_discovery_blurb), subtitleMaxLines = 12)
                RowDivider()
                SettingsRow(title = stringResource(R.string.about_website), value = "discoveryloop.live", onClick = { openLink(DISCOVERY_LOOP_SITE) })
            }
            SettingsGroup(header = "spool", eyebrow = true) {
                SettingsRow(title = "spool", subtitle = stringResource(R.string.about_spool_blurb))
                RowDivider()
                SettingsRow(title = stringResource(R.string.about_credits), onClick = onCredits)
                RowDivider()
                SettingsRow(title = stringResource(R.string.about_source_code), value = "JoyalGeorge2006/spool", onClick = { openLink(SPOOL_SOURCE) })
            }
            AboutVersion()
        }
    }
}

@Composable
private fun AboutVersion() {
    Text(stringResource(R.string.spool_version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

/** Bundled, preserved notices remain readable offline rather than relying on a remote page. */
@Composable
internal fun CreditsLicensesPage(contentPadding: PaddingValues, onOpenNotice: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    fun openLink(uri: String) {
        runCatching { uriHandler.openUri(uri) }.onFailure {
            Toast.makeText(context, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
        }
    }
    SpoolSettingsSurface {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(contentPadding)) {
            SettingsPageTitle(stringResource(R.string.about_credits))
            SettingsGroup(header = stringResource(R.string.credits_spool_bitchord), eyebrow = true) {
                SettingsRow(title = stringResource(R.string.credits_original_project), subtitle = stringResource(R.string.credits_original_project_subtitle), subtitleMaxLines = 12)
                RowDivider()
                SettingsRow(title = stringResource(R.string.credits_upstream_developer), value = "kushagrasinghx", onClick = { openLink(UPSTREAM_AUTHOR) })
                RowDivider()
                SettingsRow(title = stringResource(R.string.credits_spool_source), value = "JoyalGeorge2006/spool", onClick = { openLink(SPOOL_SOURCE) })
                RowDivider()
                SettingsRow(title = "GNU GPL v3", subtitle = stringResource(R.string.credits_gpl_subtitle), subtitleMaxLines = 12, onClick = { onOpenNotice("GPL-3.0.txt") })
            }
            SettingsGroup(header = stringResource(R.string.credits_bundled_notices), eyebrow = true,
                footer = stringResource(R.string.credits_bundled_notices_footer)) {
                BUNDLED_NOTICES.forEachIndexed { index, notice ->
                    if (index > 0) RowDivider()
                    SettingsRow(title = notice.second, onClick = { onOpenNotice(notice.first) })
                }
            }
            SettingsGroup(header = stringResource(R.string.credits_services), eyebrow = true) {
                SettingsRow(title = stringResource(R.string.credits_independent_app), subtitle = stringResource(R.string.credits_independent_app_subtitle), subtitleMaxLines = 12)
            }
            AboutVersion()
        }
    }
}

internal val BUNDLED_NOTICES = listOf(
    "DST-NOTICE.txt" to "DST decoder notices",
    "DST-LGPL-2.1.txt" to "DST decoder LGPL v2.1",
    "Signalsmith-Linear.txt" to "Signalsmith Linear",
    "Signalsmith-Stretch.txt" to "Signalsmith Stretch",
    "Vazirmatn-OFL.txt" to "Vazirmatn font",
)

@Composable
internal fun LicenseNoticePage(fileName: String, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val allowed = fileName == "GPL-3.0.txt" || BUNDLED_NOTICES.any { it.first == fileName }
    val text = remember(fileName) {
        if (allowed) context.assets.open("licenses/$fileName").bufferedReader().use { it.readText() }
        else context.getString(R.string.license_notice_unavailable)
    }
    SpoolSettingsSurface {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(contentPadding)) {
            SettingsPageTitle(if (fileName == "GPL-3.0.txt") "GNU GPL v3" else BUNDLED_NOTICES.firstOrNull { it.first == fileName }?.second ?: stringResource(R.string.license_notice))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        }
    }
}
