/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

/**
 * K-weights 16-bit interleaved PCM and reduces it to the mean square energy of each 100 ms
 * sub-block for ITU-R BS.1770-4 integrated loudness measurement.
 */
class LoudnessMeter(sampleRate: Int, private val channelCount: Int) {
    private val weights = DoubleArray(channelCount) { channelWeight(it, channelCount) }
    private val subBlockFrames = max(1, sampleRate / 10)

    // Filter order:
    // 1. High shelf
    // 2. RLB high-pass
    // Coefficients as in libebur128.
    private val shelfB0: Double
    private val shelfB1: Double
    private val shelfB2: Double
    private val shelfA1: Double
    private val shelfA2: Double
    private val highPassA1: Double
    private val highPassA2: Double

    private val shelfZ1 = DoubleArray(channelCount)
    private val shelfZ2 = DoubleArray(channelCount)
    private val highPassZ1 = DoubleArray(channelCount)
    private val highPassZ2 = DoubleArray(channelCount)

    private val channelSums = DoubleArray(channelCount)
    private var samples = ShortArray(0)
    private var framesInSubBlock = 0

    init {
        val shelfK = tan(PI * 1681.974450955533 / sampleRate)
        val shelfQ = 0.7071752369554196
        val vh = 10.0.pow(3.999843853973347 / 20)
        val vb = vh.pow(0.4996667741545416)
        val shelfA0 = 1 + shelfK / shelfQ + shelfK * shelfK
        shelfB0 = (vh + vb * shelfK / shelfQ + shelfK * shelfK) / shelfA0
        shelfB1 = 2 * (shelfK * shelfK - vh) / shelfA0
        shelfB2 = (vh - vb * shelfK / shelfQ + shelfK * shelfK) / shelfA0
        shelfA1 = 2 * (shelfK * shelfK - 1) / shelfA0
        shelfA2 = (1 - shelfK / shelfQ + shelfK * shelfK) / shelfA0

        val highPassK = tan(PI * 38.13547087602444 / sampleRate)
        val highPassQ = 0.5003270373238773
        val highPassA0 = 1 + highPassK / highPassQ + highPassK * highPassK
        highPassA1 = 2 * (highPassK * highPassK - 1) / highPassA0
        highPassA2 = (1 - highPassK / highPassQ + highPassK * highPassK) / highPassA0
    }

    /**
     * Measures the whole frames between [buffer]'s position and limit without moving its position,
     * and adds the energy of each completed sub-block to [out].
     */
    fun process(buffer: ByteBuffer, out: LoudnessBlocks) {
        val totalFrames = buffer.remaining() / (2 * channelCount)
        if (totalFrames == 0) return
        val sampleCount = totalFrames * channelCount
        if (samples.size < sampleCount) samples = ShortArray(sampleCount)
        buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples, 0, sampleCount)

        var frame = 0
        while (frame < totalFrames) {
            val frames = minOf(totalFrames - frame, subBlockFrames - framesInSubBlock)
            for (channel in 0 until channelCount) {
                channelSums[channel] += filter(channel, frame * channelCount + channel, frames)
            }
            frame += frames
            framesInSubBlock += frames
            if (framesInSubBlock == subBlockFrames) {
                var energy = 0.0
                for (channel in 0 until channelCount) {
                    energy += weights[channel] * channelSums[channel] / subBlockFrames
                    channelSums[channel] = 0.0
                }
                out.add(energy)
                framesInSubBlock = 0
            }
        }
    }

    /** K-weights [frames] samples of [channel] from [start], returning the sum of their squares. */
    private fun filter(channel: Int, start: Int, frames: Int): Double {
        val samples = samples
        val b0 = shelfB0
        val b1 = shelfB1
        val b2 = shelfB2
        val a1 = shelfA1
        val a2 = shelfA2
        val hpA1 = highPassA1
        val hpA2 = highPassA2
        var s1 = shelfZ1[channel]
        var s2 = shelfZ2[channel]
        var h1 = highPassZ1[channel]
        var h2 = highPassZ2[channel]
        var sum = 0.0
        var index = start
        repeat(frames) {
            val x = samples[index] / 32768.0
            index += channelCount

            val shelfOut = b0 * x + s1
            s1 = b1 * x - a1 * shelfOut + s2
            s2 = b2 * x - a2 * shelfOut

            val y = shelfOut + h1
            h1 = -2 * shelfOut - hpA1 * y + h2
            h2 = shelfOut - hpA2 * y

            sum += y * y
        }
        shelfZ1[channel] = s1
        shelfZ2[channel] = s2
        highPassZ1[channel] = h1
        highPassZ2[channel] = h2
        return sum
    }

    /** Drops the unfinished sub-block so that the next frames start a new one. */
    fun discardPartialSubBlock() {
        channelSums.fill(0.0)
        framesInSubBlock = 0
    }

    /** Clears the filter history as well, for input that does not continue the previous frames. */
    fun reset() {
        discardPartialSubBlock()
        shelfZ1.fill(0.0)
        shelfZ2.fill(0.0)
        highPassZ1.fill(0.0)
        highPassZ2.fill(0.0)
    }

    private companion object {
        // Channel order of 5.1 PCM on Android: FL, FR, FC, LFE, BL, BR.
        // The LFE channel is excluded.
        fun channelWeight(channel: Int, channelCount: Int): Double = when {
            channelCount >= 6 && channel == 3 -> 0.0
            channelCount >= 5 && channel >= 3 -> 1.41
            else -> 1.0
        }
    }
}

/** Sub-block energies of one measured section, and its integrated loudness. */
class LoudnessBlocks {
    private var energies = DoubleArray(1024)
    var size = 0
        private set

    /**
     * Adds a 100 ms sub-block's energy: the sum over channels of each channel's weight times the
     * mean square of its K-weighted samples.
     */
    fun add(energy: Double) {
        if (size == energies.size) energies = energies.copyOf(size * 2)
        energies[size++] = energy
    }

    /**
     * Gated integrated loudness in LUFS over 400 ms blocks overlapping by 75 %, or null when no
     * block passes the gates (e.g. the section is silent or shorter than 400 ms).
     */
    fun integratedLoudness(): Double? {
        val blockCount = size - 3
        if (blockCount <= 0) return null
        val blocks = DoubleArray(blockCount) { i ->
            (energies[i] + energies[i + 1] + energies[i + 2] + energies[i + 3]) / 4
        }

        val absoluteGate = energyOf(ABSOLUTE_GATE_LUFS)
        val relativeGate = energyOf(
            loudnessOf(meanAbove(blocks, absoluteGate) ?: return null) - RELATIVE_GATE_OFFSET_LU
        )
        return loudnessOf(meanAbove(blocks, max(absoluteGate, relativeGate)) ?: return null)
    }

    private fun meanAbove(blocks: DoubleArray, threshold: Double): Double? {
        var sum = 0.0
        var count = 0
        for (energy in blocks) {
            if (energy > threshold) {
                sum += energy
                count++
            }
        }
        return if (count == 0) null else sum / count
    }

    private fun loudnessOf(energy: Double) =
        LUFS_CALIBRATION_OFFSET + 10 * log10(energy)

    private fun energyOf(loudness: Double) =
        10.0.pow((loudness - LUFS_CALIBRATION_OFFSET) / 10)

    private companion object {
        const val ABSOLUTE_GATE_LUFS = -70.0
        const val RELATIVE_GATE_OFFSET_LU = 10.0
        const val LUFS_CALIBRATION_OFFSET = -0.691
    }
}
