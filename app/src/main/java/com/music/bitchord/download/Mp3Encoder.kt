package com.music.bitchord.download

/**
 * JNI front to the bundled libmp3lame (see native/lame and the
 * `bitchord_lame` target in CMakeLists.txt).
 *
 * Android ships no MP3 encoder — MediaCodec encodes AAC and AMR only — so the
 * "Save MP3 copy" export encodes through LAME instead. One [Session] is one
 * encoder instance; PCM goes in interleaved 16-bit, encoded MP3 frames come
 * out. Not thread-safe: a session belongs to the one export that opened it.
 */
internal object Mp3Encoder {

    init {
        System.loadLibrary("bitchord_lame")
    }

    class Session(sampleRate: Int, channels: Int, bitrateKbps: Int) {
        private var handle: Long = nativeInit(sampleRate, channels, bitrateKbps)

        init {
            check(handle != 0L) { "LAME refused ${sampleRate}Hz/$channels ch" }
        }

        /** @param count how many shorts of [pcm] are real samples; the array may be longer. */
        fun encode(pcm: ShortArray, count: Int): ByteArray =
            nativeEncode(handle, pcm, count) ?: ByteArray(0)

        /** The frames LAME was still holding back. Call once, then [close]. */
        fun flush(): ByteArray = nativeFlush(handle) ?: ByteArray(0)

        fun close() {
            if (handle != 0L) {
                nativeClose(handle)
                handle = 0
            }
        }
    }

    private external fun nativeInit(sampleRate: Int, channels: Int, bitrateKbps: Int): Long
    private external fun nativeEncode(handle: Long, pcm: ShortArray, shortCount: Int): ByteArray?
    private external fun nativeFlush(handle: Long): ByteArray?
    private external fun nativeClose(handle: Long)
}
