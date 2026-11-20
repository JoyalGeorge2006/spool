package com.music.bitchord.auth

/** Gap between identity reads while waiting for a landing to settle. */
internal const val AUTO_POLL_MS = 600L

/** Give up auto-capturing after this many reads (about 15 s); the manual fallback remains. */
internal const val AUTO_POLL_MAX = 25

/** The same identity must be read this many times in a row before it is trusted. */
internal const val AUTO_STABLE_READS = 2

/**
 * Whether landing on [url] should start waiting for a settled identity. Only a
 * fresh sign-in does: switching channel keeps its explicit "use this profile"
 * tap, and a capture already handed on is never repeated by a later page load.
 */
internal fun shouldAutoCapture(mode: WebSessionMode, url: String?, handedOff: Boolean): Boolean =
    mode == WebSessionMode.SIGN_IN && !handedOff && isMusicOriginUrl(url)

/** Exactly the Music origin, so "music.youtube.com.example" does not count. */
internal fun isMusicOriginUrl(url: String?): Boolean =
    url != null && (url == MUSIC_ORIGIN || url.startsWith("$MUSIC_ORIGIN/") ||
        url.startsWith("$MUSIC_ORIGIN?") || url.startsWith("$MUSIC_ORIGIN#"))

/**
 * One step of "wait until the page's identity stops changing". A null
 * [identity] (not signed in yet, or no identity fields on the page) resets the
 * count; the same identity as last time adds one; a different one restarts at 1.
 */
internal fun nextStability(lastIdentity: String?, stable: Int, identity: String?): Pair<String?, Int> =
    when {
        identity == null -> null to 0
        identity == lastIdentity -> identity to stable + 1
        else -> identity to 1
    }

/** A captured session is only worth auto-handing-on once it names an identity. */
internal fun autoIdentityOf(session: CapturedSession): String? =
    if (session.pageId == null && session.dataSyncId == null) null
    else "${session.pageId}|${session.dataSyncId}|${session.authUser}"

/** A Google login with more than one YouTube channel gets an explicit choice. */
internal fun needsProfileChoice(channelCount: Int): Boolean = channelCount > 1

/** The session to save once the listener chose [channel]: the same login, that channel's identity. */
internal fun CapturedSession.forChannel(pageId: String?, dataSyncId: String?): CapturedSession =
    copy(pageId = pageId, dataSyncId = dataSyncId)

/** Revoked synchronously on close/retry and checked before accepting a callback. */
internal class SignInAttempt {
    var active: Boolean = true
        private set
    fun close() { active = false }
}

/** A navigation or disposal invalidates pending page probes. */
internal class SignInPageGate {
    private var generation = 0
    private var open = true
    var loadFailed: Boolean = false
        private set
    fun token(): Int = generation
    fun next(): Int { generation++; loadFailed = false; return generation }
    /** Main-document failures revoke both automatic and manual pending reads. */
    fun failMainFrame() { generation++; loadFailed = true }
    fun accepts(token: Int): Boolean = open && !loadFailed && token == generation
    fun close() { open = false; generation++ }
}
