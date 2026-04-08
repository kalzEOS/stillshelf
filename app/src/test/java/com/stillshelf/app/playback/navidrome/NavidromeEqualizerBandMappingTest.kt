package com.stillshelf.app.playback.navidrome

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class NavidromeEqualizerBandMappingTest {

    @Test
    fun directGainProcessorRaisesSignalWhenAllBandsAreBoosted() {
        val processor = NavidromeEqualizerAudioProcessor().apply {
            updateSettings(
                enabled = true,
                bandLevelsDb = List(10) { 6f }
            )
        }
        processor.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        val inputSamples = createSineWaveSamples(
            sampleRateHz = 44_100,
            frequencyHz = 1_000.0,
            sampleCount = 4_096,
            amplitude = 0.08
        )

        processor.queueInput(createPcm16Buffer(inputSamples))
        processor.queueEndOfStream()
        val outputSamples = readPcm16Samples(processor.getOutput())

        assertTrue(outputSamples.isNotEmpty())
        assertTrue(rms(outputSamples) > rms(inputSamples) * 1.6f)
    }

    @Test
    fun coefficientBuilderSkipsFlatBands() {
        assertTrue(
            buildNavidromeEqualizerCoefficients(
                sampleRateHz = 44_100,
                bandLevelsDb = List(10) { 0f }
            ).isEmpty()
        )
    }

    @Test
    fun coefficientBuilderPreservesDirectBandShape() {
        val coefficients = buildNavidromeEqualizerCoefficients(
            sampleRateHz = 44_100,
            bandLevelsDb = listOf(6f, 0f, -3f, 0f, 2f, 0f, 0f, 0f, 0f, 0f)
        )

        assertEquals(3, coefficients.size)
    }

    @Test
    fun softLimiterOnlyTouchesPeaksNearClipping() {
        assertEquals(0.5f, applyNavidromeSoftLimiter(0.5f), 0.0001f)
        assertTrue(applyNavidromeSoftLimiter(1.2f) <= 1f)
        assertTrue(applyNavidromeSoftLimiter(-1.2f) >= -1f)
        assertTrue(applyNavidromeSoftLimiter(0.98f) < 0.98f)
    }

    private fun createSineWaveSamples(
        sampleRateHz: Int,
        frequencyHz: Double,
        sampleCount: Int,
        amplitude: Double
    ): FloatArray {
        return FloatArray(sampleCount) { index ->
            (sin(2.0 * PI * frequencyHz * index / sampleRateHz) * amplitude).toFloat()
        }
    }

    private fun createPcm16Buffer(samples: FloatArray): ByteBuffer {
        return ByteBuffer.allocateDirect(samples.size * 2)
            .order(ByteOrder.nativeOrder())
            .apply {
                samples.forEach { sample ->
                    val clamped = sample.coerceIn(-1f, 1f)
                    val pcm16 = if (clamped <= -1f) {
                        Short.MIN_VALUE
                    } else {
                        (clamped * Short.MAX_VALUE.toFloat()).toInt().toShort()
                    }
                    putShort(pcm16)
                }
                flip()
            }
    }

    private fun readPcm16Samples(buffer: ByteBuffer): FloatArray {
        val copy = buffer.order(ByteOrder.nativeOrder())
        val sampleCount = copy.remaining() / 2
        return FloatArray(sampleCount) { index ->
            copy.getShort(index * 2).toFloat() / Short.MAX_VALUE.toFloat()
        }
    }

    private fun rms(samples: FloatArray): Float {
        val meanSquare = samples.fold(0.0) { sum, sample ->
            sum + sample * sample
        } / samples.size.coerceAtLeast(1)
        return kotlin.math.sqrt(meanSquare).toFloat()
    }
}
