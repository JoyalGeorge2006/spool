package com.music.bitchord

import com.music.bitchord.download.Downloads
import com.music.bitchord.download.Mp3Exporter
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The already-MP3 export path writes the app's own ID3v2 and then copies the
 * source; a source carrying its own leading tag must lose it, or the file
 * ends up with two. Cases from id3.org/id3v2.4.0-structure: the size is
 * synchsafe and excludes header and footer; the footer exists only under
 * flag 0x10.
 */
class Mp3Id3SkipTest {

    private fun header(
        version: Int = 3,
        flags: Int = 0,
        size: Long = 0,
        corruptSize: Boolean = false,
    ): ByteArray {
        val h = ByteArray(10)
        h[0] = 'I'.code.toByte(); h[1] = 'D'.code.toByte(); h[2] = '3'.code.toByte()
        h[3] = version.toByte(); h[5] = flags.toByte()
        for (i in 0..3) {
            h[6 + i] = ((size shr (21 - 7 * i)) and 0x7F).toByte()
        }
        if (corruptSize) h[6] = 0x80.toByte()
        return h
    }

    @Test fun noTag() {
        val bytes = "not an mp3 header".toByteArray().copyOf(10)
        assertEquals(0L, Mp3Exporter.id3v2SkipLength(bytes))
    }

    @Test fun v23Plain() {
        // 10-byte header + 300-byte tag, no footer in v2.3.
        assertEquals(310L, Mp3Exporter.id3v2SkipLength(header(version = 3, size = 300)))
    }

    @Test fun v24WithFooter() {
        // Flag 0x10 adds the 10-byte footer to the skip.
        assertEquals(320L, Mp3Exporter.id3v2SkipLength(header(version = 4, flags = 0x10, size = 300)))
    }

    @Test fun v24FlagWithoutFooterMeaning() {
        // Any other flag (e.g. unsynchronisation 0x80) does not add a footer.
        assertEquals(310L, Mp3Exporter.id3v2SkipLength(header(version = 4, flags = 0x80.toByte().toInt() and 0xFF, size = 300)))
    }

    @Test fun malformedSize() {
        // A size byte >= 0x80 is not synchsafe: do not skip on a guess.
        assertEquals(0L, Mp3Exporter.id3v2SkipLength(header(corruptSize = true)))
    }

    @Test fun v23StrayBitIsNotAFooter() {
        // In v2.3 the 0x10 bit is not a footer flag: no extra 10 bytes.
        assertEquals(310L, Mp3Exporter.id3v2SkipLength(header(version = 3, flags = 0x10, size = 300)))
    }

    @Test fun copyRefusesEmptyFile() {
        val out = java.io.ByteArrayOutputStream()
        try {
            Mp3Exporter.copyAudioBytes(java.io.ByteArrayInputStream(ByteArray(0)), out)
            fail("expected refusal")
        } catch (e: IllegalStateException) { /* zero audio */ }
    }

    @Test fun copyRefusesTagOnlyFile() {
        // A v2.4 tag whose declared size consumes exactly the whole file.
        val out = java.io.ByteArrayOutputStream()
        try {
            Mp3Exporter.copyAudioBytes(java.io.ByteArrayInputStream(header(version = 4, flags = 0, size = 300) + ByteArray(300)), out)
            fail("expected refusal")
        } catch (e: IllegalStateException) { /* zero audio */ }
    }

    @Test fun copyRefusesTruncatedTag() {
        val out = java.io.ByteArrayOutputStream()
        try {
            Mp3Exporter.copyAudioBytes(java.io.ByteArrayInputStream(header(version = 4, flags = 0, size = 300) + ByteArray(50)), out)
            fail("expected refusal")
        } catch (e: IllegalStateException) { /* tag outlives file */ }
    }

    @Test fun copySkipsTagAndKeepsAudio() {
        val audio = ByteArray(100) { 0x11 }
        val out = java.io.ByteArrayOutputStream()
        Mp3Exporter.copyAudioBytes(java.io.ByteArrayInputStream(header(version = 4, flags = 0x10, size = 10) + ByteArray(20) + audio), out)
        assertEquals(100, out.size())
    }

    @Test fun adoptExtFromDisplayName() {
        assertEquals("mp3", Downloads.adoptedExtension("Song Name.MP3", "content", "12345"))
        assertEquals("flac", Downloads.adoptedExtension("a.flac", "content", "999"))
    }

    @Test fun adoptExtContentUriNeverUsesRowId() {
        assertEquals("", Downloads.adoptedExtension(null, "content", "12345"))
    }

    @Test fun adoptExtFileUriFallback() {
        assertEquals("mp3", Downloads.adoptedExtension(null, "file", "track.mp3"))
    }

    @Test fun unsupportedVersion() {
        assertEquals(0L, Mp3Exporter.id3v2SkipLength(header(version = 5, size = 300)))
    }

    @Test fun shortHeader() {
        assertEquals(0L, Mp3Exporter.id3v2SkipLength(ByteArray(5)))
    }

    @Test fun zeroSizeTag() {
        // A bare 10-byte header with an empty tag still gets skipped.
        assertEquals(10L, Mp3Exporter.id3v2SkipLength(header(version = 3, size = 0)))
    }
}
