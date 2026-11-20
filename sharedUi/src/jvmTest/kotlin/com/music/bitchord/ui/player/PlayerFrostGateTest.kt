package com.music.bitchord.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.components.FrostMaterial
import com.music.bitchord.ui.components.MenuFrostDark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The player drawers' Blur-effects gate, rendered for real: the exact
 * conditional every drawer over the player carries (see LyricsOffsetSheet,
 * PlayerDrawer) - haze blur through FrostMaterial when
 * [PlayerHost.frostEnabled], the solid fill when it is not. ON must differ
 * from OFF over the same busy art (identical pixels would mean the switch or
 * the haze source wiring does nothing), and OFF must be exactly the solid
 * fill, so a tint can never be passed off as blur.
 *
 * LIMITED on purpose: a pixel difference proves the gate flips the render
 * path, not that the blur diffuses well. The diffusion evidence stays the
 * run-61 artifact analysis (on/off crops, outside-card identity, gradient
 * energy), and real blur quality still needs a device.
 */
@OptIn(ExperimentalTestApi::class)
class PlayerFrostGateTest {

    private fun installFrostHost(enabled: Boolean) {
        installQueueTestHost()
        val base = PlayerPlatform.host
        PlayerPlatform.install(object : PlayerHost by base {
            @Composable
            override fun frostEnabled(): Boolean = enabled
        })
    }

    private fun ComposeUiTest.renderPanel(): ImageBitmap {
        setContent {
            val haze = remember { HazeState() }
            Box(Modifier.fillMaxSize()) {
                // Busy stand-in art: smooth blocks would hide transparency.
                Canvas(Modifier.fillMaxSize().hazeSource(haze)) {
                    val cell = 24.dp.toPx()
                    var row = 0
                    var y = 0f
                    while (y < size.height) {
                        var x = 0f
                        var col = row
                        while (x < size.width) {
                            drawRect(
                                color = when ((col + row) % 3) {
                                    0 -> Color(0xFF7A3FF2)
                                    1 -> Color(0xFF00B8A9)
                                    else -> Color(0xFFFFC641)
                                },
                                topLeft = Offset(x, y),
                                size = Size(cell, cell),
                            )
                            x += cell
                            col++
                        }
                        y += cell
                        row++
                    }
                }
                // The panel, with the drawers' exact gate.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.BottomCenter)
                        .then(
                            if (PlayerPlatform.host.frostEnabled()) {
                                Modifier.hazeEffect(state = haze, style = FrostMaterial.style(MenuFrostDark))
                            } else {
                                Modifier.background(MenuFrostDark)
                            },
                        ),
                )
            }
        }
        return onRoot().captureToImage()
    }

    /** A row of pixels across the panel's middle. */
    private fun panelRow(image: ImageBitmap): IntArray {
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        val y = image.height - 60
        return pixels.sliceArray(y * image.width until (y + 1) * image.width)
    }

    @Test
    fun frostGateFlipsTheRenderPath() = runComposeUiTest {
        installFrostHost(enabled = false)
        val off = panelRow(renderPanel())
        installFrostHost(enabled = true)
        val on = panelRow(renderPanel())

        assertEquals(off.size, on.size)
        // OFF: the panel is the solid fill, top to bottom - no page in it.
        val solid = 0xFF1C1C1E.toInt()
        assertTrue(
            off.all { it == solid },
            "blur OFF must draw exactly MenuFrostDark over the busy art",
        )
        // ON: the blurred page shows through the tint, so pixels move.
        val moved = off.indices.count { off[it] != on[it] }
        assertTrue(
            moved > off.size / 2,
            "blur ON must differ from OFF across the panel (was $moved of ${off.size} pixels)",
        )
    }
}
