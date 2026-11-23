package com.music.bitchord.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.*

/** Presentation only. The caller remains the sole owner of rating and rollback. */
@Composable
fun RowScope.SparkRatingAction(
    active: Boolean,
    dislike: Boolean,
    label: String,
    activeLabel: String,
    onClick: () -> Unit,
) {
    val colors = iosMenuColors()
    val accent = if (dislike) Color(0xFF9A9AA6) else Color(0xFFFF5C7A)
    val fill by animateFloatAsState(if (active) 1f else 0f, tween(200), label = "rating paint")
    val pop = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active != previous) {
            previous = active
            if (active) {
                pop.snapTo(.8f); tilt.snapTo(-10f); burst.snapTo(0f)
                launch { pop.animateTo(1f, spring(.55f, Spring.StiffnessMediumLow)) }
                launch { tilt.animateTo(0f, spring(.55f, Spring.StiffnessMediumLow)) }
                burst.animateTo(1f, tween(1000))
            } else {
                burst.snapTo(1f); tilt.snapTo(0f); pop.snapTo(.92f)
                pop.animateTo(1f, tween(180))
            }
        }
    }
    val thumb = remember {
        PathParser().parsePathString("M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z").toPath()
    }
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .clip(RoundedCornerShape(14.dp)).background(colors.pressed)
            .semantics { selected = active }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(23.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(23.dp)) {
                rotate((if (dislike) 180f else 0f) + tilt.value) {
                    scale(pop.value) {
                        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                            // Same path and placement throughout. Never crossfade two icons.
                            drawPath(thumb, accent.copy(alpha = fill))
                            drawPath(thumb, colors.label.copy(alpha = 1f - fill), style = Stroke(1.6f, join = StrokeJoin.Round))
                        }
                    }
                }
                if (active && burst.value < 1f) {
                    val count = if (dislike) 9 else 12
                    repeat(count) { i ->
                        val p = ((burst.value * 1000f - i * 25f) / 650f).coerceIn(0f, 1f)
                        if (p > 0f && p < 1f) {
                            val angle = (i * 30f + 20f) * PI.toFloat() / 180f
                            val travel = (18f + i % 4 * 5f).dp.toPx()
                            val ease = 1f - (1f - p).pow(3)
                            val x = if (dislike) sin(i * 1.7f) * 12.dp.toPx() * p else cos(angle) * travel * ease
                            val y = if (dislike) (22f * p * p + 5f * p).dp.toPx() else sin(angle) * travel * ease + 16.dp.toPx() * p * p
                            drawCircle(accent.copy(alpha = .85f * (1f - p)), (1.6f + i % 3 * .35f).dp.toPx() * (1f - .5f * p), center + androidx.compose.ui.geometry.Offset(x, y))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // One text node, immediate truthful state, no overlapping outgoing label.
        Text(if (active) activeLabel else label, color = colors.label, fontSize = 11.5.sp,
            lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
