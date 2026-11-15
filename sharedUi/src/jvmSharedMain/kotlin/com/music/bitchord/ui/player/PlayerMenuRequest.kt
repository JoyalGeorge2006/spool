package com.music.bitchord.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A player sheet the app's track menu can ask the player to open. */
enum class PlayerMenuAction {
    /** The audio output picker, behind the same Bluetooth-permission gate as the old chip. */
    OUTPUT,

    /** The cast dialog, the one the output sheet's "Cast" row opens. */
    CAST,

    /** Who is in the party when in one, otherwise create/join (the old capsule's person half). */
    PARTY,
}

/**
 * Asks the player to open one of its own sheets. Set by the host after it has
 * closed its menu; the player consumes it (reads and clears) in one place, so
 * each [request] opens exactly one sheet. One player per process, so one slot,
 * like [MixViewRequest].
 */
object PlayerMenuRequest {
    var pending by mutableStateOf<PlayerMenuAction?>(null)
        private set

    fun request(action: PlayerMenuAction) {
        pending = action
    }

    internal fun consume(): PlayerMenuAction? = pending.also { pending = null }
}

/**
 * Where the player consumes [PlayerMenuRequest]: each pending request runs the
 * matching callback once and is cleared. Held apart from the screen so it can be
 * driven in a test without building the whole player. The callbacks are read at
 * use, so a stale lambda never runs.
 *
 * A request made while no player is composed stays pending and runs the moment
 * this enters composition.
 */
@Composable
internal fun PlayerMenuRequestEffect(
    onOutput: () -> Unit,
    onCast: () -> Unit,
    onParty: () -> Unit,
) {
    val output by rememberUpdatedState(onOutput)
    val cast by rememberUpdatedState(onCast)
    val party by rememberUpdatedState(onParty)
    val request = PlayerMenuRequest.pending
    LaunchedEffect(request) {
        when (PlayerMenuRequest.consume()) {
            PlayerMenuAction.OUTPUT -> output()
            PlayerMenuAction.CAST -> cast()
            PlayerMenuAction.PARTY -> party()
            null -> Unit
        }
    }
}
