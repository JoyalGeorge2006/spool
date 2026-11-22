package com.music.bitchord.data.innertube

import com.music.bitchord.data.YtMusicRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class ListenerGenerationTest {
 private fun response(id: String) = """{"contents":[{"musicResponsiveListItemRenderer":{
 "playlistItemData":{"videoId":"$id"},"flexColumns":[
 {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Fixture title"}]}}},
 {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Fixture artist"}]}}}]
 }}]}"""
 private fun session() {
  Innertube.cookie="SAPISID=isolated-fixture"
  Innertube.adoptSessionScope("page-a","sync-a","0","fixture-visitor","fixture-version",true)
  Innertube.selectChannel("page-a","sync-a","0")
  YtMusicRepository.invalidateHistoryCache()
 }
 private fun transport(handler: suspend () -> String) = HttpClient(MockEngine { respond(handler(),headers=headersOf(HttpHeaders.ContentType,"application/json")) }) {
  install(ContentNegotiation) { json() }
 }
 @Test fun cookieAndChannelTransitionsHaveGenerationsIncludingAToBToA() {
  val old=Innertube.cookie
  try {
   session();val a=Innertube.listenerGeneration
   Innertube.selectChannel("page-a","sync-a","0");assertEquals(a,Innertube.listenerGeneration)
   Innertube.selectChannel("page-b","sync-b","0");val b=Innertube.listenerGeneration;assertTrue(b>a)
   Innertube.selectChannel("page-a","sync-a","0");assertTrue(Innertube.listenerGeneration>b)
   val before=Innertube.listenerGeneration;Innertube.cookie=null;assertTrue(Innertube.listenerGeneration>before)
  } finally {Innertube.cookie=old}
 }
 @Test fun sameCookieProfileSwitchCannotReuseHistoryMemo() = runBlocking<Unit> {
  val old=Innertube.cookie;val oldTransport=Innertube.activeTransportForTests
  var calls=0
  val client=transport { calls++;response(if(calls==1) "profile-a" else "profile-b") }
  try {
   session();Innertube.activeTransportForTests=client
   assertEquals(listOf("profile-a"),YtMusicRepository.recentHistory().getOrThrow().map{it.videoId})
   assertEquals(listOf("profile-a"),YtMusicRepository.recentHistory().getOrThrow().map{it.videoId});assertEquals(1,calls)
   Innertube.selectChannel("page-b","sync-b","0")
   assertEquals(listOf("profile-b"),YtMusicRepository.recentHistory().getOrThrow().map{it.videoId});assertEquals(2,calls)
  } finally {Innertube.activeTransportForTests=oldTransport;Innertube.cookie=old;client.close()}
 }
 @Test fun inFlightOldHistoryIsRejectedAndCannotSeedNewProfileMemo() = runBlocking<Unit> {
  val old=Innertube.cookie;val oldTransport=Innertube.activeTransportForTests
  val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var calls=0
  val client=transport { calls++;if(calls==1){started.complete(Unit);release.await();response("old-profile")}else response("new-profile") }
  try {
   session();Innertube.activeTransportForTests=client
   val request=async {YtMusicRepository.recentHistory()};withTimeout(2000){started.await()}
   Innertube.selectChannel("page-b","sync-b","0");release.complete(Unit)
   assertTrue(withTimeout(2000){request.await()}.isFailure)
   assertEquals(listOf("new-profile"),YtMusicRepository.recentHistory().getOrThrow().map{it.videoId})
  } finally {release.complete(Unit);Innertube.activeTransportForTests=oldTransport;Innertube.cookie=old;client.close()}
 }
 @Test fun inFlightLibraryCannotSeedOldLikesAfterProfileSwitch() = runBlocking<Unit> {
  val old=Innertube.cookie;val oldTransport=Innertube.activeTransportForTests
  val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
  val client=transport { started.complete(Unit);release.await();response("old-library-song") }
  try {
   session();com.music.bitchord.data.LikeState.clear();Innertube.activeTransportForTests=client
   val request=async {YtMusicRepository.library()};withTimeout(2000){started.await()}
   Innertube.selectChannel("page-b","sync-b","0");release.complete(Unit)
   val result=withTimeout(2000){request.await()}
   assertTrue(com.music.bitchord.data.LikeState.overrides.value.isEmpty())
   assertTrue(result.isFailure)
  } finally {release.complete(Unit);Innertube.activeTransportForTests=oldTransport;Innertube.cookie=old;client.close();com.music.bitchord.data.LikeState.clear()}
 }
 @Test fun likedContinuationCannotSeedAfterListenerSwitchDuringPageLoad() = runBlocking<Unit> {
  val old=Innertube.cookie
  try {
   session();com.music.bitchord.data.LikeState.clear()
   YtMusicRepository.syncLikedMusic("first") {
    Innertube.selectChannel("page-b","sync-b","0")
    YtMusicRepository.SongPage(listOf(com.music.bitchord.data.model.Song(
     videoId="old-like",title="Old",artist="Fixture",thumbnailUrl=null)),null)
   }
   assertTrue(com.music.bitchord.data.LikeState.overrides.value.isEmpty())
  } finally {Innertube.cookie=old;com.music.bitchord.data.LikeState.clear()}
 }
 @Test fun currentListenerLikedContinuationStillSeedsItsPages() = runBlocking<Unit> {
  val old=Innertube.cookie
  try {
   session();com.music.bitchord.data.LikeState.clear()
   YtMusicRepository.syncLikedMusic("first") {
    YtMusicRepository.SongPage(listOf(com.music.bitchord.data.model.Song(
     videoId="current-like",title="Current",artist="Fixture",thumbnailUrl=null)),null)
   }
   assertEquals(com.music.bitchord.data.model.LikeStatus.LIKE,com.music.bitchord.data.LikeState.overrides.value["current-like"])
  } finally {Innertube.cookie=old;com.music.bitchord.data.LikeState.clear()}
 }

 @Test fun rejectedRatingCannotRetryUnderTheNewListener() = runBlocking<Unit> {
  val old=Innertube.cookie;val oldTransport=Innertube.activeTransportForTests
  val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val pages=mutableListOf<String?>()
  val client=HttpClient(MockEngine { request ->
   if (request.url.encodedPath.endsWith("/like/like")) {
    pages.add(request.headers["X-Goog-PageId"])
    if (pages.size==1) {started.complete(Unit);release.await();error("403 rejected fixture")}
    respond("{}",headers=headersOf(HttpHeaders.ContentType,"application/json"))
   } else respond("{}",headers=headersOf(HttpHeaders.ContentType,"text/html"))
  }) { install(ContentNegotiation) { json() } }
  try {
   session();Innertube.activeTransportForTests=client
   val rating=async {YtMusicRepository.rate("fixture-track",com.music.bitchord.data.model.LikeStatus.LIKE)}
   withTimeout(2000){started.await()};Innertube.selectChannel("page-b","sync-b","0");release.complete(Unit)
   val result=withTimeout(2000){rating.await()}
   assertEquals(listOf("page-a"),pages);assertTrue(result.isFailure)
  } finally {release.complete(Unit);Innertube.activeTransportForTests=oldTransport;Innertube.cookie=old;client.close()}
 }
 @Test fun transportRetryCannotMoveAnOldRatingToTheNewProfile() = runBlocking<Unit> {
  val old=Innertube.cookie;val oldTransport=Innertube.activeTransportForTests
  val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val pages=mutableListOf<String?>()
  val client=HttpClient(MockEngine { request ->
   pages.add(request.headers["X-Goog-PageId"])
   if (pages.size==1) {started.complete(Unit);release.await();throw java.io.IOException("fixture disconnect")}
   respond("{}",headers=headersOf(HttpHeaders.ContentType,"application/json"))
  }) { install(ContentNegotiation) { json() } }
  try {
   session();Innertube.activeTransportForTests=client
   val rating=async {runCatching {Innertube.rate("fixture-track",com.music.bitchord.data.model.LikeStatus.LIKE)}}
   withTimeout(2000){started.await()};Innertube.selectChannel("page-b","sync-b","0");release.complete(Unit)
   val result=withTimeout(3000){rating.await()}
   assertEquals(listOf("page-a"),pages);assertTrue(result.isFailure)
  } finally {release.complete(Unit);Innertube.activeTransportForTests=oldTransport;Innertube.cookie=old;client.close()}
 }

 @Test fun sameListenerTransportRetryStillWorks() = runBlocking<Unit> {
  val old=Innertube.cookie;val oldTransport=Innertube.activeTransportForTests;val pages=mutableListOf<String?>()
  val client=HttpClient(MockEngine { request ->
   pages.add(request.headers["X-Goog-PageId"])
   if(pages.size==1)throw java.io.IOException("fixture disconnect")
   respond("{}",headers=headersOf(HttpHeaders.ContentType,"application/json"))
  }) { install(ContentNegotiation) { json() } }
  try {
   session();Innertube.activeTransportForTests=client
   YtMusicRepository.rate("fixture-track",com.music.bitchord.data.model.LikeStatus.LIKE).getOrThrow()
   assertEquals(listOf("page-a","page-a"),pages)
  } finally {Innertube.activeTransportForTests=oldTransport;Innertube.cookie=old;client.close()}
 }
 @Test fun sameListenerAuthenticatedRejectionCanRefreshAndRetry() = runBlocking<Unit> {
  val old=Innertube.cookie;val oldTransport=Innertube.activeTransportForTests;val pages=mutableListOf<String?>()
  val client=HttpClient(MockEngine { request ->
   if(request.url.encodedPath.endsWith("/like/like")) {
    pages.add(request.headers["X-Goog-PageId"])
    if(pages.size==1)error("403 rejected fixture")
    respond("{}",headers=headersOf(HttpHeaders.ContentType,"application/json"))
   } else respond("{}",headers=headersOf(HttpHeaders.ContentType,"text/html"))
  }) { install(ContentNegotiation) { json() } }
  try {
   session();Innertube.activeTransportForTests=client
   YtMusicRepository.rate("fixture-track",com.music.bitchord.data.model.LikeStatus.LIKE).getOrThrow()
   assertEquals(listOf("page-a","page-a"),pages)
  } finally {Innertube.activeTransportForTests=oldTransport;Innertube.cookie=old;client.close()}
 }
 @Test fun stagedRequestDoesNotAdoptOrDependOnAnActiveListenerSwitch() = runBlocking<Unit> {
  val old=Innertube.cookie;val oldTransport=Innertube.stagedTransportForTests
  val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var sentCookie:String?=null
  val client=HttpClient(MockEngine { request ->
   sentCookie=request.headers["Cookie"];started.complete(Unit);release.await()
   respond("{}",headers=headersOf(HttpHeaders.ContentType,"application/json"))
  }) { install(ContentNegotiation) { json() } }
  try {
   session();Innertube.stagedTransportForTests=client
   val staged=Innertube.StagedCredentials("SAPISID=staged-fixture","staged-page","staged-sync","0","fixture-visitor","fixture-version")
   val request=async {Innertube.stagedAccountMenu(staged)};withTimeout(2000){started.await()}
   Innertube.selectChannel("page-b","sync-b","0");val current=Innertube.listenerGeneration;release.complete(Unit)
   withTimeout(2000){request.await()}
   assertEquals("SAPISID=staged-fixture",sentCookie);assertEquals(current,Innertube.listenerGeneration)
   assertEquals("SAPISID=isolated-fixture",Innertube.cookie)
  } finally {release.complete(Unit);Innertube.stagedTransportForTests=oldTransport;Innertube.cookie=old;client.close()}
 }


 @Test fun reorderCannotAdoptANewListenerDuringPlanning() = runBlocking<Unit> {
  val old=Innertube.cookie;val oldTransport=Innertube.activeTransportForTests
  val pages=mutableListOf<String?>()
  val client=HttpClient(MockEngine { request ->
   pages.add(request.headers["X-Goog-PageId"])
   respond("{}",headers=headersOf(HttpHeaders.ContentType,"application/json"))
  }) { install(ContentNegotiation) { json() } }
  try {
   session();Innertube.activeTransportForTests=client
   val source=List(120){"entry-$it"};val reversed=source.reversed();var switched=false
   val target=object:AbstractList<String>() {
    override val size get()=reversed.size
    override fun get(index:Int):String {
     if(!switched){switched=true;Innertube.selectChannel("page-b","sync-b","0")}
     return reversed[index]
    }
   }
   val result=YtMusicRepository.reorderPlaylist("fixture-playlist",source,target)
   assertTrue(result.isFailure)
   assertTrue("Stale operation sent new-profile writes: $pages",pages.isEmpty())
  } finally {Innertube.activeTransportForTests=oldTransport;Innertube.cookie=old;client.close()}
 }

 @Test fun sameListenerReorderStillSendsAllThreeBatches() = runBlocking<Unit> {
  val old=Innertube.cookie;val previous=Innertube.activeTransportForTests;val pages=mutableListOf<String?>()
  val client=HttpClient(MockEngine { request -> pages.add(request.headers["X-Goog-PageId"]);respond("{}",headers=headersOf(HttpHeaders.ContentType,"application/json")) }) {install(ContentNegotiation){json()}}
  try {
   session();Innertube.activeTransportForTests=client;val source=List(120){"entry-$it"}
   assertTrue(YtMusicRepository.reorderPlaylist("fixture",source,source.reversed()).isSuccess)
   assertEquals(listOf("page-a","page-a","page-a"),pages)
  } finally {Innertube.activeTransportForTests=previous;Innertube.cookie=old;client.close()}
 }
 @Test fun nestedRepositoryCallInheritsOlderOperationAndSendsNothingAfterSwitch() = runBlocking<Unit> {
  val old=Innertube.cookie;val previous=Innertube.activeTransportForTests;var calls=0
  val client=transport {calls++;"{}"}
  try {
   session();Innertube.activeTransportForTests=client;val generation=Innertube.listenerGeneration
   Innertube.selectChannel("page-b","sync-b","0")
   val result=withContext(Innertube.listenerContext(generation)) {
    // UI action has already captured its origin, but repository entry is later.
    YtMusicRepository.renamePlaylist("fixture","New name")
   }
   assertTrue(result.isFailure);assertEquals(0,calls)
  } finally {Innertube.activeTransportForTests=previous;Innertube.cookie=old;client.close()}
 }
 @Test fun reactiveIdentityTracksCookieRotationAndChannelChangesIncludingAToBToA() {
  val old=Innertube.cookie
  try {
   session();val a=Innertube.listenerGenerationFlow.value
   assertEquals(Innertube.listenerGeneration,a)
   Innertube.cookie=Innertube.cookie;assertEquals(a,Innertube.listenerGenerationFlow.value)
   Innertube.cookie="SAPISID=rotated-fixture"
   val rotated=Innertube.listenerGenerationFlow.value;assertTrue(rotated>a)
   assertEquals(Innertube.listenerGeneration,rotated)
   Innertube.selectChannel("page-b","sync-b","0");val b=Innertube.listenerGenerationFlow.value
   Innertube.selectChannel("page-a","sync-a","0");assertTrue(Innertube.listenerGenerationFlow.value>b)
   assertEquals(Innertube.listenerGeneration,Innertube.listenerGenerationFlow.value)
  } finally {Innertube.cookie=old}
 }
}
