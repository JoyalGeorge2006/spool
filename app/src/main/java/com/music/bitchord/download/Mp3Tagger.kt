package com.music.bitchord.download

import com.music.bitchord.data.model.Song
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The ID3v2.3 tag written at the front of every exported MP3.
 *
 * A small writer rather than a library, in the same spirit as [Mp4Tagger] and
 * [WebmTagger] beside it: an MP3 export is the version of a download most
 * likely to leave the phone, so it carries the same title, artist, album,
 * track number, cover and lyrics the other containers get. v2.3 rather than
 * v2.4 because the files are meant for arbitrary players, and v2.3 is the
 * revision everything that reads ID3 at all understands — including Windows
 * Explorer and the Android media scanner's UTF-16 handling.
 *
 * LAME is told not to write its own tag; this byte string is simply written
 * ahead of the encoded frames.
 */
internal object Mp3Tagger {

    fun tag(
        track: Song,
        lyrics: LyricsTag.Embeddable? = null,
        artwork: MediaTagger.Artwork? = null,
    ): ByteArray {
        val frames = ByteArrayOutputStream()
        textFrame(frames, "TIT2", track.title)
        if (track.artist.isNotBlank()) textFrame(frames, "TPE1", track.artist)
        track.albumName?.takeIf { it.isNotBlank() }?.let { textFrame(frames, "TALB", it) }
        track.trackNumber?.takeIf { it > 0 }?.let { textFrame(frames, "TRCK", it.toString()) }
        lyrics?.plain?.takeIf { it.isNotBlank() }?.let { lyricsFrame(frames, it) }
        artwork?.let { pictureFrame(frames, it.bytes, it.mime) }

        val body = frames.toByteArray()
        val out = ByteArrayOutputStream(10 + body.size)
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        out.write(synchsafe(body.size))
        out.write(body)
        return out.toByteArray()
    }

    /** ID3 sizes are 28-bit: seven bits per byte, so a stray 0xFF can never read as an MPEG sync. */
    private fun synchsafe(size: Int): ByteArray = byteArrayOf(
        ((size shr 21) and 0x7F).toByte(),
        ((size shr 14) and 0x7F).toByte(),
        ((size shr 7) and 0x7F).toByte(),
        (size and 0x7F).toByte(),
    )

    /** UTF-16 with BOM: encoding byte 0x01, the only text encoding every v2.3 reader honours. */
    private fun utf16(text: String): ByteArray =
        text.toByteArray(Charsets.UTF_16LE).let {
            byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + it
        }

    private fun frame(out: ByteArrayOutputStream, id: String, payload: ByteArray) {
        out.write(id.toByteArray(Charsets.US_ASCII))
        // v2.3 frame sizes are plain 32-bit big-endian, not synchsafe.
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array())
        out.write(byteArrayOf(0, 0))
        out.write(payload)
    }

    private fun textFrame(out: ByteArrayOutputStream, id: String, text: String) =
        frame(out, id, utf16(text))

    private fun lyricsFrame(out: ByteArrayOutputStream, text: String) {
        val payload = ByteArrayOutputStream()
        payload.write(1) // UTF-16
        payload.write("eng".toByteArray(Charsets.US_ASCII))
        // Empty content descriptor: BOM followed by the two-byte terminator.
        payload.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0))
        // Encoding byte 1 promises UTF-16 with BOM, so the text carries its
        // own BOM too - without it readers misalign the first character.
        payload.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE))
        frame(out, "USLT", payload.toByteArray())
    }

    private fun pictureFrame(out: ByteArrayOutputStream, bytes: ByteArray, mime: String) {
        val payload = ByteArrayOutputStream()
        payload.write(0) // ISO-8859-1 for the MIME string and description below
        payload.write(mime.toByteArray(Charsets.ISO_8859_1))
        payload.write(0)
        payload.write(3) // front cover
        payload.write(0) // empty description
        payload.write(bytes)
        frame(out, "APIC", payload.toByteArray())
    }
}
