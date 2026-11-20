package com.music.bitchord

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyMember
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.playback.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Emulator-only integration: real service/controller/ExoPlayer, local generated PCM, no music network. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SpoolServiceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun <T> main(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    private fun await(label: String, condition: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 15000
        while (android.os.SystemClock.elapsedRealtime() < end) {
            if (main(condition)) return
            Thread.sleep(50)
        }
        fail("Timed out: $label")
    }
    private fun controller(): MediaController {
        val latch = CountDownLatch(1)
        var answer: MediaController? = null
        var error: Throwable? = null
        main {
            val future = MediaController.Builder(context, SessionToken(context,
                ComponentName(context, PlaybackService::class.java))).buildAsync()
            future.addListener({
                try { answer = future.get() } catch (t: Throwable) { error = t }
                latch.countDown()
            }, { it.run() })
        }
        assertTrue("Service connection", latch.await(15, TimeUnit.SECONDS))
        error?.let { throw it }
        return requireNotNull(answer)
    }
    private fun localSongs(): List<Song> {
        val file = File(context.cacheDir, "integration-silence.wav")
        val data = ByteArray(16000) // 1 second, mono 8kHz PCM16
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(data.size)
        file.writeBytes(header.array() + data)
        return (1..3).map { Song("local:test-$it", "Fixture $it", "Test", null,
            durationText = "0:01", localUri = Uri.fromFile(file).toString(),
            queueEntryId = "integration-$it") }
    }
    private fun remove(c: MediaController, song: Song, index: Int): SessionResult {
        val args = Bundle().apply {
            putInt(EXTRA_QUEUE_UNDO_INDEX, index)
            putString(EXTRA_QUEUE_UNDO_ENTRY, song.queueEntryId)
            putString(EXTRA_QUEUE_UNDO_MEDIA, song.videoId)
        }
        return main { c.sendCustomCommand(SessionCommand(ACTION_REMOVE_QUEUE_UNDO, Bundle.EMPTY), args) }
            .get(10, TimeUnit.SECONDS)
    }
    @Test fun finiteLocalPlaybackEndsThenRemoveUndoUsesRealService() {
        val c = controller()
        try {
            val songs = localSongs()
            com.music.bitchord.data.settings.AppSettings.setAutoplay(false)
            main { c.setMediaItems(songs.map { it.toMediaItem() }, 2, 0L); c.repeatMode = Player.REPEAT_MODE_OFF; c.prepare(); c.play() }
            await("finite local source reaches ENDED") { c.playbackState == Player.STATE_ENDED }
            assertEquals(3, main { c.mediaItemCount })
            val removed = remove(c, songs[0], 0)
            assertEquals(SessionResult.RESULT_SUCCESS, removed.resultCode)
            await("noncurrent removal applied") { c.mediaItemCount == 2 }
            val token = removed.extras.getString(EXTRA_QUEUE_UNDO_TOKEN)
            assertNotNull(token)
            run {
                val dump = main { (0 until c.mediaItemCount).map {
                    c.getMediaItemAt(it).queueEntryId + "/" + c.getMediaItemAt(it).toSong().queueTier
                }.joinToString() + " current=" + c.currentMediaItemIndex }
                println("UNDO-PRE-RESTORE queue=" + dump + " token=" + token +
                    " extras=" + removed.extras.keySet().joinToString())
            }
            val restored = main { c.sendCustomCommand(SessionCommand(ACTION_RESTORE_QUEUE_UNDO, Bundle.EMPTY),
                Bundle().apply { putString(EXTRA_QUEUE_UNDO_TOKEN, token) }) }.get(10, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, restored.resultCode)
            await("restore applied without duplicate") { c.mediaItemCount == 3 }
            assertEquals(listOf("integration-1", "integration-2", "integration-3"), main {
                (0 until c.mediaItemCount).map { c.getMediaItemAt(it).queueEntryId }
            })
            assertEquals("local:test-3", main { c.currentMediaItem?.mediaId })
            assertEquals(Player.STATE_ENDED, main { c.playbackState })
        } finally { main { c.stop(); c.clearMediaItems(); c.release() } }
    }
    @Suppress("UNCHECKED_CAST")
    @Test fun injectedLockedFollowerIsRejectedByRealServiceThenHostUnlocks() {
        // Test-only state injection isolates service enforcement from network/backend authorization.
        val field = ListenTogether.javaClass.getDeclaredField("_state").apply { isAccessible = true }
        val flow = field.get(ListenTogether) as MutableStateFlow<ListenTogether.State>
        val old = flow.value
        val c = controller()
        try {
            val songs = localSongs()
            main { c.setMediaItems(songs.map { it.toMediaItem() }) }
            await("queue seeded") { c.mediaItemCount == 3 }
            val follower = old.copy(code = "TESTONLY", you = PartyMember(memberId = "fixture", displayName = "Fixture", isHost = false), hostOnlyControl = true)
            main { flow.value = follower }
            assertTrue(flow.value.controlsLocked)
            val refused = remove(c, songs[1], 1)
            assertEquals(androidx.media3.session.SessionError.ERROR_PERMISSION_DENIED, refused.resultCode)
            // A raw count here races PartySync's asynchronous queue rewrite after
            // the injected membership change (run 43834881690: expected:<3> but
            // was:<0>). The integrity the refusal owes is the row itself: once
            // the sync settles, the refused entry is still queued, exactly once.
            await("refused entry still queued exactly once after sync settles") {
                c.mediaItemCount == 3 && (0 until c.mediaItemCount).count {
                    c.getMediaItemAt(it).toSong().videoId == songs[1].videoId
                } == 1
            }
            main { flow.value = follower.copy(you = follower.you!!.copy(isHost = true)) }
            assertFalse(flow.value.controlsLocked)
            // PartySync can replace local entries when membership changes. The command
            // must refer to the live session entry, not a pre-join fixture identity.
            await("host queue available after party synchronization") { c.mediaItemCount == 3 }
            val liveSong = main { c.getMediaItemAt(1).toSong() }
            println("Host live entry: ${liveSong.videoId} / ${liveSong.queueEntryId}")
            assertEquals(SessionResult.RESULT_SUCCESS, remove(c, liveSong, 1).resultCode)
            await("host removal applied") { c.mediaItemCount == 2 }
        } finally { main { flow.value = old; c.stop(); c.clearMediaItems(); c.release() } }
    }
    @Test fun unplayableSingleTrackFailsVisiblyStopsAndRetriesFromIdle() {
        // The end state a bot-blocked or broken source must reach: the error
        // latches on the player (the UI renders PlayerState.error from it), the
        // queue keeps the failed item, recovery stops after its budget instead
        // of spinning, and the play path re-prepares from IDLE and fails the
        // same bounded way again - no silent hang, no retry loop.
        val c = controller()
        val errors = java.util.concurrent.atomic.AtomicInteger()
        main { c.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { errors.incrementAndGet() }
        }) }
        try {
            com.music.bitchord.data.settings.AppSettings.setAutoplay(false)
            val bad = File(context.cacheDir, "unplayable-fixture.m4a")
                .apply { writeBytes(ByteArray(4096) { 0x55 }) }
            val item = MediaItem.Builder()
                .setMediaId("local:unplayable-fixture")
                .setUri(Uri.fromFile(bad))
                .build()
            main { c.stop(); c.playWhenReady = false; c.setMediaItem(item); c.repeatMode = Player.REPEAT_MODE_OFF; c.prepare(); c.play() }
            await("terminal playback error latched on the player") {
                c.playerError != null && c.playbackState == Player.STATE_IDLE
            }
            // The first error precedes the service recovery budget. Require a
            // stable terminal state after the budget, rather than catching the
            // transient IDLE before the service automatically retries.
            Thread.sleep(2500)
            assertEquals(Player.STATE_IDLE, main { c.playbackState })
            assertNotNull(main { c.playerError })
            // Nothing after it in the queue: the item stays put (skipPastUnplayable).
            assertEquals(1, main { c.mediaItemCount })
            val firstError = main { c.playerError?.errorCodeName }
            assertNotNull(firstError)
            val settledErrors = errors.get()
            assertTrue("at least one real error event", settledErrors > 0)
            Thread.sleep(1500)
            assertEquals("automatic retry has stopped", settledErrors, errors.get())
            // What the fixed togglePlayPause runs: prepare() then play() from IDLE.
            main { c.prepare(); c.play() }
            await("retry fails the same bounded way") {
                c.playerError != null && c.playbackState == Player.STATE_IDLE
            }
            Thread.sleep(2500)
            assertEquals(Player.STATE_IDLE, main { c.playbackState })
            assertEquals(firstError, main { c.playerError?.errorCodeName })
            assertEquals(1, main { c.mediaItemCount })
            assertTrue("manual prepare/play produced a fresh failure event", errors.get() > settledErrors)
            val retriedErrors = errors.get()
            Thread.sleep(1500)
            assertEquals("manual retry also terminates", retriedErrors, errors.get())
            // A successful local selection must clear the previous failure.
            val good = localSongs().first()
            main { c.stop(); c.setMediaItem(good.toMediaItem()); c.prepare(); c.play() }
            await("valid local track clears error and plays") {
                c.playerError == null && c.playbackState == Player.STATE_READY
            }
        } finally { main { c.stop(); c.clearMediaItems(); c.release() } }
    }
    @Test fun unplayableLastQueueEntryStopsWithoutDroppingQueue() {
        val c = controller()
        val oldAutoplay = com.music.bitchord.data.settings.AppSettings.autoplay.value
        try {
            com.music.bitchord.data.settings.AppSettings.setAutoplay(false)
            val bad = File(context.cacheDir, "unplayable-last.m4a").apply { writeBytes(ByteArray(4096) { 0x55 }) }
            val last = Song("local:unplayable-last", "Broken final fixture", "Test", null,
                localUri = Uri.fromFile(bad).toString(), queueEntryId = "last-failure")
            val first = localSongs().first()
            val errors = java.util.concurrent.atomic.AtomicInteger()
            main {
                c.addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) { errors.incrementAndGet() }
                })
                c.stop(); c.playWhenReady = false
                c.setMediaItems(listOf(first.toMediaItem(), last.toMediaItem()), 1, 0L)
                c.repeatMode = Player.REPEAT_MODE_OFF; c.prepare(); c.play()
            }
            await("last entry reaches terminal error") { c.playerError != null && c.playbackState == Player.STATE_IDLE }
            Thread.sleep(2500)
            val settled = errors.get()
            assertTrue(settled > 0)
            Thread.sleep(1500)
            assertEquals("last-item recovery budget stays stopped", settled, errors.get())
            assertEquals(Player.STATE_IDLE, main { c.playbackState })
            assertNotNull(main { c.playerError })
            assertEquals(2, main { c.mediaItemCount })
            assertEquals(1, main { c.currentMediaItemIndex })
            assertEquals("last-failure", main { c.currentMediaItem?.queueEntryId })
        } finally {
            main { c.stop(); c.clearMediaItems(); c.release() }
            com.music.bitchord.data.settings.AppSettings.setAutoplay(oldAutoplay)
        }
    }
    @Test fun unreadableLocalLyricsWithoutDurationFinishesUnavailable() {
        val store = androidx.lifecycle.ViewModelStore()
        val oldSynced = com.music.bitchord.data.settings.AppSettings.syncedLyrics.value
        val oldSources = com.music.bitchord.data.settings.AppSettings.lyricsSources.value
        val app = context.applicationContext as android.app.Application
        try {
            com.music.bitchord.data.settings.AppSettings.setSyncedLyrics(true)
            com.music.bitchord.data.settings.AppSettings.setLyricsSources(setOf(com.music.bitchord.data.lyrics.LyricsSource.LRCLIB))
            val bad = File(context.cacheDir, "lyrics-unreadable.m4a").apply { writeBytes(ByteArray(4096) { 0x55 }) }
            val vm = main {
                androidx.lifecycle.ViewModelProvider(store,
                    androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app))[
                    com.music.bitchord.ui.MainViewModel::class.java]
            }
            main { vm.loadLyrics("local:lyrics-failure", "Unreadable fixture", "Test", 0L,
                localUri = Uri.fromFile(bad).toString()) }
            await("local file lyric lookup finishes without waiting for impossible duration") { vm.lyricsChecked.value }
            assertNull(vm.lyrics.value)
            Thread.sleep(1000)
            assertTrue(vm.lyricsChecked.value)
        } finally {
            main { store.clear() }
            com.music.bitchord.data.settings.AppSettings.setSyncedLyrics(oldSynced)
            com.music.bitchord.data.settings.AppSettings.setLyricsSources(oldSources)
        }
    }
    @Test fun repeatedConfiguredQueueEditsKeepServiceOrderAcrossTwelveCycles() {
        repeat(12) { cycle ->
            println("CONFIGURED_QUEUE_STRESS cycle=${cycle + 1}/12")
            autoplayRemovalSurvivesStaleIndexAndQueueGrowth()
        }
    }
    @Test fun repeatedFollowerRefusalKeepsQueueAcrossEightCycles() {
        repeat(8) { cycle ->
            println("FOLLOWER_QUEUE_STRESS cycle=${cycle + 1}/8")
            injectedLockedFollowerIsRejectedByRealServiceThenHostUnlocks()
        }
    }
    @Test fun autoplayRemovalSurvivesStaleIndexAndQueueGrowth() {
        // The reported failure: swiping an autoplay row did nothing. The UI's
        // command carries the index its list showed; autoplay/party edits can
        // advance the live queue past that read. A unique entry id must still
        // name the row exactly - identity wins over the stale index - while a
        // wrong media id stays refused and Undo restores the row live.
        val c = controller()
        try {
            com.music.bitchord.data.settings.AppSettings.setAutoplay(false)
            val base = localSongs()
            val songs = listOf(base[0], base[1], base[2].copy(queueTier = QueueTier.AUTOPLAY))
            // The service player survives between tests: pin transport state so
            // a previous test's play() cannot advance the queue mid-undo.
            main { c.stop(); c.playWhenReady = false; c.setMediaItems(songs.map { it.toMediaItem() }, 0, 0L); c.repeatMode = Player.REPEAT_MODE_OFF; c.prepare() }
            await("queue seeded") { c.mediaItemCount == 3 && !c.isPlaying }
            // Growth the UI has not seen: an insert ahead of the row (its
            // displayed index 2 now holds integration-2) plus a further
            // autoplay append behind it.
            val inserted = Song("local:test-0", "Fixture 0", "Test", null,
                durationText = "0:01", localUri = songs[0].localUri, queueEntryId = "integration-0")
            val extraAutoplay = Song("local:test-4", "Fixture 4", "Test", null,
                durationText = "0:01", localUri = songs[0].localUri, queueEntryId = "integration-4",
                queueTier = QueueTier.AUTOPLAY)
            main { c.addMediaItem(0, inserted.toMediaItem()); c.addMediaItem(extraAutoplay.toMediaItem()) }
            await("queue growth applied") { c.mediaItemCount == 5 }
            // The stale-index command the UI would have sent for the autoplay row.
            val removed = remove(c, songs[2], 2)
            assertEquals(SessionResult.RESULT_SUCCESS, removed.resultCode)
            await("autoplay row removed by identity") { c.mediaItemCount == 4 }
            assertEquals(listOf("integration-0", "integration-1", "integration-2", "integration-4"), main {
                (0 until c.mediaItemCount).map { c.getMediaItemAt(it).queueEntryId }
            })
            // A wrong media id for a stale entry still refuses; nothing moves.
            val refused = remove(c, songs[2].copy(videoId = "local:other"), 1)
            assertEquals(androidx.media3.session.SessionError.ERROR_BAD_VALUE, refused.resultCode)
            assertEquals(4, main { c.mediaItemCount })
            // Undo restores the row beside its live same-tier neighbour.
            val token = removed.extras.getString(EXTRA_QUEUE_UNDO_TOKEN)
            assertNotNull(token)
            val preRestore = main { (0 until c.mediaItemCount).map {
                c.getMediaItemAt(it).queueEntryId + "/" + c.getMediaItemAt(it).toSong().queueTier
            }.joinToString() + " current=" + c.currentMediaItemIndex }
            val recordedAnchors = "before=" + removed.extras.getString(EXTRA_QUEUE_UNDO_BEFORE) +
                " after=" + removed.extras.getString(EXTRA_QUEUE_UNDO_AFTER)
            val restored = main { c.sendCustomCommand(SessionCommand(ACTION_RESTORE_QUEUE_UNDO, Bundle.EMPTY),
                Bundle().apply { putString(EXTRA_QUEUE_UNDO_TOKEN, token) }) }.get(10, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, restored.resultCode)
            await("undo restored the autoplay row") { c.mediaItemCount == 5 }
            assertEquals(
                "preRestore: " + preRestore + " | anchors: " + recordedAnchors +
                    " | rm: " + removed.extras.getString(EXTRA_QUEUE_UNDO_DEBUG) +
                    " | rs: " + restored.extras.getString(EXTRA_QUEUE_UNDO_DEBUG_RESTORE),
                listOf("integration-0", "integration-1", "integration-2", "integration-3", "integration-4"),
                main { (0 until c.mediaItemCount).map { c.getMediaItemAt(it).queueEntryId } },
            )
        } finally { main { c.stop(); c.clearMediaItems(); c.release() } }
    }

    @Test fun localFixtureDrivesRealAndroidPlayerUi() {
        // Ephemeral emulator only. No online song/account/backend dependency.
        com.music.bitchord.data.settings.AppSettings.setOnboardingComplete()
        // Flat is the only player theme; there is no setter left to pin.
        val c = controller()
        try {
            main { c.setMediaItems(localSongs().map { it.toMediaItem() }); c.repeatMode = Player.REPEAT_MODE_ONE; c.prepare(); c.play() }
            await("fixture playing before UI") { c.isPlaying }
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            click("Fixture 1")
            await("expanded player header accessible") { find("Close player") != null && find("More") != null }
            val closeBounds = android.graphics.Rect().also { requireNotNull(find("Close player")).getBoundsInScreen(it) }
            val moreBounds = android.graphics.Rect().also { requireNotNull(find("More")).getBoundsInScreen(it) }
            val headerScreen = instrumentation.uiAutomation.takeScreenshot()!!
            assertTrue("Collapse target needs a left gutter: $closeBounds", closeBounds.left > 0)
            assertTrue("More target needs a right gutter: $moreBounds width=${headerScreen.width}",
                moreBounds.right < headerScreen.width)
            click("Pause")
            await("real player UI pause updates service") { !c.isPlaying }
            click("Play")
            await("real player UI play updates service") { c.isPlaying }
            repeat(6) { if (find("Lyrics") == null) swipeUp() }
            click("Lyrics")
            Thread.sleep(1200)
            val paneLabel = requireNotNull(find("Lyrics"))
            val caption = find("Playing from Opened file") ?: find("Playing from Queue")
            val paneBounds = android.graphics.Rect().also { paneLabel.getBoundsInScreen(it) }
            val stripBounds = android.graphics.Rect().also { requireNotNull(find("Close player")).getBoundsInScreen(it) }
            assertTrue("Lyrics tab must remain below pinned strip: tab=$paneBounds strip=$stripBounds",
                paneBounds.top >= stripBounds.bottom)
            if (caption != null) {
                val captionBounds = android.graphics.Rect().also { caption.getBoundsInScreen(it) }
                assertTrue("Lyrics tab must not overlap origin caption: tab=$paneBounds caption=$captionBounds",
                    paneBounds.top >= captionBounds.bottom)
            }
            val geometry = "Lyrics tab=$paneBounds strip=$stripBounds; ordinary swipe then tab tap"
            println(geometry)
            instrumentation.uiAutomation.takeScreenshot()!!.let { image ->
                val bytes = java.io.ByteArrayOutputStream()
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes)
                keepMedia("04-player-lyrics-tabs.png",bytes.toByteArray(),"image/png")
            }
            keepMedia("04-player-lyrics-geometry.txt",geometry.toByteArray(),"text/plain")
            val previousTheme = com.music.bitchord.data.settings.AppSettings.themeMode.value
            try {
                main { com.music.bitchord.data.settings.AppSettings.setThemeMode(com.music.bitchord.data.settings.ThemeMode.LIGHT) }
                Thread.sleep(700)
                capture("05-player-lyrics-light.png")
            } finally { main { com.music.bitchord.data.settings.AppSettings.setThemeMode(previousTheme) } }
            click("Up next")
            repeat(6) { if (find("Fixture 3") == null) swipeUp() }
            assertNotNull("Last queue row genuinely reachable", find("Fixture 3"))
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.filesDir, "android-player-at-failure.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            val nodes = StringBuilder()
            fun dump(n: android.view.accessibility.AccessibilityNodeInfo?, depth: Int = 0) {
                n ?: return
                nodes.append(" ".repeat(depth)).append(n.text).append(" | ").append(n.contentDescription).append('\n')
                for (i in 0 until n.childCount) dump(n.getChild(i), depth + 1)
            }
            dump(instrumentation.uiAutomation.rootInActiveWindow)
            File(context.filesDir, "android-player-ui.txt").writeText(nodes.toString())
            assertTrue("Queue pane contains last fixture", nodes.contains("Fixture 3"))
            click("Close player")
        } finally { main { c.stop(); c.release() } }
    }

    private fun find(label: String): android.view.accessibility.AccessibilityNodeInfo? {
            fun walk(n: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                n ?: return null
                if (n.text?.toString() == label || n.contentDescription?.toString() == label) return n
                for (i in 0 until n.childCount) walk(n.getChild(i))?.let { return it }
                return null
            }
            return walk(instrumentation.uiAutomation.rootInActiveWindow)
        }
    private fun click(label: String) {
            val end = android.os.SystemClock.elapsedRealtime() + 25000
            while (android.os.SystemClock.elapsedRealtime() < end) {
                var node = find(label)
                if (node != null) {
                    while (node != null && !node.isClickable) node = node.parent
                    if (node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true) return
                }
                Thread.sleep(150)
            }
            val ui = StringBuilder()
            fun dump(n: android.view.accessibility.AccessibilityNodeInfo?, depth: Int = 0) {
                n ?: return
                ui.append(" ".repeat(depth)).append(n.text).append(" | ").append(n.contentDescription)
                    .append(" click=").append(n.isClickable).append('\n')
                for (i in 0 until n.childCount) dump(n.getChild(i), depth + 1)
            }
            dump(instrumentation.uiAutomation.rootInActiveWindow)
            File(context.filesDir, "android-player-ui.txt").writeText(
                "fixtureTheme=" + com.music.bitchord.data.settings.AppSettings.playerTheme.value + "\n" + ui.toString())
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.filesDir, "android-player-at-failure.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            fail("UI target not clickable: $label")
        }
    private fun swipeUp() {
                val ui = instrumentation.uiAutomation
                val now = android.os.SystemClock.uptimeMillis()
                val screen = ui.takeScreenshot()!!
                val x = screen.width * 0.5f
                val y1 = screen.height * 0.72f
                val y2 = screen.height * 0.30f
                val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y1, 0)
                down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                ui.injectInputEvent(down, true); down.recycle()
                for (i in 1..12) {
                    val move = android.view.MotionEvent.obtain(now, now + i * 20L, android.view.MotionEvent.ACTION_MOVE,
                        x, y1 + (y2 - y1) * i / 12f, 0)
                    move.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    ui.injectInputEvent(move, true); move.recycle()
                }
                val up = android.view.MotionEvent.obtain(now, now + 260, android.view.MotionEvent.ACTION_UP, x, y2, 0)
                up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                ui.injectInputEvent(up, true); up.recycle()
                Thread.sleep(700)
            }
    private fun shell(command: String): ByteArray =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }

    private fun keepMedia(name: String, bytes: ByteArray, mime: String) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/SpoolSurfaceAudit")
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.query(uri,arrayOf(android.provider.MediaStore.MediaColumns.DATA),null,null,null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(0)
                shell("mkdir -p /data/local/tmp/spool-audit")
                val result = shell("cp $path /data/local/tmp/spool-audit/$name").decodeToString()
                println("AUDIT_EXPORT $name path=$path result=$result")
            }
        }
    }
    private fun capture(name: String) {
                shell("mkdir -p /data/local/tmp/spool-audit")
                val output = shell("screencap -p /data/local/tmp/spool-audit/$name").decodeToString()
                println("SHELL_SCREENSHOT $name $output")
                val image = instrumentation.uiAutomation.takeScreenshot()!!
                val bytes = java.io.ByteArrayOutputStream()
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes)
                keepMedia(name,bytes.toByteArray(),"image/png")
            }
    private fun allNodes(): List<android.view.accessibility.AccessibilityNodeInfo> {
                val answer = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                fun walk(n: android.view.accessibility.AccessibilityNodeInfo?) {
                    n ?: return
                    answer.add(n)
                    for (i in 0 until n.childCount) walk(n.getChild(i))
                }
                walk(instrumentation.uiAutomation.rootInActiveWindow)
                return answer
            }
    private fun tab(label: String) {
                await("bottom tab visible: $label") { allNodes().any {
                    val r=android.graphics.Rect(); it.getBoundsInScreen(r)
                    (it.text?.toString()==label || it.contentDescription?.toString()==label) && r.top>500
                } }
                val node = allNodes().filter { it.text?.toString() == label || it.contentDescription?.toString() == label }.maxByOrNull {
                    val r = android.graphics.Rect(); it.getBoundsInScreen(r); r.top
                } ?: error("No tab $label")
                var target: android.view.accessibility.AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) target = target.parent
                assertTrue("Tab $label clickable", target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true)
                Thread.sleep(400)
            }
    private fun launchAuditActivity() {
        com.music.bitchord.data.settings.AppSettings.setOnboardingComplete()
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    private fun dumpUi(name: String) {
        val text=allNodes().joinToString("\n") {
            val r = android.graphics.Rect(); it.getBoundsInScreen(r)
            "${it.text} | ${it.contentDescription} | ${it.className} | bounds=$r click=${it.isClickable} visible=${it.isVisibleToUser} actions=${it.actionList}"
        }
        println("AUDIT_UI $name\n$text")
        keepMedia(name,text.toByteArray(),"text/plain")
    }
    @Test fun searchRetainsQueryAcrossNativeTabCycles() {
        launchAuditActivity()
            tab("Search")
            await("search field available") { allNodes().any { it.className?.toString() == "android.widget.EditText" } }
            capture("06-search-before-entry.png")
            dumpUi("06-search-before-entry.txt")
            val input = allNodes().first { it.className?.toString() == "android.widget.EditText" }
            input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
            assertTrue(input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"spoolretain9")
            }))
            Thread.sleep(500)
            capture("06-search-after-entry.png")
            dumpUi("06-search-after-entry.txt")
            await("Query entered before leaving Search") { find("spoolretain9") != null }
            capture("06-query-before-back.png")
            dumpUi("06-query-before-back.txt")
            assertNotNull("Query entered before leaving Search", find("spoolretain9"))
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            Thread.sleep(400)
            instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo ${context.packageName} reset").close()
            repeat(3) { tab("Listen"); tab("Explore"); tab("Library"); tab("Search") }
            val frameBytes = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo ${context.packageName} framestats")
            ).use { it.readBytes() }
            keepMedia("06-native-tab-framestats.txt",frameBytes,"text/plain")
            capture("06-query-after-tabs.png")
            dumpUi("06-query-after-tabs.txt")
            // A retained, unfocused BasicTextField can be absent from the platform
            // accessibility snapshot even while its text is visibly painted. Focus
            // the existing field without changing its value, then assert the exact
            // original query. Keep both snapshots to discriminate stale semantics.
            if (find("spoolretain9") == null) {
                val now = android.os.SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val event = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), action, 130f, 50f, 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                    event.recycle()
                }
                await("Retained query readable after focus") { find("spoolretain9") != null }
                capture("06-query-after-refocus.png"); dumpUi("06-query-after-refocus.txt")
            }
            assertNotNull("Search query retained after three real tab cycles", find("spoolretain9"))
            capture("06-search-query-retained.png")

    }
    @Test fun tabTraversalWarmProfile() {
        launchAuditActivity()
        tab("Listen")
        repeat(2) { tab("Explore"); tab("Library"); tab("Search"); tab("Listen") }
        // Resolve actual tab bounds before profiling. Tree traversal triggers
        // accessibility prefetch on the UI thread and contaminated the v44 sample.
        val tabPoints = listOf("Explore", "Library", "Search", "Listen").associateWith { label ->
            val n = allNodes().filter { it.text?.toString() == label || it.contentDescription?.toString() == label }
                .maxByOrNull { val r=android.graphics.Rect(); it.getBoundsInScreen(r); r.top } ?: error("Missing tab $label")
            val r=android.graphics.Rect(); n.getBoundsInScreen(r)
            assertTrue(r.top > 500 && r.bottom <= 640)
            r.centerX().toFloat() to r.centerY().toFloat()
        }
        fun tapTabs() { for ((x,y) in tabPoints.values) {
            val now = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,x,y,0)
                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)); event.recycle()
            }
            Thread.sleep(400)
        } }
        shell("dumpsys gfxinfo ${context.packageName} reset")
        repeat(3) { tapTabs() }
        keepMedia("09-warm-tab-framestats.txt", shell("dumpsys gfxinfo ${context.packageName} framestats"), "text/plain")
        // A separate sampled trace has profiler overhead and is not the frame sample.
        val trace = File(context.cacheDir, "spool-tabs.trace")
        android.os.Debug.startMethodTracingSampling(trace.absolutePath, 16 * 1024 * 1024, 1000)
        try { repeat(2) { tapTabs() } }
        finally { android.os.Debug.stopMethodTracing() }
        if (trace.exists()) keepMedia("09-tabs-sampled.trace", trace.readBytes(), "application/octet-stream")
        capture("09-warm-tabs-complete.png")
    }
    private fun clickVisibleRow(label: String, evidence: String) {
        capture("$evidence-before.png"); dumpUi("$evidence-before.txt")
        val n = find(label) ?: error("Missing visible row $label")
        val r = android.graphics.Rect(); n.getBoundsInScreen(r)
        assertTrue("$label has visible unobscured bounds $r", r.top > 75 && r.bottom < 480 && r.width() > 0)
        var target: android.view.accessibility.AccessibilityNodeInfo? = n
        while (target != null && !target.isClickable) target = target.parent
        val accessible = target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true
        println("AUDIT_ROW $label accessibility=$accessible bounds=$r")
        if (!accessible) {
            val now = android.os.SystemClock.uptimeMillis()
            val x = r.centerX().toFloat(); val y = r.centerY().toFloat()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                assertTrue("Visible touch injected for $label", instrumentation.uiAutomation.injectInputEvent(event, true))
                event.recycle()
                Thread.sleep(60)
            }
        }
        Thread.sleep(900)
        capture("$evidence-after.png"); dumpUi("$evidence-after.txt")
    }
    @Test fun playbackSettingsBothThemesNativeAudit() {
        launchAuditActivity()
            tab("Listen")
            val avatar = requireNotNull(find("Switch account"))
            val avatarBounds = android.graphics.Rect(); avatar.getBoundsInScreen(avatarBounds)
            assertTrue("Avatar target is on screen", avatarBounds.top > 20 && avatarBounds.bottom < 180)
            click("Switch account")
            Thread.sleep(1200)
            capture("07-after-accessibility-settings-entry.png")
            dumpUi("07-after-accessibility-settings-entry.txt")
            if (find("Settings") == null && android.os.Build.VERSION.SDK_INT >= 34) {
                val cleared = instrumentation.uiAutomation.clearCache()
                println("SETTINGS_CACHE_FRESHNESS cleared=$cleared")
                dumpUi("07-settings-after-cache-refresh.txt")
                capture("07-settings-after-cache-refresh.png")
            }
            if (find("Settings") == null) {
                // Keep the failed accessibility-route evidence; an ACTION_CLICK
                // return value is not proof that the target screen opened.
                val now = android.os.SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val event = android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,
                        avatarBounds.centerX().toFloat(),avatarBounds.centerY().toFloat(),0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)); event.recycle()
                }
            }
            await("Settings page opened") { find("Settings") != null }
            capture("07-after-settings-entry.png")
            dumpUi("07-after-settings-entry.txt")
            await("Settings content and scrolling available") { find("Settings") != null && allNodes().any { it.isScrollable } }
            fun scrollTo(label: String) {
                // Whole-page accessibility scrolls can overshoot while Compose's
                // nodes still describe the previous frame. Small physical drags
                // and two stable readings keep the actual row above the dock.
                repeat(24) { step ->
                    Thread.sleep(300)
                    val n = find(label)
                    val r = android.graphics.Rect(); n?.getBoundsInScreen(r)
                    if (n != null && r.top > 85 && r.bottom < 460) {
                        Thread.sleep(400)
                        val stable = android.graphics.Rect(); find(label)?.getBoundsInScreen(stable)
                        if (stable == r) return
                        // Candidate already visible: let the last physical scroll
                        // settle instead of adding another swipe and overshooting.
                        Thread.sleep(700)
                        capture("07-settings-candidate-settling-$step.png")
                        dumpUi("07-settings-candidate-settling-$step.txt")
                        return@repeat
                    }
                    capture("07-settings-scroll-step-$step.png")
                    dumpUi("07-settings-scroll-step-$step.txt")
                    val backwards = n != null && r.top <= 85
                    val now = android.os.SystemClock.uptimeMillis()
                    val start = if (backwards) 190f else 350f
                    val end = if (backwards) 350f else 190f
                    for (i in 0..12) {
                        val action = if (i == 0) android.view.MotionEvent.ACTION_DOWN else if (i == 12) android.view.MotionEvent.ACTION_UP else android.view.MotionEvent.ACTION_MOVE
                        val y = start + (end-start)*i/12f
                        val event = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), action, 220f, y, 0)
                        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)); event.recycle()
                        Thread.sleep(30)
                    }
                    Thread.sleep(500)
                }
                error("Settings row not reached: $label")
            }
            scrollTo("Playback")
            clickVisibleRow("Playback", "07-playback-open")
            await("Playback page opened") { find("Output precision") != null }
            Thread.sleep(400)
            val oldTheme = com.music.bitchord.data.settings.AppSettings.themeMode.value
            try {
                for (theme in listOf(com.music.bitchord.data.settings.ThemeMode.DARK,com.music.bitchord.data.settings.ThemeMode.LIGHT)) {
                    main { com.music.bitchord.data.settings.AppSettings.setThemeMode(theme) }
                    repeat(8) { allNodes().firstOrNull { it.isScrollable }?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); Thread.sleep(80) }
                    Thread.sleep(400)
                    capture("07-settings-playback-"+theme.name.lowercase()+".png")
                    swipeUp()
                    capture("08-settings-playback-scrolled-"+theme.name.lowercase()+".png")
                    scrollTo("Equalizer")
                    capture("08-settings-playback-final-row-"+theme.name.lowercase()+".png")
                    dumpUi("08-settings-playback-final-row-"+theme.name.lowercase()+".txt")
                }
            } finally { main { com.music.bitchord.data.settings.AppSettings.setThemeMode(oldTheme) } }

    }

}
