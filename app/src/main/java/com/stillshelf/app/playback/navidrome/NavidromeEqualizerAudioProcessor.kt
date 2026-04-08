package com.stillshelf.app.playback.navidrome

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.stillshelf.app.core.model.NAVIDROME_EQUALIZER_MAX_DB
import com.stillshelf.app.core.model.NAVIDROME_EQUALIZER_MIN_DB
import com.stillshelf.app.core.model.NAVIDROME_EQUALIZER_STEP_DB
import com.stillshelf.app.core.model.navidromeEqualizerBandFrequenciesHz
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

private const val NAVIDROME_EQUALIZER_BAND_Q = 1.4142135f
private const val MIN_AUDIBLE_GAIN_DB = 0.001f
private const val NAVIDROME_EQUALIZER_LIMITER_THRESHOLD = 0.92f

internal data class NavidromeBiquadCoefficients(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float
)

internal fun buildNavidromeEqualizerCoefficients(
    sampleRateHz: Int,
    bandLevelsDb: List<Float>
): List<NavidromeBiquadCoefficients> {
    if (sampleRateHz <= 0) return emptyList()
    val nyquistHz = sampleRateHz / 2f
    return navidromeEqualizerBandFrequenciesHz.mapIndexedNotNull { index, frequencyHz ->
        val gainDb = bandLevelsDb.getOrElse(index) { 0f }
            .coerceIn(NAVIDROME_EQUALIZER_MIN_DB, NAVIDROME_EQUALIZER_MAX_DB)
            .let { level -> (level / NAVIDROME_EQUALIZER_STEP_DB).roundToInt().toFloat() * NAVIDROME_EQUALIZER_STEP_DB }
        if (abs(gainDb) < MIN_AUDIBLE_GAIN_DB) {
            return@mapIndexedNotNull null
        }
        val clampedFrequencyHz = frequencyHz.coerceAtMost((nyquistHz * 0.95f).toInt()).toFloat()
        if (clampedFrequencyHz <= 0f || clampedFrequencyHz >= nyquistHz) {
            return@mapIndexedNotNull null
        }
        val amplitude = 10.0.pow(gainDb / 40.0).toFloat()
        val omega = (2.0 * PI * clampedFrequencyHz / sampleRateHz).toFloat()
        val sinOmega = sin(omega)
        if (abs(sinOmega) < 1e-9f) {
            return@mapIndexedNotNull null
        }
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2f * NAVIDROME_EQUALIZER_BAND_Q)

        val rawB0 = 1f + alpha * amplitude
        val rawB1 = -2f * cosOmega
        val rawB2 = 1f - alpha * amplitude
        val rawA0 = 1f + alpha / amplitude
        val rawA1 = -2f * cosOmega
        val rawA2 = 1f - alpha / amplitude

        if (abs(rawA0) < 1e-9f) {
            return@mapIndexedNotNull null
        }

        NavidromeBiquadCoefficients(
            b0 = rawB0 / rawA0,
            b1 = rawB1 / rawA0,
            b2 = rawB2 / rawA0,
            a1 = rawA1 / rawA0,
            a2 = rawA2 / rawA0
        )
    }
}

internal fun applyNavidromeSoftLimiter(sample: Float): Float {
    val absoluteSample = abs(sample)
    if (absoluteSample <= NAVIDROME_EQUALIZER_LIMITER_THRESHOLD) {
        return sample
    }
    val limitedSpan = 1f - NAVIDROME_EQUALIZER_LIMITER_THRESHOLD
    if (limitedSpan <= 0f) {
        return sample.coerceIn(-1f, 1f)
    }
    val normalizedExcess = ((absoluteSample - NAVIDROME_EQUALIZER_LIMITER_THRESHOLD) / limitedSpan)
        .coerceAtLeast(0f)
    val curvedExcess = tanh(normalizedExcess.toDouble()).toFloat()
    val limitedSample = NAVIDROME_EQUALIZER_LIMITER_THRESHOLD + (limitedSpan * curvedExcess)
    return limitedSample.coerceAtMost(1f) * sample.sign()
}

private fun Float.sign(): Float = if (this < 0f) -1f else 1f

@UnstableApi
internal class NavidromeEqualizerAudioProcessor : BaseAudioProcessor() {
    private val settingsLock = Any()

    @Volatile
    private var pendingSettingsVersion: Long = 0L

    @Volatile
    private var pendingEnabled: Boolean = false

    @Volatile
    private var pendingBandLevelsDb: FloatArray = FloatArray(navidromeEqualizerBandFrequenciesHz.size)

    private var appliedSettingsVersion: Long = -1L
    private var appliedSampleRateHz: Int = 0
    private var appliedChannelCount: Int = 0
    private var coefficients: List<NavidromeBiquadCoefficients> = emptyList()
    private var z1: Array<FloatArray> = emptyArray()
    private var z2: Array<FloatArray> = emptyArray()

    fun updateSettings(enabled: Boolean, bandLevelsDb: List<Float>) {
        synchronized(settingsLock) {
            pendingEnabled = enabled
            pendingBandLevelsDb = FloatArray(navidromeEqualizerBandFrequenciesHz.size) { index ->
                bandLevelsDb.getOrElse(index) { 0f }
                    .coerceIn(NAVIDROME_EQUALIZER_MIN_DB, NAVIDROME_EQUALIZER_MAX_DB)
                    .let { level -> (level / NAVIDROME_EQUALIZER_STEP_DB).roundToInt().toFloat() * NAVIDROME_EQUALIZER_STEP_DB }
            }
            pendingSettingsVersion++
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }
        ensureProcessingState()

        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())

        if (coefficients.isEmpty()) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        var sampleIndex = 0
        while (inputBuffer.hasRemaining()) {
            val channelIndex = sampleIndex % channelCount
            var sample = inputBuffer.short.toFloat() / Short.MAX_VALUE.toFloat()
            val channelZ1 = z1[channelIndex]
            val channelZ2 = z2[channelIndex]
            coefficients.forEachIndexed { bandIndex, coeff ->
                val processed = coeff.b0 * sample + channelZ1[bandIndex]
                channelZ1[bandIndex] = coeff.b1 * sample - coeff.a1 * processed + channelZ2[bandIndex]
                channelZ2[bandIndex] = coeff.b2 * sample - coeff.a2 * processed
                sample = processed
            }
            val clampedSample = applyNavidromeSoftLimiter(sample)
            val pcm16 = if (clampedSample <= -1f) {
                Short.MIN_VALUE
            } else {
                (clampedSample * Short.MAX_VALUE.toFloat()).roundToInt().toShort()
            }
            outputBuffer.putShort(pcm16)
            sampleIndex++
        }
        outputBuffer.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        super.onFlush(streamMetadata)
        resetFilterState()
    }

    override fun onReset() {
        coefficients = emptyList()
        appliedSettingsVersion = -1L
        appliedSampleRateHz = 0
        appliedChannelCount = 0
        z1 = emptyArray()
        z2 = emptyArray()
    }

    private fun ensureProcessingState() {
        synchronized(settingsLock) {
            val sampleRateHz = inputAudioFormat.sampleRate
            val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
            if (
                appliedSettingsVersion == pendingSettingsVersion &&
                appliedSampleRateHz == sampleRateHz &&
                appliedChannelCount == channelCount
            ) {
                return
            }

            coefficients = if (pendingEnabled) {
                buildNavidromeEqualizerCoefficients(
                    sampleRateHz = sampleRateHz,
                    bandLevelsDb = pendingBandLevelsDb.asList()
                )
            } else {
                emptyList()
            }
            appliedSettingsVersion = pendingSettingsVersion
            appliedSampleRateHz = sampleRateHz
            appliedChannelCount = channelCount
            resetFilterState()
        }
    }

    private fun resetFilterState() {
        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        z1 = Array(channelCount) { FloatArray(coefficients.size) }
        z2 = Array(channelCount) { FloatArray(coefficients.size) }
    }
}
