package com.music.bitchord.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.NOTIFICATION_ART_PX
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.artworkAt
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The single image a widget draws: the album cover, filling it edge to edge,
 * with a flat scrim band at the bottom for the transport to sit on.
 *
 * All of it is baked into one bitmap because a widget cannot shade anything at
 * runtime — [android.widget.RemoteViews] has no RenderEffect, no Haze, no
 * shaders, and no way to reach a view's render node — so the band is drawn
 * here, once per track, on the CPU. Flat by design: no blur is baked anywhere.
 */
internal object MediaWidgetArt {

    /**
     * Draws the widget's artwork at exactly [widthPx] × [heightPx].
     *
     * [bandPx] is the height of the transport strip the layout will lay over the
     * result — the scrim band is sized from it, so the two stay locked together. See
     * `@dimen/widget_band_compact`.
     *
     * [key] identifies the track this is for, and is what the composite is
     * remembered under. Pass null only when there is nothing to remember (no
     * track at all), so the placeholder isn't cached under a shared name.
     */
    suspend fun render(
        context: Context,
        artworkUrl: String?,
        widthPx: Int,
        heightPx: Int,
        bandPx: Int,
        key: String?,
        cornerRadiusPx: Float,
    ): Bitmap {
        peek(key, widthPx, heightPx, bandPx)?.let { return it }
        val cacheKey = key?.let { cacheKey(it, widthPx, heightPx, bandPx) }

        val cover = if (isCoolingOff(key)) null else loadArtwork(context, artworkUrl, maxOf(widthPx, heightPx))
        // A cover that was asked for and didn't arrive is a failure, not an
        // answer. The composite is still drawn — the caller has a widget to
        // fill either way — but it must not be remembered, or one dropped
        // connection would pin the placeholder to this track for as long as the
        // process lives and no amount of reconnecting would shift it. See the
        // `put` at the end.
        val failed = cover == null && !artworkUrl.isNullOrBlank()
        if (failed && key != null) failures[key] = SystemClock.elapsedRealtime()
        val composed = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        if (cover != null) canvas.fillCentreCropped(cover) else canvas.fillPlaceholder()

        // Flat: no baked blur under the transport — a plain scrim band carries
        // it, the way the in-app mini player carries its controls on a slab.
        canvas.scrimBottom(bandPx)

        val rounded = composed.withRoundedCorners(cornerRadiusPx)
        composed.recycle()
        if (!failed) cacheKey?.let { composites.put(it, rounded) }
        return rounded
    }

    /**
     * The composite for these arguments if it has already been drawn, without
     * drawing it if it hasn't.
     *
     * Lets a provider find out, on the thread it was called on, whether it can
     * push a finished widget in one go. Without it every update would have to
     * push the controls first and the artwork second, and the gap between the
     * two shows: a play/pause tap — which changes one glyph and nothing else —
     * would blink the cover away and back again.
     */
    fun peek(key: String?, widthPx: Int, heightPx: Int, bandPx: Int): Bitmap? =
        key?.let { composites[cacheKey(it, widthPx, heightPx, bandPx)] }?.takeIf { !it.isRecycled }

    /**
     * The cover cut to a [sizePx] circle, for the 4×1 widget.
     *
     * Cut here rather than by the host, because clipping a RemoteViews image to
     * its outline only exists from API 31. Null when there is no picture to
     * draw, so the layout's own placeholder disc shows instead. Shares the
     * failure cool-off and the cache with [render], under a key of its own.
     */
    suspend fun circle(context: Context, artworkUrl: String?, sizePx: Int, key: String): Bitmap? {
        peekCircle(key, sizePx)?.let { return it }
        if (isCoolingOff(key)) return null
        val cover = loadArtwork(context, artworkUrl, sizePx)
        if (cover == null) {
            if (!artworkUrl.isNullOrBlank()) failures[key] = SystemClock.elapsedRealtime()
            return null
        }
        val square = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(square).fillCentreCropped(cover)
        val round = square.withRoundedCorners(sizePx / 2f)
        square.recycle()
        composites.put(circleKey(key, sizePx), round)
        return round
    }

    /** [circle]'s result if it has already been drawn. @see peek */
    fun peekCircle(key: String, sizePx: Int): Bitmap? =
        composites[circleKey(key, sizePx)]?.takeIf { !it.isRecycled }

    private fun circleKey(key: String, sizePx: Int) = "circle|$key|$sizePx"

    /**
     * The 4×1 widget's body: the cover filling a [widthPx] × [heightPx] capsule,
     * dimmed just enough for white text, cut to the widget's shape.
     *
     * Drawn from a small working image so the reduction averages the whole
     * cover. Null key or a failed cover gives the placeholder gradient, uncached.
     */
    suspend fun pill(context: Context, artworkUrl: String?, widthPx: Int, heightPx: Int, key: String?): Bitmap {
        key?.let { peekPill(it, widthPx, heightPx) }?.let { return it }
        val cover = if (key == null || isCoolingOff(key)) null else loadArtwork(context, artworkUrl, ROW_ART_PX)
        val failed = cover == null && !artworkUrl.isNullOrBlank()
        if (failed && key != null) failures[key] = SystemClock.elapsedRealtime()

        val composed = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        if (cover == null) {
            canvas.fillPlaceholder()
        } else {
            val workW = PILL_WORKING_WIDTH_PX
            val workH = (workW * heightPx / widthPx.coerceAtLeast(1)).coerceAtLeast(4)
            // Cropped at 4× the working size and halved down, so the reduction
            // averages the whole cover rather than sampling a sparse grid of it.
            val crop = Bitmap.createBitmap(workW * 4, workH * 4, Bitmap.Config.ARGB_8888)
            Canvas(crop).fillCentreCropped(cover)
            val small = crop.halvedTo(workH.toFloat())
            if (small !== crop) crop.recycle()
            val w = small.width
            val h = small.height
            // Flat: the cover draws as it is — no blur pass — under the heavy
            // scrim below, which is what carries the title's legibility now.
            canvas.drawBitmap(
                small,
                Rect(0, 0, w, h),
                Rect(0, 0, widthPx, heightPx),
                Paint().apply { isFilterBitmap = true },
            )
            small.recycle()
        }
        // Heavier than the player's 6–30%: that backdrop carries large type,
        // this one a 16sp title and thin glyphs, over sleeves that can be white.
        canvas.drawRect(
            0f,
            0f,
            widthPx.toFloat(),
            heightPx.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, heightPx.toFloat(),
                    0x66000000.toInt(), 0x8C000000.toInt(), Shader.TileMode.CLAMP,
                )
            },
        )

        // A little of the wallpaper through it too, so the capsule sits on
        // the home screen rather than being pasted over it.
        val rounded = composed.withRoundedCorners(heightPx / 2f, PILL_ALPHA)
        composed.recycle()
        if (!failed && key != null) composites.put(pillKey(key, widthPx, heightPx), rounded)
        return rounded
    }

    /** [pill]'s result if it has already been drawn. @see peek */
    fun peekPill(key: String, widthPx: Int, heightPx: Int): Bitmap? =
        composites[pillKey(key, widthPx, heightPx)]?.takeIf { !it.isRecycled }

    private fun pillKey(key: String, widthPx: Int, heightPx: Int) = "pill|$key|$widthPx|$heightPx"

    /** Drops every remembered composite — the last widget has just been removed. */
    fun clear() {
        composites.evictAll()
        failures.clear()
    }

    /**
     * Whether this cover failed recently enough that it isn't worth asking for
     * again yet.
     *
     * The counterweight to not caching failures. A widget redraws on every play
     * and every pause as well as on every track change, so a cover that is not
     * merely slow but genuinely gone — a dead URL, a source that has moved its
     * images — would otherwise be re-fetched, twice over with the fallback, on
     * every tap of the play button. Long enough that a jammed play/pause finger
     * costs one attempt; short enough that walking back into Wi-Fi fixes the
     * widget on the next thing that happens rather than on the next restart.
     */
    private fun isCoolingOff(key: String?): Boolean {
        val failedAt = key?.let { failures[it] } ?: return false
        val since = SystemClock.elapsedRealtime() - failedAt
        // Also the way back out: a clock that has gone backwards (elapsedRealtime
        // does not, but a stale entry from before a process restart would read
        // that way) must not read as "failed in the future" and cool off forever.
        if (since in 0 until FAILURE_COOLDOWN_MS) return true
        failures.remove(key)
        return false
    }

    private fun cacheKey(key: String, widthPx: Int, heightPx: Int, bandPx: Int) =
        "$key|$widthPx|$heightPx|$bandPx"

    // ---- artwork ----

    private suspend fun loadArtwork(context: Context, url: String?, longestSidePx: Int): Bitmap? {
        if (url.isNullOrBlank()) return null
        val px = artPxFor(longestSidePx)
        // Second choice, and only when the first misses: the size the media
        // session itself asked for. A widget wider than [NOTIFICATION_ART_PX]
        // lands on a rung above it, which is the right size to *draw* but not
        // one this track is guaranteed to already have on disk — so with no
        // network the correct size fails and the widget falls back to the
        // gradient while the notification, the player and the lock screen are
        // all showing the cover from cache. Asked for second rather than
        // first because on a phone it is an upscale, and one worth avoiding
        // whenever the better copy can actually be had.
        val fallbackPx = NOTIFICATION_ART_PX.takeIf { it != px }
        return load(context, url, px) ?: fallbackPx?.let { load(context, url, it) }
    }

    private suspend fun load(context: Context, url: String, px: Int): Bitmap? {
        val request = ImageRequest.Builder(context)
            // Through the app's own size ladder, so this shares a disk-cache
            // entry with the rows, cards, headers and player already drawing the
            // same cover instead of pulling a widget-sized copy of its own over
            // the wire. Local artwork (content://…/albumart/…) carries no size
            // hint and passes through untouched.
            .data(url.artworkAt(px) ?: url)
            .size(px)
            .build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    /**
     * The smallest size in the app's existing artwork ladder that still covers a
     * widget this big.
     *
     * Deliberately not the widget's own pixel width. A size nothing else in the
     * app asks for is a cache entry nothing else in the app fills, so the widget
     * would fetch its own copy of every cover over the network; landing on one of
     * these means the artwork is usually already on disk — and for the playing
     * track, [NOTIFICATION_ART_PX] is the size the media session itself
     * requested, so it is certainly there. A 720px cover in an 860px-wide widget
     * is a 1.2× upscale that no one can see.
     *
     * [PLAYER_ART_PX] is on the ladder for exactly the same reason as the rest of
     * it, and it matters most at the top: a tablet-sized instance is wider than
     * every other rung, so without it the largest widgets were the ones certain
     * to fetch a size of their own. It is what the full player asks for, so a
     * widget that big now shares the player's copy instead.
     */
    private fun artPxFor(longestSidePx: Int): Int = when {
        longestSidePx <= ROW_ART_PX -> ROW_ART_PX
        longestSidePx <= CARD_ART_PX -> CARD_ART_PX
        longestSidePx <= NOTIFICATION_ART_PX -> NOTIFICATION_ART_PX
        longestSidePx <= HEADER_ART_PX -> HEADER_ART_PX
        else -> PLAYER_ART_PX
    }

    /** Fills the canvas with [src], cropped from its centre rather than squashed. */
    private fun Canvas.fillCentreCropped(src: Bitmap) {
        val scale = maxOf(width.toFloat() / src.width, height.toFloat() / src.height)
        val sampleW = (width / scale).coerceAtMost(src.width.toFloat())
        val sampleH = (height / scale).coerceAtMost(src.height.toFloat())
        val left = (src.width - sampleW) / 2f
        val top = (src.height - sampleH) / 2f
        drawBitmap(
            src,
            Rect(
                left.toInt(),
                top.toInt(),
                (left + sampleW).toInt(),
                (top + sampleH).toInt(),
            ),
            Rect(0, 0, width, height),
            Paint().apply { isFilterBitmap = true },
        )
    }

    /**
     * What stands in for a cover there isn't one of: a track with no artwork, a
     * fetch that failed, or nothing ever played.
     *
     * Run through the same scrim as real artwork rather than short-circuited
     * past it — one code path, so it costs
     * nothing to leave it in.
     */
    private fun Canvas.fillPlaceholder() {
        drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0xFF2E3446.toInt(), 0xFF1B2130.toInt(), 0xFF07090E.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
    }


    /**
     * [this] halved until another halving would take it below [target] pixels
     * tall — or [this] itself, if it is already that small.
     *
     * Halving is both the cheap way down and a real low-pass on the way: a
     * bilinear downscale by exactly two averages each 2×2 block. Dropping
     * straight to the target size in one step would still sample only 2×2, so
     * most of the picture would never be looked at and the result would alias —
     * which on a moving queue of covers is visible as the band flickering
     * between tracks that ought to look alike.
     */
    private fun Bitmap.halvedTo(target: Float): Bitmap {
        var current = this
        while (current.height / 2 >= target && current.width / 2 >= 2) {
            val next = Bitmap.createScaledBitmap(
                current,
                current.width / 2,
                current.height / 2,
                true,
            )
            if (current !== this) current.recycle()
            current = next
        }
        return current
    }



    private fun Canvas.scrimBottom(bandPx: Int) {
        val top = (height - bandPx * SCRIM_SCALE).coerceAtLeast(0f)
        drawRect(
            0f,
            top,
            width.toFloat(),
            height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, top, 0f, height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, 0x59000000, 0xD9000000.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
    }

    /**
     * The same bitmap with its corners rounded off.
     *
     * A second bitmap rather than a mask applied in place: clearing the corners
     * of the original means either an un-antialiased `clipPath` or a
     * difference-of-paths draw in CLEAR mode, and both leave a visibly ragged
     * arc. Drawn through a shader instead, the round rect's own antialiasing
     * does the work.
     */
    private fun Bitmap.withRoundedCorners(radiusPx: Float, alpha: Int = 255): Bitmap {
        if (radiusPx <= 0f && alpha == 255) return this
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            radiusPx,
            radiusPx,
            Paint().apply {
                isAntiAlias = true
                this.alpha = alpha
                shader = BitmapShader(this@withRoundedCorners, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            },
        )
        return out
    }

    // ---- tuning ----

    /**
     * How much taller than the transport band the scrim runs. The band's own
     * height would put the start of the ramp exactly on the band's top edge,
     * which is a line; at twice it, the band is already well established by the
     * time it reaches the buttons and still imperceptible where it begins.
     *
     * On a two-cell widget the band is more than half the height, so the region
     * hits the top of the widget and the ramp is compressed. That is the right
     * trade at that size — the alternative is a shorter ramp with a visible start.
     */

    /** [pill]'s opacity: mostly the dimmed cover, with some wallpaper through it. */
    private const val PILL_ALPHA = 210

    /** Width of the working image [pill] scales on. Height follows the capsule. */
    private const val PILL_WORKING_WIDTH_PX = 64

    /**
     * [pill]'s box radius as a fraction of the working width. At a tenth, three
     * passes spread each colour across about a third of the capsule — the sleeve
     * reads as its colours, not as a picture, like the player's backdrop.
     */

    /** How far above the band the scrim starts, in bands. */
    private const val SCRIM_SCALE = 1.2f

    /**
     * Finished composites, by track and size.
     *
     * Worth keeping because most widget updates do not change the picture at
     * all: a play/pause tap swaps one glyph, and re-deriving the artwork for it
     * would mean a cover decode and four scaling passes to draw the identical
     * bitmap again. Sized for a handful of widgets' worth at phone resolutions.
     */
    private val composites = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** When each cover last failed to load. @see isCoolingOff */
    private val failures = ConcurrentHashMap<String, Long>()

    /** @see isCoolingOff */
    private const val FAILURE_COOLDOWN_MS = 30_000L
}
