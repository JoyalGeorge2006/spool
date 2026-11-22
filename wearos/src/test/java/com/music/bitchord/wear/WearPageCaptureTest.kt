package com.music.bitchord.wear

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import com.github.takahirom.roborazzi.captureRoboImage
import com.music.bitchord.wear.data.*
import com.music.bitchord.wear.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real watch composables, round 192dp viewport, synthetic offline state. Not a running watch. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w192dp-h192dp-round-mdpi")
class WearPageCaptureTest {
    @get:Rule val compose = createComposeRule()
    private val np = NowPlaying("fixture", "Crimson Sky", "Nova Rae", "Afterglow", false, false,
        72000L, 0L, 218000L, 1f, true, true, false, true, false, 0, emptyList(), null)
    private val queue = Queue(listOf(QueueRow(0,"Crimson Sky","Nova Rae",null),
        QueueRow(1,"Golden Hour Drive","The Midnight",null), QueueRow(2,"Afterglow","Nova Rae",null),
        QueueRow(3,"Quiet City","Evening Lights",null)),0,"fixture")
    private val actions = PlayerActions({}, {}, {}, {}, {}, {}, {}, {})
    private fun <T> state(link: PhoneLink, field: String, value: T) {
        PhoneLink::class.java.getDeclaredField(field).apply { isAccessible = true }
            .let { @Suppress("UNCHECKED_CAST") (it.get(link) as MutableStateFlow<T>).value = value }
    }
    private fun capture(name: String, scroll: Boolean = false, content: @Composable (PhoneLink) -> Unit) {
        // Cancelled scope makes commands inert: no phone account, service or network is contacted.
        val scope = CoroutineScope(Job() + Dispatchers.Main.immediate).also { it.cancel() }
        val link = PhoneLink(ApplicationProvider.getApplicationContext<Context>(), scope)
        state(link,"_nowPlaying",np)
        state(link,"_volume",Volume(6,15))
        state(link,"_heard",true)
        state(link,"_link",Link.Connected)
        state(link,"_outputs",listOf(PhoneOutput(1,"Phone speaker","PHONE",true),
            PhoneOutput(2,"Headphones","BLUETOOTH",false)))
        compose.setContent {
            BitChordWearTheme {
                Box(Modifier.fillMaxSize().background(Color.Black).clip(CircleShape)) {
                    AppScaffold(timeText = { TimeText() }) { content(link) }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/watch_${name}_top.png")
        if (scroll) {
            val nodes=compose.onAllNodes(hasScrollAction())
            if(nodes.fetchSemanticsNodes().isNotEmpty()) {
                repeat(3) { i ->
                    nodes.onFirst().performTouchInput { swipeUp(durationMillis = 900) }
                    compose.waitForIdle()
                    compose.onRoot().captureRoboImage("build/outputs/roborazzi/watch_${name}_scroll_${i}.png")
                }
            }
        }
    }
    @Test fun player() = capture("player") { NowPlayingScreen(np,null,Volume(6,15),actions) }
    @Test fun queue() = capture("queue",true) { QueueScreen(queue,"fixture",{}, {}) }
    @Test fun queue_empty() = capture("queue_empty") { QueueScreen(Queue(emptyList(),0,"fixture"),"fixture",{}, {}) }
    @Test fun queue_loading() = capture("queue_loading") { QueueScreen(null,"fixture",{}, {}) }
    @Test fun more() = capture("more",true) { MoreScreen(np,it,{}) }
    @Test fun output() = capture("output",true) { OutputScreen(it,{}) }
    @Test fun ambient() = capture("ambient") { AmbientPlayer(np,Ambient(active=true)) }
    @Test fun connecting() = capture("connecting") {
        state(it,"_nowPlaying",null as NowPlaying?); state(it,"_heard",false); state(it,"_link",Link.Connecting); WearApp(it)
    }
    @Test fun no_phone() = capture("no_phone") { state(it,"_link",Link.NoPhone); WearApp(it) }
    @Test fun nothing_playing() = capture("nothing_playing") { state(it,"_nowPlaying",null as NowPlaying?); WearApp(it) }
}
