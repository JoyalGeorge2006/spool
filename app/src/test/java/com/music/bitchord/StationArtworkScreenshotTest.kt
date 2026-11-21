@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.music.bitchord

import coil3.SingletonImageLoader
import coil3.request.crossfade
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.data.model.*
import com.music.bitchord.ui.screens.TopPicksShelf
import com.music.bitchord.ui.theme.BitChordTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
abstract class StationArtworkScreenshotTestBase {
    @get:Rule val compose = createComposeRule()
    private fun fixture(): String {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "station-cover-1200.png")
        val bitmap = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); val paint = Paint().apply { isAntiAlias = true }
        canvas.drawColor(android.graphics.Color.MAGENTA) // 24px embedded edge must disappear with crop
        paint.color = android.graphics.Color.rgb(42, 72, 92); canvas.drawRect(24f,24f,1176f,1176f,paint)
        paint.color = android.graphics.Color.rgb(238,191,105); canvas.drawCircle(600f,530f,260f,paint)
        paint.color = android.graphics.Color.rgb(42,72,92); canvas.drawCircle(600f,530f,75f,paint)
        paint.color = android.graphics.Color.WHITE;paint.textSize=72f
        canvas.drawText("TEST COVER",370f,1000f,paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeFile(file.path,bounds)
        assertEquals(1200,bounds.outWidth);assertEquals(1200,bounds.outHeight)
        return file.toURI().toString()
    }
    @OptIn(coil3.annotation.DelicateCoilApi::class)
    protected fun shot(dark: Boolean,width: Int,large: Boolean=false,state: String="loaded") {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = SingletonImageLoader.get(context)
        // Still-pixel fixture: preserve the production decoder/fetcher components,
        // disable only its transition. Callback success does not prove a
        // CrossfadePainter has advanced under Robolectric's separate clocks.
        val stillLoader = original.newBuilder().crossfade(false).build()
        SingletonImageLoader.setUnsafe(stillLoader)
        try { shotWithStillLoader(dark, width, large, state) }
        finally { SingletonImageLoader.setUnsafe(original) }
    }
    private fun shotWithStillLoader(dark: Boolean,width: Int,large: Boolean,state: String) {
        V6ScreenshotTest().installUiHost()
        val uri = when(state) { "loaded" -> fixture(); "error" -> "file:///no-such-station-cover.png";else -> null }
        val picks = listOf(TopPickKind.PERSONAL_STATION,TopPickKind.DISCOVERY_STATION).map { kind ->
            val q=listOf(Song("exact:${kind.name}","Fixture song","Fixture artist",uri))
            TopPick(kind,images=listOfNotNull(uri),watch=WatchTarget("queue:${kind.name}"),preparedSongs=q)
        }
        val taps=mutableListOf<TopPick>()
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(1f,if(large)2f else 1f)) {
            BitChordTheme(darkTheme=dark) {
                Box(Modifier.width(width.dp).height(if(large)440.dp else 340.dp).background(MaterialTheme.colorScheme.background).testTag("station-frame")) {
                    TopPicksShelf(picks,null,{pick,_->taps+=pick},null)
                }
            }
        } }
        if(state=="loaded") {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("station-art-loaded",useUnmergedTree=true).fetchSemanticsNodes().size==2 }
            val capture="build/outputs/roborazzi/station_crop_${width}_${if(large)"2x" else "1x"}_${if(dark)"dark" else "light"}.png"
            val frameCapture=capture.replace("station_crop_","station_crop_source_")
            var frame: Bitmap? = null
            // Same runner rule as PlayerDrawerFrostShotTest.captureAndStore: under the
            // plain unit-test task the roborazzi default task type is None, so a bare
            // captureRoboImage is a no-op and the read-back below fails. Force Record.
            // Keep a bounded layout/draw settle budget. The fixture loader
            // disables transitions, not decoding or any pixel assertion.
            repeat(16) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(64L, java.util.concurrent.TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(64L)
                compose.waitForIdle()
            }
            run {
                val out = File(frameCapture)
                out.parentFile?.mkdirs()
                compose.onNodeWithTag("station-frame").captureRoboImage(out.absolutePath,
                    RoborazziOptions(taskType = RoborazziTaskType.Record))
                frame=android.graphics.BitmapFactory.decodeFile(out.absolutePath)
            }
            val hasArt=frame?.let { f ->
                var count=0
                for(y in 0 until f.height step 4) for(x in 0 until f.width step 4) {
                    val c=f.getPixel(x,y)
                    if(android.graphics.Color.red(c)>200 && android.graphics.Color.green(c)>150 && android.graphics.Color.blue(c)<140) count++
                }
                count>10
            } ?: false
            assertTrue("station artwork never faded in after pumping both clocks past the crossfade", hasArt)
            val rendered=requireNotNull(frame) { "Actual full-frame PNG" }
            val frameBounds=compose.onNodeWithTag("station-frame").fetchSemanticsNode().boundsInRoot
            val artBounds=compose.onAllNodesWithTag("station-art-loaded",useUnmergedTree=true).onFirst().fetchSemanticsNode().boundsInRoot
            val bitmap=Bitmap.createBitmap(rendered,(artBounds.left-frameBounds.left).toInt(),
                (artBounds.top-frameBounds.top).toInt(),artBounds.width.toInt(),artBounds.height.toInt())
            File(capture).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            val edge=bitmap.getPixel(2,bitmap.height/2)
            assertFalse("Embedded magenta edge should be cropped",
                android.graphics.Color.red(edge)>204 && android.graphics.Color.blue(edge)>204 && android.graphics.Color.green(edge)<51)
            assertTrue("Crop contains actual blue artwork, not a blank themed surface",
                kotlin.math.abs(android.graphics.Color.red(edge)-42)<8 &&
                kotlin.math.abs(android.graphics.Color.green(edge)-72)<8 &&
                kotlin.math.abs(android.graphics.Color.blue(edge)-92)<8)
            val gold=bitmap.getPixel(bitmap.width/2,(bitmap.height*0.25f).toInt())
            assertTrue("Known gold record is present in rendered crop",
                android.graphics.Color.red(gold)>200 && android.graphics.Color.green(gold)>150 && android.graphics.Color.blue(gold)<140)
            bitmap.recycle();rendered.recycle()
        } else {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("station-art-fallback",useUnmergedTree=true).fetchSemanticsNodes().size==2 }
        }
        compose.onNodeWithText("Your Station").performClick()
        compose.runOnIdle { assertEquals(picks.first().watch,taps.single().watch);assertEquals(picks.first().preparedSongs,taps.single().preparedSongs) }
        run {
            val out = File("build/outputs/roborazzi/station_${state}_${width}_${if(large)"2x" else "1x"}_${if(dark)"dark" else "light"}.png")
            out.parentFile?.mkdirs()
            compose.onNodeWithTag("station-frame").captureRoboImage(out.absolutePath,
                RoborazziOptions(taskType = RoborazziTaskType.Record))
        }
    }
}

class StationArtworkScreenshotTest_loaded320light : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(false,320) }
class StationArtworkScreenshotTest_loaded320dark : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(true,320) }
class StationArtworkScreenshotTest_loaded390light : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(false,390) }
class StationArtworkScreenshotTest_loaded390dark : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(true,390) }
class StationArtworkScreenshotTest_large320light : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(false,320,true) }
class StationArtworkScreenshotTest_large320dark : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(true,320,true) }
class StationArtworkScreenshotTest_large390light : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(false,390,true) }
class StationArtworkScreenshotTest_large390dark : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(true,390,true) }
class StationArtworkScreenshotTest_missingLight : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(false,390,state="missing") }
class StationArtworkScreenshotTest_missingDark : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(true,390,state="missing") }
class StationArtworkScreenshotTest_errorLight : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(false,390,state="error") }
class StationArtworkScreenshotTest_errorDark : StationArtworkScreenshotTestBase() { @Test fun verify() = shot(true,390,state="error") }
