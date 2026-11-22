package com.music.bitchord.ui

/** Confirmed progress only: a failed response does not prove a remote batch was rolled back. */
data class PlaylistImportResult(
    val title: String,
    val browseId: String?,
    val requestedCount: Int,
    val confirmedCount: Int,
    val complete: Boolean,
    val savedLocally: Boolean,
    val listenerGeneration: Long,
    val accountChanged: Boolean = false,
    val creationAttempted: Boolean = false,
)

/** Text is deliberately title/account-free when the listener changed. Never navigate on it. */
enum class PlaylistImportNotice { PARTIAL, ACCOUNT_CHANGED, CREATION_UNCONFIRMED, STOPPED }

fun PlaylistImportResult.noticeFor(currentGeneration: Long): PlaylistImportNotice? {
    if (complete) return null // The existing complete/local callback owns success feedback.
    val changed = accountChanged || listenerGeneration != currentGeneration
    return when {
        changed && browseId != null -> PlaylistImportNotice.ACCOUNT_CHANGED
        changed && creationAttempted -> PlaylistImportNotice.CREATION_UNCONFIRMED
        changed -> PlaylistImportNotice.STOPPED
        browseId != null -> PlaylistImportNotice.PARTIAL
        else -> null
    }
}
