package com.music.bitchord.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.music.bitchord.data.settings.MixBlend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Static status is independent of tempo, phase, playback and translation toggles. */
class FlatMotionPolicyTest {
    @Test fun automixOpacityDoesNotFollowBeatOrPlayback() {
        val blend = mutableStateOf<MixBlend?>(null)
        val status = MixPulse { blend.value }
        assertFalse(status.active)
        assertEquals(0f, status.cover)
        for (beat in listOf(0f, 250f, 600f, 1800f)) {
            for (playing in listOf(false, true)) {
                blend.value = MixBlend(beat, 123456789L, playing)
                assertTrue(status.active)
                assertEquals(1f, status.cover)
                assertEquals(0.8f, status.alpha(0.8f))
            }
        }
        blend.value = null
        assertEquals(0f, status.cover)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun translationChangesNeverProvideDecorativeParticleProgress() = runComposeUiTest {
        val trigger = mutableStateOf(0)
        var calls = 0
        setContent {
            LyricsTranslationMotion(trigger = trigger.value, reduceMotion = false) { progress ->
                assertNull(progress)
                calls++
            }
        }
        waitForIdle()
        runOnIdle { trigger.value++ }
        waitForIdle()
        runOnIdle { assertTrue(calls >= 2) }
    }
}
