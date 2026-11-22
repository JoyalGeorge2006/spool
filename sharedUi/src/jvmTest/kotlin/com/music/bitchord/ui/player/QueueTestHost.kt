package com.music.bitchord.ui.player

import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The least host the queue needs, for tests only.
 *
 * [InlineQueue] and its rows touch the host for the artwork URL and, through
 * the settings, for a few boolean flows (queue history, reduce animation). A
 * full [PlayerHost] has dozens of members the queue never reads, so this
 * answers by reflection: every flow is `false`, an artwork URL is null, a
 * suspend or Unit member does nothing. Reading any other setting type would
 * fail loudly with a ClassCastException, which is the signal to widen this
 * deliberately rather than to trust a guess. If the builder already has a real
 * test host, use that and delete this file.
 */
internal fun installQueueTestHost() {
    val loader = PlayerHost::class.java.classLoader
    val settings = Proxy.newProxyInstance(loader, arrayOf(PlayerSettingsSource::class.java)) { _, method, _ ->
        if (StateFlow::class.java.isAssignableFrom(method.returnType)) MutableStateFlow(false) else null
    } as PlayerSettingsSource
    val host = Proxy.newProxyInstance(loader, arrayOf(PlayerHost::class.java)) { proxy, method, args ->
        when {
            method.name == "getSettings" -> settings
            method.name == "rememberRemoteArtworkUrl" -> null
            method.isDefault -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, *(args ?: emptyArray()))
            StateFlow::class.java.isAssignableFrom(method.returnType) -> MutableStateFlow(false)
            else -> null
        }
    } as PlayerHost
    PlayerPlatform.install(host)
}
