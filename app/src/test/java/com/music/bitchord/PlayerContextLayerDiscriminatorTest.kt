@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PlayerTheme
import com.music.bitchord.ui.player.PlayerHost
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Artificial fixture-only diagnostic, not a production defect or fix claim.
 * Same complete production player and Related pager, changing ONLY an outer
 * test-owned compositor boundary. Does not switch off internal production
 * layers or claim a cause/fix. Clean isolated tabs already narrowed the defect
 * to player context; this distinguishes parent-layer containment sensitivity. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
@OptIn(coil3.annotation.DelicateCoilApi::class)
class PlayerContextLayerDiscriminatorTest {
    @get:Rule(order=0) val nativeDecode = NativeDecodeMainThreadRule()
    private val loaded = java.util.concurrent.atomic.AtomicBoolean(false)
    @get:Rule(order=1) val observeArtwork = org.junit.rules.TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                val original = coil3.SingletonImageLoader.get(context)
                val observing = original.newBuilder().eventListener(object : coil3.EventListener() {
                    override fun onSuccess(request: coil3.request.ImageRequest, result: coil3.request.SuccessResult) {
                        if(request.data.toString().contains("player-layer-")) {
                            println("PLAYER-COVER-REQUEST data=${request.data} decoded=${result.image.width}x${result.image.height}")
                            // The palette also requests this file at 128px. Only
                            // the production 1200px cover closes this condition.
                            if(result.image.width == 1200 && result.image.height == 1200) loaded.set(true)
                        }
                    }
                }).build()
                loaded.set(false)
                coil3.SingletonImageLoader.setUnsafe(observing)
                try { base.evaluate() } finally {
                    // Inner Compose rule has disposed the composition first.
                    observing.shutdown()
                    coil3.SingletonImageLoader.setUnsafe(original)
                }
            }
        }
    }
    @get:Rule(order=2) val compose = createComposeRule()
    private fun capture(offscreen: Boolean, directSemantics: Boolean = false, exactVisibleTouch: Boolean = false) {
        val fixture = V6ScreenshotTest()
        fixture.installUiHost()
        val restore = fixture.installVideoStubHost()
        val installed = PlayerPlatform.host
        val relatedRequests = java.util.concurrent.atomic.AtomicInteger(0)
        PlayerPlatform.install(object : PlayerHost by installed {
            override suspend fun relatedSongs(song: Song): Result<List<Song>> {
                relatedRequests.incrementAndGet()
                return Result.success(List(8) {
                    Song("layer-fixture-$it", "Related track ${it+1}", "Fixture artist", null)
                })
            }
        })
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val image = java.io.File(context.cacheDir, "player-layer-${offscreen}.png")
        val bitmap = android.graphics.Bitmap.createBitmap(1200,1200,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(42,72,92))
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { color=android.graphics.Color.rgb(238,191,105);isAntiAlias=true }
        canvas.drawCircle(600f,530f,260f,paint)
        paint.color=android.graphics.Color.WHITE;paint.textSize=72f
        canvas.drawText("LAYER ART",380f,1000f,paint)
        image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        val uri = image.toURI().toString()
        val old = AppSettings.playerTheme.value
        AppSettings.playerTheme.value = PlayerTheme.FLAT
        try {
            compose.setContent {
                BitChordTheme(darkTheme=true) {
                    Box(Modifier.width(390.dp).height(780.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .graphicsLayer { compositingStrategy = if(offscreen) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
                        .testTag("player-context-layer-control")) {
                        fixture.PlayerShot(playing=false,titleText="Track 1",alignQueueSeed=true,artworkUrl=uri)
                    }
                }
            }
            // Preserve evidence even if the specific cover request times out.
            val coverFailure = runCatching { compose.waitUntil(10_000) { loaded.get() } }.exceptionOrNull()
            val mode = if(exactVisibleTouch) "offscreen_exact_touch" else if(directSemantics) "offscreen_semantics" else if(offscreen) "offscreen" else "auto"
            val artworkPath = "build/outputs/roborazzi/player_context_layer_${mode}_artwork.png"
            fun record(): Unit {
                compose.onNodeWithTag("player-context-layer-control").captureRoboImage(
                    artworkPath, roborazziOptions=RoborazziOptions(taskType=RoborazziTaskType.Record))
            }
            fun goldPixels(): Int {
                val frame = android.graphics.BitmapFactory.decodeFile(artworkPath)
                    ?: throw AssertionError("Recorded artwork PNG is not decodable: $artworkPath")
                return try {
                    var count=0
                    for(y in 0 until frame.height) for(x in 0 until frame.width) {
                        val pixel=frame.getPixel(x,y)
                        if(android.graphics.Color.red(pixel) in 228..248 &&
                            android.graphics.Color.green(pixel) in 181..201 &&
                            android.graphics.Color.blue(pixel) in 95..115) count++
                    }
                    count
                } finally { frame.recycle() }
            }
            var gold=0
            // The condition is painted pixels, not the loader's success event.
            // Pump both clocks for the production crossfade. Keep the last
            // pre-navigation PNG whether readiness succeeds or fails.
            for(frame in 0 until 20) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(300L,java.util.concurrent.TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(300L)
                compose.waitForIdle()
                record()
                gold=goldPixels()
                if(loaded.get() && gold > 1000) break
            }
            println("PLAYER-COVER-RASTER mode=$mode cover1200=${loaded.get()} gold=$gold coverFailure=$coverFailure")
            println("PLAYER-RELATED-BEFORE mode=$mode\n"+compose.onRoot(useUnmergedTree=true).printToString())
            // Positive area is part of "visible": an offscreen-composited duplicate reports
            // zero bounds and would satisfy the bare in-viewport check, stealing the click.
            val visibleRelated = hasText("Related") and SemanticsMatcher("visible Related tab") {
                val b=it.boundsInRoot
                b.width > 0f && b.height > 0f && b.left>=0f && b.right<=390f && b.top>=0f && b.bottom<=780f
            }
            val matches = compose.onAllNodes(visibleRelated).fetchSemanticsNodes()
            println("PLAYER-RELATED-MATCHES mode=$mode count=${matches.size}")
            matches.forEach { node ->
                println("PLAYER-RELATED-MATCH mode=$mode id=${node.id} bounds=${node.boundsInRoot} config=${node.config}")
            }
            if (exactVisibleTouch) {
                // Fresh composition in a fourth test. Keep the original touch
                // mechanics, changing only ambiguous node selection. The
                // original touch and direct-semantics arms remain unchanged.
                val exactTab = visibleRelated and SemanticsMatcher("positive-size Related tab") {
                    it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f
                } and hasClickAction() and isEnabled() and
                    SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,
                        androidx.compose.ui.semantics.Role.Tab)
                val nodes = compose.onAllNodes(exactTab).fetchSemanticsNodes()
                check(nodes.size == 1) { "Expected one visible enabled Related Tab, got ${nodes.size}" }
                val id = nodes.single().id
                println("PLAYER-RELATED-EXACT-TOUCH mode=$mode id=$id bounds=${nodes.single().boundsInRoot}")
                // Re-resolve by captured identity AND the positive-area visible
                // Tab predicate. Never select a hidden duplicate after layout.
                compose.onNode(exactTab and SemanticsMatcher("exact captured Related Tab") { it.id == id })
                    .assertIsDisplayed().performClick()
            } else if (directSemantics) {
                // Separate diagnostic condition: exact visible, enabled Tab
                // with its own OnClick. Never a fallback that greens the
                // unchanged original touch-path Offscreen control.
                val exactTab = visibleRelated and SemanticsMatcher("positive-size Related tab") {
                    it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f
                } and hasClickAction() and isEnabled() and
                    SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role,
                        androidx.compose.ui.semantics.Role.Tab)
                val nodes = compose.onAllNodes(exactTab).fetchSemanticsNodes()
                check(nodes.size == 1) { "Expected one visible enabled Related Tab, got ${nodes.size}" }
                val id = nodes.single().id
                println("PLAYER-RELATED-EXACT-SEMANTICS mode=$mode id=$id bounds=${nodes.single().boundsInRoot}")
                compose.onNode(SemanticsMatcher("exact captured Related Tab") { it.id == id })
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { click ->
                        check(click()) { "Exact Related semantics action declined" }
                    }
            } else {
                check(matches.isNotEmpty()) { "No visible Related match" }
                val id = matches.first().id
                println("PLAYER-RELATED-ORIGINAL-TOUCH mode=$mode id=$id bounds=${matches.first().boundsInRoot}")
                // This log is pre-click selection evidence, not atomic clicked-ID
                // proof: onFirst is deliberately re-resolved by original code.
                // Preserve original merged-tree selector and touch mechanics.
                compose.onAllNodes(visibleRelated).onFirst().performClick()
            }
            compose.mainClock.advanceTimeBy(1800)
            compose.waitForIdle()
            println("PLAYER-RELATED-AFTER mode=$mode requests=${relatedRequests.get()}\n"+compose.onRoot(useUnmergedTree=true).printToString())
            // Capture the actual post-navigation frame BEFORE any assertion.
            compose.onNodeWithTag("player-context-layer-control").captureRoboImage(
                "build/outputs/roborazzi/player_context_layer_${mode}.png",
                roborazziOptions=RoborazziOptions(taskType=RoborazziTaskType.Record))
            assertTrue("Production 1200px cover must succeed; $coverFailure",loaded.get() && coverFailure == null)
            assertTrue("Fixture gold must be visible in actual recorded pre-navigation pixels ($gold)",gold > 1000)
            assertTrue("Read-only Related provider must be requested",relatedRequests.get() > 0)
            compose.onNodeWithText("Related track 1").assertIsDisplayed()
        } finally {
            image.delete()
            AppSettings.playerTheme.value=old
            PlayerPlatform.install(restore)
        }
    }
    @Test fun unchangedAutoBoundary() = capture(false)
    @Test fun forcedParentOffscreenBoundary() = capture(true)
    @Test fun forcedParentOffscreenExactSemanticsDiagnostic() = capture(true, directSemantics = true)
    @Test fun forcedParentOffscreenExactVisibleTouchDiagnostic() = capture(true, exactVisibleTouch = true)
}
