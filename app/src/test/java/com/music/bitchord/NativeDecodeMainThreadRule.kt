package com.music.bitchord

import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Diagnostic isolation for host native ImageDecoder's JNIEnv thread affinity.
 * Keeps the app's real Coil components, fetchers, decoders, cache and crossfade.
 * Only decode dispatch changes to the same main looper that initializes native
 * graphics. Not a claim about Android production decoder threading or a proven
 * upstream fix. Must wrap the Compose rule so cleanup runs after composition
 * disposal. Never use on installed-device/instrumentation tests. */
@OptIn(DelicateCoilApi::class)
class NativeDecodeMainThreadRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val original = SingletonImageLoader.get(context)
            val diagnostic = original.newBuilder()
                .decoderCoroutineContext(Dispatchers.Main.immediate)
                .build()
            SingletonImageLoader.setUnsafe(diagnostic)
            println("NATIVE-DECODE-ISOLATION real-loader decoder=Main.immediate ${description.className}")
            try {
                base.evaluate()
            } finally {
                // Composition has been disposed by the inner Compose rule.
                // Coil 3.0.4 shutdown cancels its own scope/callbacks and clears
                // shared memory entries; it does not shut down the disk cache.
                diagnostic.shutdown()
                SingletonImageLoader.setUnsafe(original)
            }
        }
    }
}
