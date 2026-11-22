package com.music.bitchord

import com.music.bitchord.data.settings.AppSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformancePreferenceIsolationTest {
    @Test fun `high refresh leaves reduced motion and blur unchanged`() {
        val oldMotion = AppSettings.reduceAnimation.value
        val oldBlur = AppSettings.reduceDynamicBlur.value
        val oldHigh = AppSettings.highPerformanceMode.value
        try {
            AppSettings.setReduceAnimation(true)
            AppSettings.reduceDynamicBlur.value = true
            AppSettings.setHighPerformanceMode(true)
            assertTrue(AppSettings.reduceAnimation.value)
            assertTrue(AppSettings.reduceDynamicBlur.value)
            AppSettings.reload()
            assertTrue(AppSettings.reduceAnimation.value)
            assertTrue(AppSettings.highPerformanceMode.value)
            assertTrue(AppSettings.reduceDynamicBlur.value)
            AppSettings.setHighPerformanceMode(false)
            assertTrue(AppSettings.reduceAnimation.value)
            assertTrue(AppSettings.reduceDynamicBlur.value)
        } finally {
            AppSettings.setHighPerformanceMode(oldHigh)
            AppSettings.setReduceAnimation(oldMotion)
            AppSettings.reduceDynamicBlur.value = oldBlur
        }
    }
    @Test fun `reduced motion leaves high refresh enabled`() {
        val oldMotion = AppSettings.reduceAnimation.value
        val oldHigh = AppSettings.highPerformanceMode.value
        try {
            AppSettings.setHighPerformanceMode(true)
            AppSettings.setReduceAnimation(true)
            assertTrue(AppSettings.highPerformanceMode.value)
            assertTrue(AppSettings.reduceAnimation.value)
        } finally {
            AppSettings.setHighPerformanceMode(oldHigh)
            AppSettings.setReduceAnimation(oldMotion)
        }
    }
}
