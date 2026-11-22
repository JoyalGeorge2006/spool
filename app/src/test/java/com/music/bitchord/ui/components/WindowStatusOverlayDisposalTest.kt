package com.music.bitchord.ui.components

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** The disposal callback must not change a decor child array being traversed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class WindowStatusOverlayDisposalTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun disposalHidesNowButDefersChildArrayMutation() {
        val decor = FrameLayout(context)
        val before = View(context)
        val overlay = ComposeView(context)
        val after = View(context)
        decor.addView(before); decor.addView(overlay); decor.addView(after)
        releaseWindowStatusOverlay(decor, overlay)
        assertEquals(View.GONE, overlay.visibility)
        assertFalse(overlay.isEnabled)
        assertEquals("Disposal must leave an in-progress child traversal intact", 3, decor.childCount)
        assertSame(before, decor.getChildAt(0))
        assertSame(overlay, decor.getChildAt(1))
        assertSame(after, decor.getChildAt(2))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, decor.childCount)
        assertNull(overlay.parent)
        assertSame(before, decor.getChildAt(0))
        assertSame(after, decor.getChildAt(1))
    }

    @Test fun queuedCleanupDoesNotRemoveAReplacementOverlay() {
        val decor = FrameLayout(context)
        val old = ComposeView(context)
        val replacement = ComposeView(context)
        decor.addView(old)
        releaseWindowStatusOverlay(decor, old)
        decor.addView(replacement)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, decor.childCount)
        assertSame(replacement, decor.getChildAt(0))
        assertEquals(View.VISIBLE, replacement.visibility)
        assertTrue(replacement.isEnabled)
    }

    @Test fun queuedCleanupToleratesAnAlreadyRemovedOwnedView() {
        val decor = FrameLayout(context)
        val overlay = ComposeView(context)
        val other = View(context)
        decor.addView(overlay); decor.addView(other)
        releaseWindowStatusOverlay(decor, overlay)
        decor.removeView(overlay)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, decor.childCount)
        assertSame(other, decor.getChildAt(0))
        assertNull(overlay.parent)
    }
}
