/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback.audio

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import io.github.yuuichi_s.astertune.BuildConfig
import java.nio.ByteBuffer

/** Integrated loudness of one section of a media item, measured without gaps. */
data class LoudnessMeasurement(
    val mediaItem: MediaItem,
    /** Duration of the measured media period, or [C.TIME_UNSET]. */
    val durationUs: Long,
    val measuredUs: Long,
    /** Whether the section began near the media period's start and reached stream end. */
    val complete: Boolean,
    val integratedLufs: Double,
)

/**
 * Measures integrated loudness for each section of a media item.
 *
 * The audio processor only sees PCM. Renderer and sink wrappers identify each buffer's
 * stream using renderer-time timestamps and stream start positions.
 *
 * A section ends on a stream change, sink flush or reset (seeks, disabling), or playback end.
 * [Listener] callbacks run on the playback thread.
 */
@OptIn(UnstableApi::class)
class LoudnessTracker(private val listener: Listener) {
    /** Receives measurement section boundaries and loudness results on the playback thread. */
    interface Listener {
        /**
         * Reports the start of a section for [windowSequenceNumber].
         *
         * Results of the sections before it have already been passed to [onMeasured].
         */
        fun onSectionStarted(windowSequenceNumber: Long)

        /**
         * Reports the result of a section when it ends, including a section that does not cover
         * the whole media item. Not called when the media item is unknown or no block passes the
         * gates.
         */
        fun onMeasured(measurement: LoudnessMeasurement)
    }

    val audioProcessor: BaseAudioProcessor = MeasuringAudioProcessor()

    // Everything below is used on the playback thread only.
    private var timeline: Timeline = Timeline.EMPTY
    private val streams = ArrayDeque<Stream>()
    private var nextStream: Stream? = null
    private var nextPositionUs = 0L

    private var meter: LoudnessMeter? = null
    private var meterFormat: AudioFormat = AudioFormat.NOT_SET
    private var section: Section? = null

    fun wrapRenderer(renderer: Renderer): Renderer = TrackingRenderer(renderer)

    fun wrapSink(sink: AudioSink): AudioSink = TrackingSink(sink)

    private class Stream(
        val mediaPeriodId: MediaSource.MediaPeriodId,
        val startPositionUs: Long,
        val offsetUs: Long,
    )

    private class Section(
        val stream: Stream,
        val mediaItem: MediaItem?,
        val durationUs: Long,
        val fromStart: Boolean,
    ) {
        val blocks = LoudnessBlocks()
        var measuredUs = 0.0
        var processingNs = 0L
    }

    private fun register(mediaPeriodId: MediaSource.MediaPeriodId, startPositionUs: Long, offsetUs: Long) {
        streams.addLast(Stream(mediaPeriodId, startPositionUs, offsetUs))
        while (streams.size > MAX_STREAMS) streams.removeFirst()
    }

    private fun onBuffer(presentationTimeUs: Long) {
        // Streams are consecutive, so this buffer belongs to the latest one that has started.
        // A seek can precede all recorded start positions; use renderer-time offsets in that case.
        val stream = streams.lastOrNull { it.startPositionUs <= presentationTimeUs }
            ?: streams.lastOrNull { it.offsetUs <= presentationTimeUs }
        nextStream = stream
        nextPositionUs = if (stream != null) presentationTimeUs - stream.offsetUs else 0L
    }

    private fun measure(buffer: ByteBuffer) {
        val meter = meter ?: return
        try {
            val startNs = if (BuildConfig.DEBUG) System.nanoTime() else 0L
            var current = section
            val stream = nextStream
            if (current == null || current.stream !== stream) {
                if (current != null) {
                    // Contiguous audio moving on to another stream means the previous one ended.
                    finishSection(reachedEnd = stream != null)
                    meter.discardPartialSubBlock()
                }
                current = stream?.let { startSection(it) }
                section = current
            }
            if (current == null) return
            meter.process(buffer, current.blocks)
            val frames = buffer.remaining() / (2 * meterFormat.channelCount)
            current.measuredUs += frames * 1_000_000.0 / meterFormat.sampleRate
            if (BuildConfig.DEBUG) current.processingNs += System.nanoTime() - startNs
        } catch (e: Exception) {
            // Measuring is optional; never let it disturb playback.
            Log.w(TAG, "Loudness measurement failed", e)
            section = null
            meter.reset()
        }
    }

    private fun startSection(stream: Stream): Section {
        val periodIndex = timeline.getIndexOfPeriod(stream.mediaPeriodId.periodUid)
        var mediaItem: MediaItem? = null
        var durationUs = C.TIME_UNSET
        if (periodIndex != C.INDEX_UNSET) {
            val period = timeline.getPeriod(periodIndex, Timeline.Period())
            mediaItem = timeline.getWindow(period.windowIndex, Timeline.Window()).mediaItem
            durationUs = period.durationUs
        }
        listener.onSectionStarted(stream.mediaPeriodId.windowSequenceNumber)
        return Section(stream, mediaItem, durationUs, fromStart = nextPositionUs <= FROM_START_TOLERANCE_US)
    }

    private fun finishSection(reachedEnd: Boolean) {
        val finished = section ?: return
        section = null
        val mediaItem = finished.mediaItem ?: return
        val loudness = finished.blocks.integratedLoudness()
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Measured ${mediaItem.mediaId}: ${loudness?.let { "%.2f LUFS".format(it) }}" +
                    " over %.1f / %.1f s".format(finished.measuredUs / 1e6, finished.durationUs / 1e6) +
                    " fromStart=${finished.fromStart} reachedEnd=$reachedEnd" +
                    " processing=%.1f ms".format(finished.processingNs / 1e6)
            )
        }
        loudness ?: return
        listener.onMeasured(
            LoudnessMeasurement(
                mediaItem = mediaItem,
                durationUs = finished.durationUs,
                measuredUs = finished.measuredUs.toLong(),
                complete = finished.fromStart && reachedEnd,
                integratedLufs = loudness,
            )
        )
    }

    /** Ends the section for input that does not continue the audio measured so far. */
    private fun interruptSection() {
        finishSection(reachedEnd = false)
        meter?.reset()
    }

    /** Passes the audio through unchanged while measuring it. */
    private inner class MeasuringAudioProcessor : BaseAudioProcessor() {
        override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
            // This meter supports only 16-bit PCM. Leave other formats unprocessed
            // rather than rejecting them and failing playback.
            return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
                inputAudioFormat.channelCount > 0 &&
                inputAudioFormat.sampleRate > 0
            ) {
                inputAudioFormat
            } else {
                AudioFormat.NOT_SET
            }
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val size = inputBuffer.remaining()
            if (size == 0) return
            measure(inputBuffer)
            replaceOutputBuffer(size).put(inputBuffer).flip()
        }

        override fun onFlush(streamMetadata: StreamMetadata) {
            // The sink also flushes its processors for changes that keep the audio contiguous,
            // such as the playback speed. Sections are ended by the sink wrapper instead.
            if (!isActive) {
                meter = null
                meterFormat = AudioFormat.NOT_SET
            } else if (meter == null || meterFormat != inputAudioFormat) {
                meter = LoudnessMeter(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
                meterFormat = inputAudioFormat
            }
        }

        override fun onReset() {
            section = null
            meter = null
            meterFormat = AudioFormat.NOT_SET
        }
    }

    private inner class TrackingSink(sink: AudioSink) : ForwardingAudioSink(sink) {
        override fun handleBuffer(
            buffer: ByteBuffer,
            presentationTimeUs: Long,
            encodedAccessUnitCount: Int
        ): Boolean {
            onBuffer(presentationTimeUs)
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }

        override fun playToEndOfStream() {
            finishSection(reachedEnd = true)
            super.playToEndOfStream()
        }

        override fun flush() {
            interruptSection()
            super.flush()
        }

        override fun reset() {
            interruptSection()
            super.reset()
        }
    }

    private inner class TrackingRenderer(renderer: Renderer) : ForwardingRenderer(renderer) {
        override fun enable(
            configuration: RendererConfiguration,
            formats: Array<Format>,
            stream: SampleStream,
            positionUs: Long,
            joining: Boolean,
            mayRenderStartOfStream: Boolean,
            startPositionUs: Long,
            offsetUs: Long,
            mediaPeriodId: MediaSource.MediaPeriodId
        ) {
            // The sink was flushed when the renderer was disabled.
            // Clear old streams to avoid mismatches when renderer offsets are reused after a reset.
            streams.clear()
            register(mediaPeriodId, startPositionUs, offsetUs)
            super.enable(
                configuration,
                formats,
                stream,
                positionUs,
                joining,
                mayRenderStartOfStream,
                startPositionUs,
                offsetUs,
                mediaPeriodId
            )
        }

        override fun replaceStream(
            formats: Array<Format>,
            stream: SampleStream,
            startPositionUs: Long,
            offsetUs: Long,
            mediaPeriodId: MediaSource.MediaPeriodId
        ) {
            register(mediaPeriodId, startPositionUs, offsetUs)
            super.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId)
        }

        override fun setTimeline(timeline: Timeline) {
            this@LoudnessTracker.timeline = timeline
            super.setTimeline(timeline)
        }
    }

    private companion object {
        const val TAG = "LoudnessTracker"
        const val MAX_STREAMS = 16

        // Encoder delay can put the first decoded sample slightly after the start.
        const val FROM_START_TOLERANCE_US = 500_000L
    }
}
