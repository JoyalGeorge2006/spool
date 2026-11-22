package com.music.bitchord.ui.components

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.music.bitchord.StatusBarStrip
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * One source owner per window. Its source contains visible content and inline
 * overlays, never the status effect. Page/menu blur uses separate states.
 * A moving dialog sheet mounts its effect at the decor-window top, outside
 * the sheet's transform, clip and hit-test bounds. Its source may still move.
 */
@Composable
internal fun WindowStatusFrost(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    windowAnchored: Boolean = false,
    window: Window? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val statusSource = remember { HazeState() }
    Box(modifier) {
        Box(
            Modifier.fillMaxSize()
                .then(if (enabled) Modifier.hazeSource(statusSource) else Modifier),
            content = content,
        )
        if (enabled && !windowAnchored) StatusBarStrip(hazeState = statusSource)
    }
    if (enabled && windowAnchored && window != null) {
        FixedWindowStatusFrost(window, statusSource)
    }
}

/** A drawing/touch-only sibling of the dialog content, never a focus target. */
@Composable
private fun FixedWindowStatusFrost(window: Window, source: HazeState) {
    val host = LocalView.current
    val parentComposition = rememberCompositionContext()
    DisposableEffect(window, source, host, parentComposition) {
        val decor = window.decorView as ViewGroup
        val overlay = ComposeView(host.context).apply {
            setParentCompositionContext(parentComposition)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(host.findViewTreeLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(host.findViewTreeSavedStateRegistryOwner())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = false
            isFocusableInTouchMode = false
            setContent { StatusBarStrip(hazeState = source) }
        }
        decor.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ))
        onDispose { releaseWindowStatusOverlay(decor, overlay) }
    }
}

/**
 * Dialog composition disposal may run while Android is walking decor children
 * in dispatchDetachedFromWindow. Never change that child array in the callback.
 * Hide this owned view now, then finish removal on the next main-queue turn.
 */
internal fun releaseWindowStatusOverlay(decor: ViewGroup, overlay: ComposeView) {
    overlay.visibility = View.GONE
    overlay.isEnabled = false
    Handler(Looper.getMainLooper()).post {
        if (overlay.parent === decor) decor.removeView(overlay)
        overlay.disposeComposition()
    }
}
