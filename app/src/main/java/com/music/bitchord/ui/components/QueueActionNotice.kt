package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState

private val NOTICE_COLOR = Color(0xFF282828)
private val NOTICE_SHAPE = RoundedCornerShape(5.dp)

/** A unique queue result, so repeating the same action restarts its lifetime. */
data class QueueActionNotice(
    val id: Int,
    val message: String,
    val minimumDurationMillis: Long = 3_000L,
    val listenerGeneration: Long? = null,
)

/** Brief queue confirmation shown immediately above the mini player. */
@Composable
fun QueueActionNoticeHost(
    notice: QueueActionNotice?,
    modifier: Modifier = Modifier,
    /** The page's haze, so the pill frosts over what is under it; null stays solid. */
    hazeState: HazeState? = null,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = notice,
        transitionSpec = {
            if (reduceAnimation) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn() + slideInVertically { height -> height / 2 }) togetherWith
                    (fadeOut() + slideOutVertically { height -> height / 2 })
            }
        },
        contentKey = { it?.id },
        label = "queueActionNotice",
        modifier = modifier.fillMaxWidth(),
    ) { current ->
        if (current != null) {
            Surface(
                // Transparent: the frost (or its solid fill) is the colour.
                color = Color.Transparent,
                contentColor = Color.White,
                shape = NOTICE_SHAPE,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .frostedCard(hazeState, NOTICE_COLOR, NOTICE_SHAPE)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}
