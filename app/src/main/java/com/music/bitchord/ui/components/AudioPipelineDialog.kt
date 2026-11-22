package com.music.bitchord.ui.components

import android.media.AudioFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.AudioOutputStatus
import com.music.bitchord.playback.audio.bitperfect.BitPerfectOutput
import com.music.bitchord.playback.dsd.DsdFilter
import com.music.bitchord.playback.dsd.DsdFormat
import dev.chrisbanes.haze.HazeState
import java.util.Locale

/**
 * Whether decoded samples are reaching the output unaltered, and what is
 * altering them when they aren't.
 *
 * Worth stating outright rather than leaving to be inferred from the rows
 * above it, because it is the one claim in this dialog a listener might act
 * on, and because the app otherwise breaks bit-exactness whenever the
 * equaliser or spatial audio is switched on without saying so anywhere.
 *
 * There are two independent ways to lose it, and the ordering names the
 * *first* one in the signal chain, which is also the first thing someone
 * would change to get it back:
 *
 * 1. A DSP stage is running. Every one of these is a filter or a gain, and
 *    none of them can run and leave the output bit-exact.
 * 2. The output encoding cannot carry the source — a 24-bit stream on a route
 *    that will not open a float track, or 32-bit integer PCM, which no
 *    AudioTrack route can carry. [outputExact] is the sink's own verdict on
 *    that; see `PrecisionAudioSink.publishOutputExactness`.
 *
 * Exactness here stops at the app: Android's mixer may still resample what
 * leaves it. The exclusive output ([bitPerfect], set under Settings → Bit-perfect
 * output) is the one path where it reaches the DAC unchanged, and every DSP
 * stage is bypassed there, so only the source itself can make it inexact.
 */
private fun bitExactVerdict(
    dsd: Boolean,
    bitPerfect: BitPerfectOutput.Status,
    outputExact: Boolean,
    outputExactDetail: String?,
    loudnessActive: Boolean,
    eqActive: Boolean,
    spatialActive: Boolean,
): String = when {
    // Not a fault to fix: there is no DSD output path (native DSD or DoP)
    // here, so the PCM conversion is a deviation every DSD track carries,
    // whatever the stages after it do.
    dsd -> "No — DSD converted to PCM"
    bitPerfect.active && bitPerfect.dacLimited ->
        "No — ${bitPerfect.sourceBits}-bit / ${rateText(bitPerfect.sourceRateHz)} converted to " +
            "${bitPerfect.deviceName}'s max"
    // Every DSP stage is bypassed on the exclusive output, so the only
    // question left is whether the source itself was integer PCM.
    bitPerfect.active && bitPerfect.exact -> "Yes — bit-perfect to ${bitPerfect.deviceName}"
    bitPerfect.active -> "No — source isn't lossless PCM"
    loudnessActive -> "No — loudness normalization"
    eqActive -> "No — equalizer"
    spatialActive -> "No — spatial audio"
    outputExact -> "Yes${outputExactDetail?.let { " ($it)" }.orEmpty()}"
    else -> "No — ${outputExactDetail ?: "converted downstream"}"
}

/**
 * Full audio playback pipeline inspection surface, opened from the "Audio Pipeline"
 * row at the bottom of [com.music.bitchord.ui.player.AudioOutputSheet].
 *
 * Displays live, authoritative details for each stage in the audio pipeline:
 * Track Info -> Decoder -> Resampler -> DSP -> Output Device.
 *
 * Uses the divider-free player-panel shell. The live stage readout and
 * playback/bit-exact calculations are independent of its presentation.
 * The old static bus/node decoration is intentionally absent from this panel;
 * the live stage order and readout remain the signal-path explanation.
 */
@Composable
fun AudioPipelineDialog(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
) {
    val nerdStats by NerdStats.current.collectAsStateWithLifecycle()
    val outputStatus by AudioOutputStatus.current.collectAsStateWithLifecycle()
    val bitPerfect by BitPerfectOutput.status.collectAsStateWithLifecycle()

    val eqEnabled by AppSettings.equalizerEnabled.collectAsStateWithLifecycle()
    val eqPreset by AppSettings.equalizerPreset.collectAsStateWithLifecycle()
    val spatialAudio by AppSettings.spatialAudio.collectAsStateWithLifecycle()
    val loudnessNormalization by AppSettings.loudnessNormalization.collectAsStateWithLifecycle()

    StandardPlayerPanel(
        hazeState = hazeState,
        title = stringResource(R.string.audio_pipeline),
        subtitle = stringResource(R.string.audio_pipeline_subtitle),
        doneLabel = stringResource(R.string.done),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 1. Track Info Stage
            val sourceName = nerdStats?.sourceName ?: "—"
            // DSD is the one source whose own rate is not the rate the
            // rest of the pipeline runs at: DsdExtractor hands the
            // renderer 176.4 kHz PCM, so every stage after this one has
            // to be told that rate instead of the DSD bit rate.
            val dsdRateHz = nerdStats?.takeIf { it.mimeType?.startsWith("audio/dsd") == true }?.sampleRateHz
            val pcmRateHz = if (dsdRateHz != null) DsdFormat.PCM_RATE else nerdStats?.sampleRateHz
            val codec = NerdStats.codecLabel(nerdStats?.mimeType) ?: nerdStats?.mimeType ?: "—"
            val format = nerdStats?.container?.let { "$codec ($it)" } ?: codec
            // A lossy codec has no bit depth of its own; the decoder's PCM
            // depth is not the source's, so it is only shown for lossless.
            val bitDepth = if (nerdStats?.isLossless == true) {
                nerdStats?.bitDepth?.let { "$it-bit" }
                    ?: nerdStats?.claimed?.bitDepth?.let { "$it-bit" }
                    ?: "—"
            } else {
                "—"
            }
            val sampleRate = nerdStats?.sampleRateHz?.let(::rateText)
                ?: nerdStats?.claimed?.sampleRateHz?.let(::rateText)
                ?: "—"
            val bitrate = nerdStats?.bitrateKbps?.let { "$it kbps" } ?: "—"
            val pcmDataRate = nerdStats?.pcmDataRateKbps?.let { "$it kbps" } ?: "—"
            val channels = when (nerdStats?.channels) {
                1 -> stringResource(R.string.mono)
                2 -> stringResource(R.string.stereo)
                null -> "—"
                else -> "${nerdStats?.channels} (Surround)"
            }

            PipelineRule()
            PipelineSection(
                title = stringResource(R.string.pipeline_track_info),
            ) {
                PipelineRow(stringResource(R.string.pipeline_source), sourceName)
                PipelineRow(stringResource(R.string.pipeline_format), format)
                PipelineRow(stringResource(R.string.pipeline_bit_depth), bitDepth)
                PipelineRow(stringResource(R.string.pipeline_sample_rate), sampleRate)
                PipelineRow(stringResource(R.string.pipeline_bitrate), bitrate)
                PipelineRow(stringResource(R.string.pipeline_pcm_data_rate), pcmDataRate)
                PipelineRow(stringResource(R.string.pipeline_channels), channels)
            }

            // 2. Decoder Stage
            val decoderName = if (dsdRateHz != null) {
                "DSD to PCM (native FIR decimator)"
            } else {
                outputStatus.decoderName ?: "—"
            }

            PipelineRule()
            PipelineSection(
                title = stringResource(R.string.pipeline_decoder),
            ) {
                PipelineRow(stringResource(R.string.pipeline_decoder_name), decoderName)
                outputStatus.decoderOutputEncoding?.let {
                    val withRate = if (dsdRateHz != null) "$it / ${rateText(DsdFormat.PCM_RATE)}" else it
                    PipelineRow(stringResource(R.string.pipeline_format), withRate)
                }
            }

            // 3. Resampler Stage
            // For DSD this is the decimation DsdExtractor did: the one
            // step that actually changes the rate, so it is described
            // as what it is rather than as a resample, and its output
            // side is the PCM it produced, not the DSD rate.
            val inRate = dsdRateHz ?: nerdStats?.sampleRateHz
            val outRate = when {
                dsdRateHz != null -> DsdFormat.PCM_RATE
                bitPerfect.active -> bitPerfect.sampleRateHz
                else -> outputStatus.actualSampleRateHz
            }
            val isPassthrough = inRate != null && outRate != null && inRate == outRate
            val ioRateText = if (inRate != null && outRate != null) {
                "${rateText(inRate)} → ${rateText(outRate)}"
            } else if (inRate != null) {
                "${rateText(inRate)} → —"
            } else if (outRate != null) {
                "— → ${rateText(outRate)}"
            } else {
                "—"
            }
            val resamplerType = when {
                dsdRateHz != null ->
                    "FIR decimation ×${dsdRateHz / DsdFormat.PCM_RATE}, ${DsdFormat.tapsFor(dsdRateHz)} taps"
                inRate == null || outRate == null -> "—"
                isPassthrough -> "None"
                else -> "Resampler"
            }
            val cutoffText = if (dsdRateHz != null) rateText(DsdFilter.CUTOFF_HZ) else "—"
            val qualityText = when {
                dsdRateHz != null -> "DSD to PCM, full band below the cutoff"
                inRate == null || outRate == null -> "—"
                isPassthrough -> "Passthrough"
                else -> "Resampled"
            }

            PipelineRule()
            PipelineSection(
                title = if (dsdRateHz != null) "DSD to PCM" else stringResource(R.string.pipeline_resampler),
            ) {
                PipelineRow(stringResource(R.string.pipeline_io_rate), ioRateText)
                PipelineRow(stringResource(R.string.pipeline_type), resamplerType)
                PipelineRow(stringResource(R.string.pipeline_cutoff), cutoffText)
                PipelineRow(stringResource(R.string.pipeline_quality), qualityText)
            }

            // 4. DSP Stage
            val pcmFormat = outputStatus.dspFormat
            val dspRate = outputStatus.actualSampleRateHz ?: pcmRateHz
            val dspRateText = dspRate?.let(::rateText) ?: "—"
            val eqPresetText = if (eqEnabled) {
                eqPreset.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
            } else {
                "Flat"
            }
            val stereoExpandText = if (spatialAudio) "250%" else "100%"
            val buffersText = outputStatus.bufferSize?.let { size ->
                val rate = outputStatus.actualSampleRateHz
                val bytesPerSample = when (outputStatus.actualEncoding) {
                    AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                    else -> 2
                }
                val channelCount = nerdStats?.channels ?: 2
                val bytesPerFrame = bytesPerSample * channelCount
                val frames = if (bytesPerFrame > 0) size / bytesPerFrame else 0
                if (rate != null && rate > 0 && frames > 0) {
                    val ms = (frames * 1000L) / rate
                    "2x (${ms}ms, $frames frames)"
                } else {
                    "—"
                }
            } ?: "—"

            // Loudness reads before the rest of the DSP stage because
            // that is the order the samples meet them in — see
            // [DspChain] on why the level correction goes first.
            val loudnessGainText = when {
                !loudnessNormalization -> stringResource(R.string.off)
                outputStatus.loudnessGainDb == null -> "—"
                else -> stringResource(
                    R.string.loudness_gain_db,
                    "%+.1f".format(Locale.ROOT, outputStatus.loudnessGainDb),
                )
            }
            val loudnessMeasuredText = outputStatus.loudnessLufs?.let {
                stringResource(R.string.loudness_lufs, "%+.1f".format(Locale.ROOT, it))
            } ?: "—"

            PipelineRule()
            PipelineSection(
                title = stringResource(R.string.pipeline_dsp),
            ) {
                PipelineRow(stringResource(R.string.pipeline_pcm_format), pcmFormat)
                PipelineRow(stringResource(R.string.pipeline_sample_rate), dspRateText)
                PipelineRow(stringResource(R.string.pipeline_loudness_gain), loudnessGainText)
                PipelineRow(stringResource(R.string.pipeline_loudness_measured), loudnessMeasuredText)
                PipelineRow(stringResource(R.string.pipeline_eq_preset), eqPresetText)
                PipelineRow(stringResource(R.string.pipeline_stereo_expand), stereoExpandText)
                PipelineRow(stringResource(R.string.pipeline_buffers), buffersText)
                PipelineRow(stringResource(R.string.pipeline_output_api), outputStatus.sink.ifBlank { "AAudio" })
                // The verdict, rather than the settings. Names whichever
                // stage is altering samples — or, when none is, whether
                // the route can carry the decoder's own encoding —
                // instead of leaving the reader to infer it from four
                // rows above.
                PipelineRow(
                    stringResource(R.string.pipeline_bit_exact),
                    bitExactVerdict(
                        dsd = dsdRateHz != null,
                        bitPerfect = bitPerfect,
                        outputExact = outputStatus.outputExact,
                        outputExactDetail = outputStatus.outputExactDetail,
                        loudnessActive = loudnessNormalization && outputStatus.loudnessGainDb != null,
                        eqActive = eqEnabled,
                        spatialActive = spatialAudio,
                    ),
                )
            }

            // 5. Output Device Stage
            val deviceName = outputStatus.deviceName.ifBlank { "System default" }
            val audioTrackEncoding = when (outputStatus.actualEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> "Float32"
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
                AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
                AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
                else -> null
            }
            val audioTrackRate = outputStatus.actualSampleRateHz
            val audioTrackText = when {
                audioTrackEncoding != null && audioTrackRate != null ->
                    "$audioTrackEncoding / ${rateText(audioTrackRate)}"
                audioTrackEncoding != null -> audioTrackEncoding
                audioTrackRate != null -> rateText(audioTrackRate)
                else -> "—"
            }

            PipelineRule()
            PipelineSection(
                title = stringResource(R.string.pipeline_output_device),
            ) {
                PipelineRow(stringResource(R.string.pipeline_device_name), deviceName)
                PipelineRow("Route", outputStatus.routeKind.name)
                PipelineRow("Transport", bitPerfect.driver.takeIf { bitPerfect.active } ?: outputStatus.transportType.label)

                val directStatusText = when {
                    bitPerfect.active ->
                        "Active (${bitPerfect.driver}, bypasses mixer)"
                    outputStatus.directPlaybackActual ->
                        "Active (Direct AudioTrack, Bypasses Mixer)"
                    outputStatus.directPlaybackRejected ->
                        "Rejected"
                    outputStatus.directPlaybackSupported ->
                        "Supported (Framework Mixed)"
                    else ->
                        "Not Supported (Mixed Path)"
                }
                PipelineRow("Direct", directStatusText)
                PipelineRow(
                    if (bitPerfect.active) "Output" else "AudioTrack",
                    if (bitPerfect.active) "PCM${bitPerfect.outputBits} / ${rateText(bitPerfect.sampleRateHz)}" else audioTrackText,
                )

                val mixerText = when {
                    bitPerfect.active ->
                        if (bitPerfect.dacLimited) "Bypassed (DAC max)" else "Bypassed (bit-perfect)"
                    outputStatus.directPlaybackActual ->
                        "Direct path active; endpoint format not independently verified"
                    outputStatus.systemMixerRateHz != null -> {
                        val hal = outputStatus.halFormat
                        val mixerRate = outputStatus.systemMixerRateHz?.let(::rateText)
                        if (hal != null) {
                            "AudioFlinger Mixer $mixerRate, HAL $hal"
                        } else {
                            "AudioFlinger Mixer $mixerRate"
                        }
                    }
                    else -> null
                }
                mixerText?.let {
                    PipelineRow("System", it)
                }

                outputStatus.usbEndpointFormat?.let {
                    PipelineRow("USB Device Capability", formatUsbCapability(it))
                    PipelineNote("Reported by Android for the connected USB device. This describes device capabilities and may differ from the active playback format.")
                }

                if (outputStatus.routeKind == com.music.bitchord.playback.AudioRouting.Kind.BLUETOOTH) {
                    val bt = outputStatus.bluetoothTelemetry
                    outputStatus.bluetoothProfile?.let { PipelineRow("Bluetooth", it) }
                    if (bt != null && bt.isConnected) {
                        PipelineRow("Codec", if (bt.hasNamedCodec) bt.codecName else "System Managed")
                        bt.bitDepth?.let { PipelineRow("Codec Bits", "$it-bit") }
                        bt.sampleRateHz?.let { PipelineRow("Codec Sample Rate", rateText(it)) }
                        PipelineRow("Codec Bitrate", bt.bitrateLabel)
                        bt.mode?.let { PipelineRow("Codec Mode", it) }
                    } else {
                        PipelineRow("Codec", "System Managed")
                    }
                }

                if (outputStatus.fallbackReason != com.music.bitchord.playback.audio.FallbackReason.NONE) {
                    val fallbackText = outputStatus.fallbackDetail ?: outputStatus.fallbackReason.label
                    PipelineRow("Fallback", fallbackText)
                }
            }
        }
    }
}

/** One pipeline stage: its section title and live label/value rows. */
@Composable
private fun PipelineSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun PipelineRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.42f).padding(end = 12.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Hairline separator between stages, indented to align with the text column and clear the signal lane. */
@Composable
internal fun PipelineRule(modifier: Modifier = Modifier.padding(start = 52.dp, end = 16.dp)) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(androidx.compose.ui.graphics.Color.Transparent),
    )
}

/** Full-bleed closing action, [AlertAction]'s shape fixed to this screen's white-on-dark palette. */
@Composable
internal fun PipelineDoneAction(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            .background(
                if (pressed) Color.White.copy(alpha = 0.09f) else Color.Transparent,
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.W600,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun PipelineNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1.dp, bottom = 4.dp),
    )
}

/** 44100 -> "44.1 kHz", 2822400 -> "2.8224 MHz". */
private fun rateText(hz: Int): String =
    NerdStats.megahertzLabel(hz) ?: "${"%.1f".format(Locale.ROOT, hz / 1000f).removeSuffix(".0")} kHz"

internal fun formatUsbCapability(raw: String): String {
    var formatted = raw
        .replace("PCM32", "PCM 32-bit")
        .replace("PCM24", "PCM 24-bit")
        .replace("PCM16", "PCM 16-bit")
        .replace("Float32", "Float 32-bit")

    val hzRegex = Regex("""(\d+)\s*Hz""")
    formatted = hzRegex.replace(formatted) { matchResult ->
        val hz = matchResult.groupValues[1].toIntOrNull()
            ?: return@replace matchResult.value
        rateText(hz)
    }
    return formatted
}
