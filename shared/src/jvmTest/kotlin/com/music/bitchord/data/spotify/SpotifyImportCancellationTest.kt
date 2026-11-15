package com.music.bitchord.data.spotify

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.EventListener
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpotifyImportCancellationTest {
    @Test
    fun normalBodyIsReturnedIntact() = runBlocking {
        MockWebServer().use { server ->
            val text = "{\"name\":\"Playlist\",\"tracks\":[1,2,3]}"
            server.enqueue(MockResponse().setBody(text))
            server.start()
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build())
            val response = withTimeout(5_000) { SpotifyImporter.run { call.executeCancellable() } }
            assertEquals(200, response.code)
            assertTrue(response.successful)
            assertEquals(text, response.body)
        }
    }

    @Test
    fun cancellationBeforeHeadersCancelsCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("body").setHeadersDelay(10, TimeUnit.SECONDS))
            server.start()
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build())
            val task = launch(start = CoroutineStart.UNDISPATCHED) {
                SpotifyImporter.run { call.executeCancellable() }
            }
            assertTrue(server.takeRequest(3, TimeUnit.SECONDS) != null)
            withTimeout(2_000) { task.cancelAndJoin() }
            assertTrue(call.isCanceled())
        }
    }

    @Test
    fun cancellationDuringTricklingBodyCancelsCallAndClosesResponse() = runBlocking {
        val bodyStarted = CountDownLatch(1)
        val callEnded = CountDownLatch(1)
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun responseBodyStart(call: Call) { bodyStarted.countDown() }
            override fun callFailed(call: Call, ioe: java.io.IOException) { callEnded.countDown() }
            override fun callEnd(call: Call) { callEnded.countDown() }
        }).build()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x".repeat(4096)).throttleBody(1, 200, TimeUnit.MILLISECONDS))
            server.start()
            val call = client.newCall(Request.Builder().url(server.url("/")).build())
            val task = launch(start = CoroutineStart.UNDISPATCHED) {
                SpotifyImporter.run { call.executeCancellable() }
            }
            assertTrue(bodyStarted.await(3, TimeUnit.SECONDS), "response body must have started")
            withTimeout(2_000) { task.cancelAndJoin() }
            assertTrue(call.isCanceled())
            assertTrue(callEnded.await(3, TimeUnit.SECONDS), "body reader must release the connection")
        }
    }
}
