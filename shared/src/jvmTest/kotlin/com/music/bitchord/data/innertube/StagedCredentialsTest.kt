package com.music.bitchord.data.innertube

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Staging must never reach the active session. Only the no-network guard is
 * covered here: the HTTP client is private and its base URLs are constants, so
 * header-level isolation needs a seam this patch does not add.
 */
class StagedCredentialsTest {
    private var savedCookie: String? = null

    @Before fun setUp() {
        savedCookie = Innertube.cookie
        Innertube.cookie = "ACTIVE=1"
    }

    @After fun tearDown() {
        Innertube.cookie = savedCookie
    }

    private val noSapisid = Innertube.StagedCredentials(
        cookie = "CANDIDATE=1", pageId = "p", dataSyncId = "d",
        authUser = "0", visitorData = null, clientVersion = null,
    )

    @Test fun `a candidate without a signing cookie is refused and the active cookie is untouched`() = runBlocking {
        val failures = listOf(
            runCatching { Innertube.stagedAccountMenu(noSapisid) },
            runCatching { Innertube.stagedAccountsList(noSapisid) },
            runCatching { Innertube.stagedAccountSwitcher(noSapisid) },
        )
        failures.forEach {
            assertTrue(it.exceptionOrNull() is Innertube.NotSignedInException)
        }
        assertEquals("ACTIVE=1", Innertube.cookie)
    }

    @Test fun `staging leaves no candidate cookie behind`() = runBlocking {
        runCatching { Innertube.stagedAccountMenu(noSapisid) }
        assertTrue(Innertube.cookie != noSapisid.cookie)
    }
}
