package com.music.bitchord.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The menu bridge: [PlayerMenuRequest] consumed by the real
 * [PlayerMenuRequestEffect] the player mounts. Output, cast and party each run
 * their own callback exactly once; a second identical request runs again; a
 * request made while no player is composed waits and runs when one appears.
 *
 * What this does not cover: the permission prompt behind `openAudioOutput` and
 * the sheets the callbacks open. Those are the host's and need a device.
 */
@OptIn(ExperimentalTestApi::class)
class PlayerMenuRequestTest {
    @AfterTest
    fun reset() {
        PlayerMenuRequest.consume()
    }

    private class Calls {
        val log = mutableListOf<String>()
    }

    @Test
    fun `each action runs only its own callback, once`() = runComposeUiTest {
        val calls = Calls()
        setContent {
            PlayerMenuRequestEffect(
                onOutput = { calls.log += "output" },
                onCast = { calls.log += "cast" },
                onParty = { calls.log += "party" },
            )
        }
        for (action in PlayerMenuAction.entries) {
            PlayerMenuRequest.request(action)
            waitForIdle()
        }
        assertEquals(listOf("output", "cast", "party"), calls.log)
        assertNull(PlayerMenuRequest.pending)
    }

    @Test
    fun `the same action requested twice runs twice`() = runComposeUiTest {
        val calls = Calls()
        setContent {
            PlayerMenuRequestEffect(
                onOutput = { calls.log += "output" },
                onCast = {},
                onParty = {},
            )
        }
        PlayerMenuRequest.request(PlayerMenuAction.OUTPUT)
        waitForIdle()
        PlayerMenuRequest.request(PlayerMenuAction.OUTPUT)
        waitForIdle()
        assertEquals(listOf("output", "output"), calls.log)
    }

    @Test
    fun `a request made with no player composed waits for one`() = runComposeUiTest {
        val calls = Calls()
        var shown by mutableStateOf(false)
        setContent {
            if (shown) {
                PlayerMenuRequestEffect(
                    onOutput = {},
                    onCast = {},
                    onParty = { calls.log += "party" },
                )
            }
        }
        PlayerMenuRequest.request(PlayerMenuAction.PARTY)
        waitForIdle()
        assertEquals(PlayerMenuAction.PARTY, PlayerMenuRequest.pending)
        assertEquals(emptyList<String>(), calls.log)

        shown = true
        waitForIdle()
        assertEquals(listOf("party"), calls.log)
        assertNull(PlayerMenuRequest.pending)
    }

    @Test
    fun `callbacks are read at use, so a recomposed lambda is the one that runs`() = runComposeUiTest {
        val calls = Calls()
        var tag by mutableStateOf("first")
        setContent {
            val current = tag
            PlayerMenuRequestEffect(
                onOutput = { calls.log += current },
                onCast = {},
                onParty = {},
            )
        }
        tag = "second"
        waitForIdle()
        PlayerMenuRequest.request(PlayerMenuAction.OUTPUT)
        waitForIdle()
        assertEquals(listOf("second"), calls.log)
    }
}
