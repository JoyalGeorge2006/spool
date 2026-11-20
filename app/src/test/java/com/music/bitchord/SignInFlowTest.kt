package com.music.bitchord

import com.music.bitchord.auth.CapturedSession
import com.music.bitchord.auth.WebSessionMode
import com.music.bitchord.auth.autoIdentityOf
import com.music.bitchord.auth.forChannel
import com.music.bitchord.auth.isMusicOriginUrl
import com.music.bitchord.auth.needsProfileChoice
import com.music.bitchord.auth.nextStability
import com.music.bitchord.auth.shouldAutoCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInFlowTest {
    private val music = "https://music.youtube.com/"

    private fun session(pageId: String? = null, sync: String? = null, user: String? = "0") =
        CapturedSession(
            cookie = "test-cookie", pageId = pageId, dataSyncId = sync, authUser = user,
            visitorData = null, clientVersion = null, loggedIn = true,
        )

    @Test fun `fresh sign-in auto-captures only on the music origin`() {
        assertTrue(shouldAutoCapture(WebSessionMode.SIGN_IN, music, false))
        assertTrue(shouldAutoCapture(WebSessionMode.SIGN_IN, "https://music.youtube.com/library", false))
        assertFalse(shouldAutoCapture(WebSessionMode.SIGN_IN, "https://accounts.google.com/v3/signin", false))
        assertFalse(shouldAutoCapture(WebSessionMode.SIGN_IN, "https://music.youtube.com.evil.example/", false))
        assertFalse(shouldAutoCapture(WebSessionMode.SIGN_IN, null, false))
    }

    @Test fun `channel switching never auto-captures`() {
        assertFalse(shouldAutoCapture(WebSessionMode.SWITCH_CHANNEL, music, false))
    }

    @Test fun `a capture already handed on is not repeated`() {
        assertFalse(shouldAutoCapture(WebSessionMode.SIGN_IN, music, true))
    }

    @Test fun `music origin check rejects lookalikes`() {
        assertTrue(isMusicOriginUrl(music))
        assertFalse(isMusicOriginUrl("https://music.youtube.com.evil.example/"))
        assertFalse(isMusicOriginUrl(null))
    }

    @Test fun `identity must repeat before it counts as settled`() {
        var step = nextStability(null, 0, "a")
        assertEquals("a" to 1, step)
        step = nextStability(step.first, step.second, "a")
        assertEquals("a" to 2, step)
    }

    @Test fun `a changed or missing identity restarts the count`() {
        assertEquals("b" to 1, nextStability("a", 1, "b"))
        assertEquals(null to 0, nextStability("a", 1, null))
    }

    @Test fun `a session with no identity fields gives no auto identity`() {
        assertNull(autoIdentityOf(session()))
        assertTrue(autoIdentityOf(session(sync = "123")) != null)
        assertTrue(autoIdentityOf(session(pageId = "p")) != autoIdentityOf(session(pageId = "q")))
    }

    @Test fun `only more than one channel needs a choice`() {
        assertFalse(needsProfileChoice(0))
        assertFalse(needsProfileChoice(1))
        assertTrue(needsProfileChoice(2))
    }

    @Test fun `forChannel changes identity but keeps the login`() {
        val chosen = session(pageId = "old", sync = "o").forChannel("new", "n")
        assertEquals("new", chosen.pageId)
        assertEquals("n", chosen.dataSyncId)
        assertEquals("test-cookie", chosen.cookie)
    }
    @Test fun `closed attempt rejects late callback`() {
        val attempt = com.music.bitchord.auth.SignInAttempt()
        assertTrue(attempt.active)
        attempt.close()
        assertFalse(attempt.active)
        attempt.close()
        assertFalse(attempt.active)
    }
    @Test fun `navigation and disposal revoke pending page probes`() {
        val gate = com.music.bitchord.auth.SignInPageGate()
        val first = gate.next()
        assertTrue(gate.accepts(first))
        val second = gate.next()
        assertFalse(gate.accepts(first))
        assertTrue(gate.accepts(second))
        gate.close()
        assertFalse(gate.accepts(second))
        assertFalse(gate.accepts(gate.next()))
    }


    @Test fun `main-frame failure revokes pending page captures until new attempt`() {
        val gate = com.music.bitchord.auth.SignInPageGate()
        val beforeFailure = gate.next()
        assertTrue(gate.accepts(beforeFailure))
        gate.failMainFrame()
        assertTrue(gate.loadFailed)
        assertFalse(gate.accepts(beforeFailure))
        assertFalse(gate.accepts(gate.token()))
        val retry = gate.next()
        assertFalse(gate.loadFailed)
        assertFalse(gate.accepts(beforeFailure))
        assertTrue(gate.accepts(retry))
        gate.close()
        assertFalse(gate.accepts(retry))
    }
}
