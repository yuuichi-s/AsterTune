package io.github.yuuichi_s.astertune.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.round
import kotlin.math.sin

/**
 * Integrated loudness tests using FFmpeg 8.0.1 reference values from the same signals,
 * written as 16-bit PCM from aevalsrc and measured with -af ebur128.
 */
class LoudnessMeterTest {
    @Test
    fun stereoSineMatchesFfmpeg() {
        // aevalsrc=0.1*sin(2*PI*1000*t)|0.1*sin(2*PI*1000*t):s=48000:d=10
        val loudness = measure(48_000, 2, 10.0) { 0.1 * sin(2 * PI * 1000 * it) }
        assertNotNull("Expected integrated loudness for the stereo sine", loudness)
        assertEquals(-20.000, loudness!!, TOLERANCE)
    }

    @Test
    fun relativeGateExcludesQuietPartLikeFfmpeg() {
        // The middle part is 20 dB below the first and is dropped by the relative gate.
        // aevalsrc=if(lt(t,3),0.5*sin(2*PI*1000*t),if(lt(t,9),0.05*sin(2*PI*1000*t),0.2*sin(2*PI*100*t)))
        // for both channels, s=44100:d=13
        val loudness = measure(44_100, 2, 13.0) {
            when {
                it < 3 -> 0.5 * sin(2 * PI * 1000 * it)
                it < 9 -> 0.05 * sin(2 * PI * 1000 * it)
                else -> 0.2 * sin(2 * PI * 100 * it)
            }
        }
        assertNotNull("Expected integrated loudness for the relative-gate signal", loudness)
        assertEquals(-9.343, loudness!!, TOLERANCE)
    }

    @Test
    fun monoHighFrequencySineMatchesFfmpeg() {
        // aevalsrc=0.3*sin(2*PI*5000*t):s=48000:d=5
        val loudness = measure(48_000, 1, 5.0) { 0.3 * sin(2 * PI * 5000 * it) }
        assertNotNull("Expected integrated loudness for the mono high-frequency sine", loudness)
        assertEquals(-10.150, loudness!!, TOLERANCE)
    }

    @Test
    fun resultDoesNotDependOnBufferSize() {
        val signal = { t: Double -> 0.1 * sin(2 * PI * 1000 * t) }
        val whole = measure(48_000, 2, 3.0, framesPerBuffer = Int.MAX_VALUE, signal = signal)
        val split = measure(48_000, 2, 3.0, framesPerBuffer = 333, signal = signal)
        assertNotNull("Whole-buffer input should produce integrated loudness", whole)
        assertNotNull("Split input should produce integrated loudness", split)
        assertEquals(whole!!, split!!, 1e-9)
    }

    @Test
    fun silenceHasNoResult() {
        assertNull(measure(48_000, 2, 5.0) { 0.0 })
    }

    @Test
    fun inputShorterThanOneBlockHasNoResult() {
        assertNull(measure(48_000, 2, 0.35) { 0.1 * sin(2 * PI * 1000 * it) })
    }

    private fun measure(
        sampleRate: Int,
        channelCount: Int,
        seconds: Double,
        framesPerBuffer: Int = 1024,
        signal: (Double) -> Double,
    ): Double? {
        val meter = LoudnessMeter(sampleRate, channelCount)
        val blocks = LoudnessBlocks()
        val totalFrames = (sampleRate * seconds).toInt()
        var frame = 0
        while (frame < totalFrames) {
            val frames = minOf(framesPerBuffer, totalFrames - frame)
            val buffer = ByteBuffer.allocate(frames * channelCount * 2).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames) {
                // Same conversion as ffmpeg's double to s16.
                val sample = round(signal((frame + it).toDouble() / sampleRate) * 32768)
                    .toInt().coerceIn(-32768, 32767).toShort()
                repeat(channelCount) { buffer.putShort(sample) }
            }
            buffer.flip()
            meter.process(buffer, blocks)
            assertEquals(0, buffer.position())
            frame += frames
        }
        return blocks.integratedLoudness()
    }

    private companion object {
        const val TOLERANCE = 0.1
    }
}
