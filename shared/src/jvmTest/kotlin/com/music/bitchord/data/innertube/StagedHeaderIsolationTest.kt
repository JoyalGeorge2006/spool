package com.music.bitchord.data.innertube

import com.music.bitchord.data.YtMusicRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.test.*

class StagedHeaderIsolationTest {
    private val candidate = Innertube.StagedCredentials(
        "SAPISID=candidate-test-sid", "candidate-page", "candidate-sync", "7",
        "candidate-visitor", "candidate-version",
    )
    private fun localClient(server: MockWebServer) = HttpClient(OkHttp) {
        engine {
            preconfigured = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(request.newBuilder().url(server.url(request.url.encodedPath)).build())
            }.build()
        }
        install(ContentNegotiation) { json() }
        expectSuccess = true
    }
    private fun checkSignature(signature: String?, origin: String) {
        assertNotNull(signature)
        val value = signature.removePrefix("SAPISIDHASH ")
        val timestamp = value.substringBefore('_')
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp candidate-test-sid $origin".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("${timestamp}_$digest", value)
    }
    @Test fun stagedCallsSendOnlyCandidateIdentityAndNeverReplaceActiveIdentity() = runBlocking {
        val old = Innertube.cookie
        val oldTransport = Innertube.stagedTransportForTests
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}")) }
            server.start()
            val transport = localClient(server)
            try {
                Innertube.cookie = "SAPISID=active-test-sid"
                Innertube.adoptSessionScope("active-page", "active-sync", "1", "active-visitor", "active-version", true)
                Innertube.stagedTransportForTests = transport
                Innertube.stagedAccountMenu(candidate)
                Innertube.stagedAccountsList(candidate)
                Innertube.stagedAccountSwitcher(candidate)
                repeat(3) { index ->
                    val request = assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                    assertEquals(candidate.cookie, request.getHeader("Cookie"))
                    assertEquals("7", request.getHeader("X-Goog-AuthUser"))
                    checkSignature(request.getHeader("Authorization"), if (index == 2) "https://www.youtube.com" else "https://music.youtube.com")
                    if (index < 2) {
                        assertEquals("candidate-page", request.getHeader("X-Goog-PageId"))
                        assertEquals("candidate-visitor", request.getHeader("X-Goog-Visitor-Id"))
                        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                        val context = body["context"]!!.jsonObject
                        assertEquals("candidate-sync", context["user"]!!.jsonObject["onBehalfOfUser"]!!.jsonPrimitive.content)
                        assertEquals("candidate-visitor", context["client"]!!.jsonObject["visitorData"]!!.jsonPrimitive.content)
                    }
                }
                assertEquals("SAPISID=active-test-sid", Innertube.cookie)
                assertEquals("active-visitor", Innertube.ensureVisitorData())
            } finally {
                Innertube.stagedTransportForTests = oldTransport
                Innertube.cookie = old
                transport.close()
            }
        }
    }
    @Test fun cancellingChannelLookupDoesNotTryFallbackOrChangeActiveCookie() = runBlocking {
        val old = Innertube.cookie
        val oldTransport = Innertube.stagedTransportForTests
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}").setHeadersDelay(20, TimeUnit.SECONDS))
            server.start()
            val transport = localClient(server)
            try {
                Innertube.cookie = "SAPISID=active-test-sid"
                Innertube.stagedTransportForTests = transport
                val job = launch(Dispatchers.Default) { YtMusicRepository.stagedChannels(candidate) }
                assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
                withTimeout(3_000) { job.cancelAndJoin() }
                assertTrue(job.isCancelled)
                assertEquals(1, server.requestCount)
                assertEquals("SAPISID=active-test-sid", Innertube.cookie)
            } finally {
                Innertube.stagedTransportForTests = oldTransport
                Innertube.cookie = old
                transport.close()
            }
        }
    }
}
