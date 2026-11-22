package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Player overflow glyphs. The stopwatch keeps lyrics timing distinct from Automix sliders. */
internal object SpoolMenuIcons {
    val Swap by lazy { line("spool_swap", "M8 3L4 7l4 4M4 7h16M16 21l4-4-4-4M20 17H4") }
    val Album by lazy { line("spool_album", "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0M14.5 12a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0") }
    val Artist by lazy { line("spool_artist", "M16 8a4 4 0 1 1-8 0 4 4 0 0 1 8 0M4 21c0-4 3.6-6.5 8-6.5s8 2.5 8 6.5") }
    val Offset by lazy { line("spool_lyrics_offset", "M20 13a8 8 0 1 1-16 0 8 8 0 0 1 16 0M12 9v4l2.5 2.5M9 2h6") }
    val Copy by lazy { line("spool_copy", "M11 9h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1") }
    val Moon by lazy { line("spool_moon", "M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8Z") }

    private fun line(name: String, data: String): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = addPathNodes(data), fill = null, stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
