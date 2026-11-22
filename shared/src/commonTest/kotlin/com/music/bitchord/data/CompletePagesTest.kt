package com.music.bitchord.data

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CompletePagesTest {
    @Test fun followsEveryPageAndDeduplicatesStableOrder() = runBlocking<Unit> {
        val requested = mutableListOf<String>()
        val result = completePages(ContinuationPage(listOf(1, 2), "a"), 5, { it }) {
            requested += it
            when (it) {
                "a" -> ContinuationPage(listOf(2, 3), "b")
                "b" -> ContinuationPage(listOf(4), null)
                else -> error("unexpected token")
            }
        }
        assertEquals(listOf(1, 2, 3, 4), result)
        assertEquals(listOf("a", "b"), requested)
    }
    @Test fun emptyContinuationPageIsNotEnd() = runBlocking<Unit> {
        assertEquals(listOf(1, 2), completePages(ContinuationPage(listOf(1), "a"), 5, { it }) {
            if (it == "a") ContinuationPage(emptyList(), "b") else ContinuationPage(listOf(2), null)
        })
    }
    @Test fun failureCannotReturnSuccessfulPartialList() = runBlocking<Unit> {
        assertFailsWith<IllegalStateException> {
            completePages(ContinuationPage(listOf(1), "a"), 5, { it }) { error("page failed") }
        }
    }
    @Test fun repeatedTokenIsRejected() = runBlocking<Unit> {
        assertFailsWith<IllegalStateException> {
            completePages(ContinuationPage(listOf(1), "a"), 5, { it }) { ContinuationPage(listOf(2), "a") }
        }
    }
    @Test fun pageCapIsExplicitFailureNotTruncation() = runBlocking<Unit> {
        assertFailsWith<IllegalStateException> {
            completePages(ContinuationPage(listOf(1), "a"), 1, { it }) { error("must not fetch") }
        }
    }
    @Test fun completePageAtCapSucceeds() = runBlocking<Unit> {
        assertEquals(listOf(1), completePages(ContinuationPage(listOf(1), null), 1, { it }) { error("no continuation") })
    }
}
