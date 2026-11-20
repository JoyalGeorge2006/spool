package com.music.bitchord.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/** Applies to local MP4s and muxed fallbacks too. Track IDs and audio are untouched. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun audioOnlyTrackSelector(context: Context): DefaultTrackSelector =
    DefaultTrackSelector(context).apply {
        parameters = buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
    }

/** No picture renderer is even constructed, including for muxed local files. */
@androidx.annotation.OptIn(UnstableApi::class)
internal open class AudioOnlyRenderersFactory(context: Context) : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: android.os.Handler,
        eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<androidx.media3.exoplayer.Renderer>,
    ) = Unit
}

internal const val NO_PLAYABLE_AUDIO_MESSAGE = "This file has no playable audio. Choose a file with an audio track."

/** Empty means track discovery is still pending, not that a file is silent. */
internal fun hasNoPlayableAudio(tracks: androidx.media3.common.Tracks): Boolean =
    !tracks.isEmpty && !tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)

/** Shared by the session and local-file acceptance probe. Never changes the queue. */
internal fun pauseIfNoPlayableAudio(player: androidx.media3.common.Player): Boolean {
    if (!player.playWhenReady || !hasNoPlayableAudio(player.currentTracks)) return false
    player.pause()
    return true
}

internal fun audioOnlyPlaybackError(tracks: androidx.media3.common.Tracks, fallback: String?): String? =
    if (hasNoPlayableAudio(tracks)) NO_PLAYABLE_AUDIO_MESSAGE else fallback.takeUnless { it == NO_PLAYABLE_AUDIO_MESSAGE }
