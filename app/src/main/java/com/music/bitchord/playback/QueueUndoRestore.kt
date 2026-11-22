package com.music.bitchord.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** Shared by service and real-player regression tests. Never seeks or calls play. */
internal fun Player.restoreRemovedQueueItem(index: Int, item: MediaItem) {
    if (mediaItemCount == 0 || playbackState == Player.STATE_ENDED) pause()
    addMediaItems(index, listOf(item))
}

/** A queue-only action must not turn the protective local pause into group intent. */
internal fun canRestoreQueueWithoutPartyPause(
    inParty: Boolean,
    partyPlaying: Boolean,
    itemCount: Int,
    playbackState: Int,
): Boolean = !inParty || !partyPlaying || (itemCount > 0 && playbackState != Player.STATE_ENDED)
