package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.TopPickMood
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

/** Production queue -> repository -> Innertube transport -> real parsers -> Taste.
 * Only HTTP responses are fixtures. Does not claim live provider availability,
 * MainActivity callback dispatch or actual decoder/playback execution. */
class MoodQueueProviderIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun text(value: String) = buildJsonObject {
        putJsonArray("runs") { add(buildJsonObject { put("text", value) }) }
    }
    private fun shelf(title: String, prefix: String) = buildJsonObject {
        putJsonObject("musicCarouselShelfRenderer") {
            putJsonObject("header") {
                putJsonObject("musicCarouselShelfBasicHeaderRenderer") { put("title", text(title)) }
            }
            putJsonArray("contents") {
                repeat(3) { index -> add(buildJsonObject {
                    putJsonObject("musicTwoRowItemRenderer") {
                        put("title", text("Playlist $index"))
                        putJsonObject("navigationEndpoint") {
                            putJsonObject("browseEndpoint") { put("browseId", "VL${prefix}$index") }
                        }
                    }
                }) }
            }
        }
    }
    private fun browse() = buildJsonObject {
        putJsonObject("contents") {
            putJsonObject("singleColumnBrowseResultsRenderer") {
                putJsonArray("tabs") { add(buildJsonObject {
                    putJsonObject("tabRenderer") { putJsonObject("content") {
                        putJsonObject("sectionListRenderer") { putJsonArray("contents") {
                            add(shelf("Malayalam", "own-"))
                            add(shelf("Unrelated scene", "foreign-"))
                        } }
                    } }
                }) }
            }
        }
    }
    private fun watch() = buildJsonObject {
        putJsonArray("contents") {
            repeat(24) { index -> add(buildJsonObject {
                putJsonObject("playlistPanelVideoRenderer") {
                    put("videoId", "f${index+1}")
                    put("title", text("Track ${index+1}"))
                    put("longBylineText", text(if(index < 8) "Familiar Artist" else "Fresh Artist $index"))
                    put("lengthText", text("3:20"))
                }
            }) }
        }
    }
    private fun requestBody(content: OutgoingContent): JsonObject {
        val bytes = when(content) {
            is OutgoingContent.ByteArrayContent -> content.bytes()
            else -> error("Unexpected request body ${content::class}")
        }
        return json.parseToJsonElement(bytes.decodeToString()).jsonObject
    }
    private val profile = Taste.Snapshot(
        artists=listOf("familiar artist"), languages=listOf("malayalam"),
        dislikedIds=listOf("f24"), skippedIds=mapOf("f23" to 3),
        topSongs=listOf(Taste.TasteSong("own", "Own favorite", "Familiar Artist"),
            Taste.TasteSong("foreign", "Wrong scene", "Unrelated Artist")),
    )

    @Test fun allTwentyEightMoodsUseProductionProviderParsingAndPersonalization() = runBlocking<Unit> {
        val oldTransport = Innertube.activeTransportForTests
        val tasteField=TopPicks::class.java.getDeclaredField("taste").apply { isAccessible=true }
        val oldTaste=tasteField.get(TopPicks) as Taste.Snapshot?
        // Cache ownership stays local to this test and is restored afterwards.
        val field=YtMusicRepository::class.java.getDeclaredField("moodGenreShelfCache").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST")
        val cache=field.get(YtMusicRepository) as MutableMap<String,List<com.music.bitchord.data.model.HomeShelf>>
        val savedCache=cache.toMap()
        val browseParams=Collections.synchronizedList(mutableListOf<String>())
        val playlistIds=Collections.synchronizedList(mutableListOf<String>())
        val client=HttpClient(MockEngine { request ->
            val body=requestBody(request.body)
            val response=when(request.url.encodedPath.substringAfterLast('/')) {
                "browse" -> {
                    assertEquals(TopPickMood.CATEGORY_BROWSE_ID,body["browseId"]?.jsonPrimitive?.content)
                    browseParams += body["params"]!!.jsonPrimitive.content
                    browse()
                }
                "next" -> {
                    val id=body["playlistId"]!!.jsonPrimitive.content
                    assertTrue("Scene scoring must exclude unrelated playlists: $id",id.startsWith("own-"))
                    assertTrue(body["isAudioOnly"]!!.jsonPrimitive.boolean)
                    playlistIds += id
                    watch()
                }
                else -> error("Unexpected network operation ${request.url.encodedPath}")
            }
            respond(response.toString(),headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }) { install(ContentNegotiation) { json(this@MoodQueueProviderIntegrationTest.json) } }
        try {
            Innertube.activeTransportForTests=client
            TopPicks.updateTaste(profile)
            for(mood in TopPickMood.entries) {
                cache.clear()
                val beforeBrowse=browseParams.size
                val beforePlaylist=playlistIds.size
                val pick=TopPicks.moodStation(mood,listOf("malayalam"))
                val songs=TopPicks.queue(pick).getOrThrow()
                assertEquals("$mood must reach its own provider category",beforeBrowse+1,browseParams.size)
                assertEquals(mood.params,browseParams.last())
                assertEquals("$mood fetches all three selected playlist queues",beforePlaylist+3,playlistIds.size)
                assertTrue("$mood personalized queue nonempty",songs.isNotEmpty())
                assertEquals("$mood no duplicate queue ids",songs.size,songs.map { it.videoId }.distinct().size)
                assertFalse("$mood disliked song removed",songs.any { it.videoId=="f24" })
                assertFalse("$mood unrelated topper not inserted",songs.any { it.videoId=="foreign" })
                assertTrue("$mood allowed own topper inserted",songs.any { it.videoId=="own" })
                assertEquals("$mood repeat skip demoted","f23",songs.last().videoId)
                assertEquals("$mood opens familiar","Familiar Artist",songs.first().artist)
                assertTrue("$mood discovery retained",songs.any { it.artist.startsWith("Fresh Artist") })
                assertTrue("$mood parsed title survives",songs.any { it.title=="Track 1" })
            }
            assertEquals(28,browseParams.size)
            assertEquals(84,playlistIds.size)
            assertEquals(TopPickMood.entries.map { it.params },browseParams)
        } finally {
            Innertube.activeTransportForTests=oldTransport
            TopPicks.updateTaste(oldTaste)
            cache.clear();cache.putAll(savedCache)
            client.close()
        }
    }
    private suspend fun <T> isolatedTransport(
        handler: (String, JsonObject) -> JsonObject,
        block: suspend () -> T,
    ): T {
        val oldTransport = Innertube.activeTransportForTests
        val tasteField = TopPicks::class.java.getDeclaredField("taste").apply { isAccessible = true }
        val oldTaste = tasteField.get(TopPicks) as Taste.Snapshot?
        val cacheField = YtMusicRepository::class.java.getDeclaredField("moodGenreShelfCache").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cache = cacheField.get(YtMusicRepository) as MutableMap<String, List<com.music.bitchord.data.model.HomeShelf>>
        val oldCache = cache.toMap()
        val client = HttpClient(MockEngine { request ->
            val response = handler(request.url.encodedPath.substringAfterLast('/'), requestBody(request.body))
            respond(response.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(this@MoodQueueProviderIntegrationTest.json) } }
        return try {
            TopPicks.updateTaste(null)
            cache.clear()
            Innertube.activeTransportForTests = client
            block()
        } finally {
            Innertube.activeTransportForTests = oldTransport
            TopPicks.updateTaste(oldTaste)
            cache.clear(); cache.putAll(oldCache)
            client.close()
        }
    }

    private fun fallbackRows() = buildJsonObject {
        putJsonArray("contents") { repeat(24) { i -> add(buildJsonObject {
            putJsonObject("musicResponsiveListItemRenderer") {
                putJsonObject("playlistItemData") { put("videoId", "fallback-$i") }
                putJsonArray("flexColumns") {
                    add(buildJsonObject { putJsonObject("musicResponsiveListItemFlexColumnRenderer") { put("text", text("Fallback track $i")) } })
                    add(buildJsonObject { putJsonObject("musicResponsiveListItemFlexColumnRenderer") { put("text", text("Fallback artist")) } })
                }
            }
        }) } }
    }

    @Test fun everyMoodFallsBackFromEmptyWatchToActualPlaylistParser() = runBlocking<Unit> {
        val fallbackCalls = Collections.synchronizedList(mutableListOf<String>())
        isolatedTransport({ path, body ->
            when (path) {
                "next" -> buildJsonObject { }
                "browse" -> {
                    val id = body["browseId"]!!.jsonPrimitive.content
                    if (id == TopPickMood.CATEGORY_BROWSE_ID) browse()
                    else {
                        assertTrue(id.startsWith("VLown-"))
                        fallbackCalls.add(id)
                        fallbackRows()
                    }
                }
                else -> error("Unexpected fixture request $path")
            }
        }) {
            for (mood in TopPickMood.entries) {
                val before = fallbackCalls.size
                val songs = TopPicks.queue(TopPicks.moodStation(mood, listOf("malayalam"))).getOrThrow()
                assertEquals("$mood must try all selected playlists", before + 3, fallbackCalls.size)
                assertEquals("$mood fallback parser/dedup", 24, songs.size)
                assertTrue(songs.all { it.videoId.startsWith("fallback-") && it.artist == "Fallback artist" })
            }
            assertEquals(84, fallbackCalls.size)
        }
    }

    @Test fun everyMoodFailsWhenCategoryContainsNoPlaylists() = runBlocking<Unit> {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        isolatedTransport({ path, _ -> calls.add(path); buildJsonObject { } }) {
            for (mood in TopPickMood.entries) {
                assertTrue("$mood empty category must fail", TopPicks.queue(TopPicks.moodStation(mood)).isFailure)
                assertEquals("browse", calls.last())
            }
            assertEquals("empty shelves are cached by category, never treated as a playable mood", TopPickMood.entries.map { it.params }.distinct().size, calls.size)
        }
    }

    @Test fun everyMoodFailsWhenWatchAndFallbackPlaylistsAreBothEmpty() = runBlocking<Unit> {
        val watchCalls = java.util.concurrent.atomic.AtomicInteger()
        val fallbackCalls = java.util.concurrent.atomic.AtomicInteger()
        isolatedTransport({ path, body ->
            when {
                path == "browse" && body["browseId"]?.jsonPrimitive?.content == TopPickMood.CATEGORY_BROWSE_ID -> browse()
                path == "next" -> { watchCalls.incrementAndGet(); buildJsonObject { } }
                path == "browse" -> { fallbackCalls.incrementAndGet(); buildJsonObject { } }
                else -> error("Unexpected fixture request $path")
            }
        }) {
            for (mood in TopPickMood.entries) {
                val beforeWatch = watchCalls.get()
                val beforeFallback = fallbackCalls.get()
                assertTrue("$mood must fail rather than return an empty success", TopPicks.queue(TopPicks.moodStation(mood, listOf("malayalam"))).isFailure)
                assertEquals(beforeWatch + 3, watchCalls.get())
                assertEquals(beforeFallback + 3, fallbackCalls.get())
            }
        }
    }

    private fun pairedWatch() = buildJsonObject {
        fun panel(id: String, audio: Boolean) = buildJsonObject {
            put("videoId", id)
            put("title", text(id))
            put("longBylineText", text(if (audio) "Fixture artist" else "Fixture artist • 10M views"))
            putJsonObject("navigationEndpoint") {
                putJsonObject("watchEndpoint") {
                    putJsonObject("watchEndpointMusicSupportedConfigs") {
                        putJsonObject("watchEndpointMusicConfig") {
                            put("musicVideoType", if (audio) "MUSIC_VIDEO_TYPE_ATV" else "MUSIC_VIDEO_TYPE_OMV")
                        }
                    }
                }
            }
        }
        putJsonArray("contents") {
            add(buildJsonObject {
                putJsonObject("playlistPanelVideoWrapperRenderer") {
                    putJsonObject("primaryRenderer") { put("playlistPanelVideoRenderer", panel("paired-video", false)) }
                    putJsonArray("counterpart") { add(buildJsonObject {
                        putJsonObject("counterpartRenderer") { put("playlistPanelVideoRenderer", panel("paired-audio", true)) }
                    }) }
                }
            })
            add(buildJsonObject { put("playlistPanelVideoRenderer", panel("unpaired-video", false)) })
        }
    }

    @Test fun everyMoodUsesProviderAudioPairAndPreservesUnpairedVideoFallback() = runBlocking<Unit> {
        isolatedTransport({ path, _ ->
            when (path) {
                "browse" -> browse()
                "next" -> pairedWatch()
                else -> error("Unexpected fixture request $path")
            }
        }) {
            for (mood in TopPickMood.entries) {
                val songs = TopPicks.queue(TopPicks.moodStation(mood, listOf("malayalam"))).getOrThrow()
                assertEquals(setOf("paired-audio", "unpaired-video"), songs.map { it.videoId }.toSet())
                assertFalse("$mood audio counterpart is not a music video", songs.single { it.videoId == "paired-audio" }.isVideo)
                assertTrue("$mood unpaired video remains an explicit fallback", songs.single { it.videoId == "unpaired-video" }.isVideo)
            }
        }
    }

}
