package com.music.bitchord.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ThumbnailBorderColorTest {
    @Test fun darkAppPageUsesWhiteHairlineRegardlessOfSystemTheme() {
        assertEquals(Color.White.copy(alpha = 0.15f), thumbnailBorderColor(Color(0xFF1B1B1B)))
        assertEquals(Color.White.copy(alpha = 0.15f), thumbnailBorderColor(Color.Black))
    }
    @Test fun lightAppPageUsesBlackHairlineRegardlessOfSystemTheme() {
        assertEquals(Color.Black.copy(alpha = 0.15f), thumbnailBorderColor(Color(0xFFF1F1F1)))
        assertEquals(Color.Black.copy(alpha = 0.15f), thumbnailBorderColor(Color.White))
    }
}
