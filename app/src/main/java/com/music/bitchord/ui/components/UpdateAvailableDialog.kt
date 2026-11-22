package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.music.bitchord.R
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** UIAlertController's own metrics: fixed narrow width, 14pt corner, 44pt rows. */
internal val ALERT_WIDTH = 270.dp
internal val ALERT_CORNER = 14.dp
internal val ACTION_HEIGHT = 44.dp

/**
 * The dim behind the alert. Flat on purpose — the glass is the card, and
 * blurring the wallpaper *behind* it too leaves nothing for the card to be
 * frosted against, which is what made this read as a grey box before.
 */
internal val SCRIM_COLOR = Color.Black.copy(alpha = 0.28f)

private val DOWNLOAD_ROW_HEIGHT = 4.dp

/** How much of the card's height the release notes are allowed to fill before scrolling. */
private val NOTES_MAX_HEIGHT = 220.dp

/**
 * Once-per-launch nudge that a newer build is on GitHub Releases — the top
 * bar's [Icons.Rounded.Upgrade][androidx.compose.material.icons.rounded.Upgrade]
 * icon is the quiet, always-there version of this; this is the one-time,
 * hard-to-miss version shown the moment the check comes back.
 *
 * Shaped like an iOS system alert, which is the same lineage as the rest of the
 * app's Apple Music styling: frosted card, hairline rules, full-width actions
 * stacked under the message rather than a Material button pair in the corner.
 *
 * The update round trip happens here rather than in a browser: Download pulls
 * the release's APK into the app cache (progress fills the hairline under the
 * message), then Install hands it to the system installer. Where the release
 * carries no APK at all, the actions fall back to opening the releases page.
 *
 * Sits over the whole app as an overlay rather than an Android [Dialog][androidx.compose.ui.window.Dialog]
 * so its glass can sample the same [HazeState] the rest of the app's frosted
 * surfaces use, the way [FrostedTopBar] and [MiniPlayer] already do.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun UpdateAvailableDialog(
    version: String,
    notes: String?,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The announcement never owns progress, cancellation or installation.
    Box(modifier.fillMaxSize().background(SCRIM_COLOR).clickable(
        indication=null,interactionSource=remember { MutableInteractionSource() },onClick=onDismiss),
        contentAlignment=Alignment.Center) {
        Column(Modifier.width(ALERT_WIDTH).clip(RoundedCornerShape(ALERT_CORNER))
            .background(MaterialTheme.colorScheme.surface).clickable(
                indication=null,interactionSource=remember { MutableInteractionSource() },onClick={})) {
            Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text("Spool $version is available",color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
                Text("Open App updates to download, check and install this update. You can keep using Spool while it downloads.",
                    Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center)
            }
            AlertRule()
            AlertAction("Open app updates",true,onDownload)
            AlertRule()
            AlertAction("Later",false,onDismiss)
        }
    }
}

/**
 * Full-bleed action row. Tinted rather than filled, so the two read as equals
 * in weight and only the font differentiates the default action — the alert's
 * whole point is that neither choice is a trap.
 *
 * [destructive] is the one exception to that: an action that deletes something
 * is red in this lineage, because the point there *is* to break the symmetry.
 * It stays a plain row rather than a filled button, so it reads as a warning
 * and not as the thing to press.
 */
@Composable
internal fun AlertAction(
    label: String,
    emphasised: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_HEIGHT)
            // iOS washes the whole row instead of drawing a ripple inside it.
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = if (emphasised) FontWeight.W600 else FontWeight.W400,
            ),
            color = (if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                .copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

/** Hairline separator — [HorizontalDivider][androidx.compose.material3.HorizontalDivider]'s 1dp reads as a bar at this scale. */
@Composable
internal fun AlertRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(androidx.compose.ui.graphics.Color.Transparent),
    )
}
