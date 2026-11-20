package com.music.bitchord.download

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * The "Save MP3 copy" export: every finished download is also written as a
 * real `.mp3` into the folder the listener picked in settings.
 *
 * Runs after the download's own commit, never instead of it — an export that
 * fails has already lost nothing but the copy, and that failure is logged
 * rather than charged against the download. The folder is a Storage Access
 * Framework tree, so no storage permission is needed on any Android version:
 * the write grant arrived with the folder the user picked.
 *
 * A track that is already MP3 (a source can serve one — the byte probe in
 * [Downloader] files it as such) is copied untouched. Everything else —
 * AAC-in-MP4 from YouTube, Opus in WebM, FLAC from a lossless source — is
 * decoded to PCM by the platform's decoders and encoded to MP3 by the bundled
 * LAME, since Android ships no MP3 encoder of its own.
 */
internal object Mp3Exporter {

    private const val TAG = "BitChord"

    /** CBR at the top of MP3's useful range; the source is usually a 129-258kbps lossy stream. */
    private const val BITRATE_KBPS = 320

    /** Samples handed to LAME per call; a few hundred milliseconds of stereo. */
    private const val PCM_CHUNK_SHORTS = 16384

    /**
     * Whether an export can be attempted at all right now: the setting is on
     * and a folder has been picked. Checked by [Downloads] before it spends
     * anything on one.
     */
    fun ready(context: Context): Boolean =
        AppSettings.mp3Export.value &&
            AppSettings.mp3ExportFolder.value.isNotBlank() &&
            hasFolderAccess(context)

    /**
     * The picked folder only counts while its SAF grant actually survives -
     * a revoked permission or a deleted tree turns every export into the
     * "no longer accessible" failure, so the offer itself must go quiet.
     */
    private fun hasFolderAccess(context: Context): Boolean {
        val tree = AppSettings.mp3ExportFolder.value
        return context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == tree && it.isWritePermission
        }
    }

    /**
     * Write [track] as `Artist - Title.mp3` into the chosen folder, decoded
     * and re-encoded from [source] unless [source] already is an MP3.
     *
     * @param playlist the release this download was part of, when it was one —
     *   used only for the log line; all exports land flat in the chosen folder.
     * @return the exported document's uri, or null when the copy already
     *   existed. Throws when the export itself could not be completed.
     */
    suspend fun export(
        context: Context,
        track: Song,
        source: Uri,
        extension: String,
        playlist: String?,
        lyrics: LyricsTag.Embeddable?,
        artwork: MediaTagger.Artwork?,
        onProgress: (Float) -> Unit,
    ): Uri? = withContext(Dispatchers.IO) {
        val tree = AppSettings.mp3ExportFolder.value
        check(tree.isNotBlank()) { "No MP3 export folder chosen" }
        val root = DocumentFile.fromTreeUri(context, Uri.parse(tree))
            ?: error("The MP3 export folder is no longer accessible — pick it again in settings")

        val name = DownloadStore.fileNameFor(track, "mp3")
        root.findFile(name)?.let { existing ->
            Log.d(TAG, "$name already has an MP3 copy; keeping it")
            return@withContext existing.uri
        }

        Log.d(TAG, "exporting ${track.videoId} as $name" + (playlist?.let { " ($it)" } ?: ""))
        val document = root.createFile("audio/mpeg", name.substringBeforeLast('.'))
            ?: error("Could not create $name in the MP3 export folder")

        try {
            context.contentResolver.openOutputStream(document.uri, "w")?.use { out ->
                out.write(Mp3Tagger.tag(track, lyrics, artwork))
                if (extension == "mp3") {
                    copyBytes(context, source, out)
                } else {
                    transcode(context, source, out, onProgress)
                }
            } ?: error("Could not open $name for writing")
        } catch (e: Throwable) {
            // Same rule as DownloadStore's pending row: a failed export must
            // not leave a partial file that looks like a finished one.
            runCatching { document.delete() }
            throw e
        }
        document.uri
    }

    /**
     * Bytes to skip past a leading ID3v2 tag, header included, or 0 when the
     * 10 [header] bytes are not a well-formed ID3v2 header. The size field is
     * synchsafe (each byte < 0x80) and excludes the header and the footer; a
     * footer exists only when flag 0x10 is set (v2.4). Kept pure for tests.
     */
    internal fun id3v2SkipLength(header: ByteArray): Long {
        if (header.size < 10) return 0L
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return 0L
        val version = header[3].toInt() and 0xFF
        if (version !in 2..4) return 0L
        val sizeBytes = header.sliceArray(6..9)
        if (sizeBytes.any { it.toInt() and 0x80 != 0 }) return 0L
        val size = sizeBytes.fold(0L) { acc, b -> (acc shl 7) or (b.toInt() and 0x7F).toLong() }
        // The footer is a v2.4 feature; in v2.2/v2.3 the 0x10 bit is not a
        // footer flag, so it must never extend the skip.
        val hasFooter = version == 4 && header[5].toInt() and 0x10 != 0
        return 10 + size + (if (hasFooter) 10 else 0)
    }

    private fun copyBytes(context: Context, source: Uri, out: OutputStream) {
        val input: InputStream = context.contentResolver.openInputStream(source)
            ?: error("Could not read the downloaded file")
        input.use { copyAudioBytes(it, out) }
    }

    /**
     * Copies [raw] to [out], skipping a leading ID3v2 tag and refusing any
     * source that would yield zero audio bytes (an ID3 header that consumes
     * exactly the whole file, or no audio at all). Internal for tests.
     */
    internal fun copyAudioBytes(raw: InputStream, out: OutputStream) {
        val stream = if (raw is BufferedInputStream) raw else BufferedInputStream(raw)
        // The app's own ID3v2 was just written ahead of this copy, so a
        // source that already starts with one must not bring a second
        // tag along - skip it (id3.org/id3v2.4.0-structure: 10-byte
        // header, synchsafe size excluding header and footer, footer
        // only when flag 0x10 is set).
        stream.mark(10)
        val header = ByteArray(10)
        var filled = 0
        while (filled < 10) {
            val n = stream.read(header, filled, 10 - filled)
            if (n <= 0) break
            filled += n
        }
        val skip = if (filled == 10) id3v2SkipLength(header) else 0L
        if (skip > 0) {
            var remaining = skip - 10 // the header bytes are already read
            while (remaining > 0) {
                val skipped = stream.skip(remaining)
                if (skipped <= 0) break
                remaining -= skipped
            }
            // A tag that claims more bytes than the file has would export
            // a valid-looking MP3 with zero audio - refuse it instead.
            check(remaining <= 0) { "Source ends inside its own ID3 tag" }
        } else {
            stream.reset()
        }
        val copied = stream.copyTo(out)
        check(copied > 0) { "Source has no audio bytes" }
    }

    /**
     * Decode [source] to PCM with the platform's decoders and encode it to
     * MP3 with LAME, streaming: at no point is a whole track's PCM in memory.
     *
     * The encoder is only opened once the decoder states its output format —
     * the sample rate and channel count of the *decoded* stream, which the
     * container's own track format does not always state truthfully.
     */
    private suspend fun transcode(context: Context, source: Uri, out: OutputStream, onProgress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, source, null)
        var trackIndex = -1
        var mime = ""
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (trackMime.startsWith("audio/")) {
                trackIndex = i
                mime = trackMime
                break
            }
        }
        check(trackIndex >= 0) { "No audio track in the downloaded file" }
        extractor.selectTrack(trackIndex)
        val durationUs = extractor.getTrackFormat(trackIndex)
            .getLong(MediaFormat.KEY_DURATION, 0L)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(extractor.getTrackFormat(trackIndex), null, null, 0)
        decoder.start()

        var encoder: Mp3Encoder.Session? = null
        try {
            val info = MediaCodec.BufferInfo()
            val pcm = ShortArray(PCM_CHUNK_SHORTS)
            var pcmCount = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = decoder.outputFormat
                    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    // Two-arg getInteger is API 29; below it every audio
                    // decoder here emits 16-bit anyway.
                    val pcmEncoding = if (android.os.Build.VERSION.SDK_INT >= 29) {
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    } else {
                        android.media.AudioFormat.ENCODING_PCM_16BIT
                    }
                    check(pcmEncoding == android.media.AudioFormat.ENCODING_PCM_16BIT) {
                        "Decoder produced an unexpected PCM format"
                    }
                    encoder = Mp3Encoder.Session(sampleRate, channels, BITRATE_KBPS)
                } else if (outIndex >= 0) {
                    val buffer = decoder.getOutputBuffer(outIndex)!!
                    if (info.size > 0) {
                        // The codec owns the whole buffer; only
                        // [offset, offset+size) of it is this output.
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val shorts = buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shorts.hasRemaining()) {
                            val take = minOf(shorts.remaining(), PCM_CHUNK_SHORTS - pcmCount)
                            shorts.get(pcm, pcmCount, take)
                            pcmCount += take
                            if (pcmCount == PCM_CHUNK_SHORTS) {
                                out.write(encoder!!.encode(pcm, pcmCount))
                                pcmCount = 0
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (durationUs > 0 && info.presentationTimeUs > 0) {
                        onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                    }
                }
            }

            val session = encoder ?: error("The decoder never stated an output format")
            if (pcmCount > 0) out.write(session.encode(pcm, pcmCount))
            out.write(session.flush())
        } finally {
            encoder?.close()
            runCatching { decoder.stop() }
            decoder.release()
            extractor.release()
        }
    }
}
