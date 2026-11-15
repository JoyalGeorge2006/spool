package com.music.bitchord

import com.music.bitchord.ui.SpoolMotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SpoolMotionPolicyTest {
    @Test fun reducedMotionFinishesOnNextFrame() {
        assertEquals(0f, SpoolMotionPolicy.effectiveScale(true, 2f), 0f)
    }
    @Test fun preservesSystemDisabledAnimations() {
        assertEquals(0f, SpoolMotionPolicy.effectiveScale(false, 0f), 0f)
    }
    @Test fun preservesSystemSlowAnimations() {
        assertEquals(2f, SpoolMotionPolicy.effectiveScale(false, 2f), 0f)
    }
    @Test fun invalidSystemValueFallsBackToNormal() {
        assertEquals(1f, SpoolMotionPolicy.effectiveScale(false, Float.NaN), 0f)
    }
}
