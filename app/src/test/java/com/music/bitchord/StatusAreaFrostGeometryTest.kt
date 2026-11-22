package com.music.bitchord

import com.music.bitchord.ui.components.StatusAreaFrostGeometry as G
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusAreaFrostGeometryTest {
    @Test fun referenceEndIsTotalDistanceNotInsetPlusDistance() {
        assertEquals(93f, G.endForWidth(720f, 85f), 0.001f)
    }
    @Test fun physicalWidthScalingMatchesApprovedReference() {
        assertEquals(139.5f, G.endForWidth(1080f, 127.5f), 0.001f)
    }
    @Test fun unusuallyTallSystemInsetIsNotCropped() {
        assertEquals(110f, G.endForWidth(720f, 110f), 0.001f)
    }
    @Test fun noSystemBarMeansNoStatusMaterial() {
        assertEquals(0f, G.endForWidth(720f, 0f), 0f)
    }
    @Test fun tintHasExactEndpointsAndCubicMidpoint() {
        assertEquals(0.9f, G.tintAlpha(0f), 0.0001f)
        assertEquals(0.7875f, G.tintAlpha(0.5f), 0.0001f)
        assertEquals(0f, G.tintAlpha(1f), 0f)
    }
    @Test fun compositeIsFullThroughReference47AndZeroAt93() {
        assertEquals(1f, G.compositeAlpha(47f / 93f), 0.0001f)
        assertEquals(0.5f, G.compositeAlpha(70f / 93f), 0.0001f)
        assertEquals(0f, G.compositeAlpha(1f), 0f)
    }
    @Test fun bothCurvesAreBoundedAndMonotonic() {
        for (i in 0..999) {
            val a = i / 999f
            val b = (i + 1) / 999f
            assertTrue(G.tintAlpha(a) in 0f..1f)
            assertTrue(G.compositeAlpha(a) in 0f..1f)
            assertTrue(G.tintAlpha(b) <= G.tintAlpha(a))
            assertTrue(G.compositeAlpha(b) <= G.compositeAlpha(a))
        }
    }
}
