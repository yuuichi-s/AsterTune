package io.github.yuuichi_s.astertune.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.yuuichi_s.astertune.constants.SleepTimerDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class SleepTimer(
    private val scope: CoroutineScope,
    val player: Player,
) : Player.Listener {
    private var sleepTimerJob: Job? = null
    var triggerTime by mutableLongStateOf(-1L)
        private set
    var pauseWhenSongEnd by mutableStateOf(false)
        private set
    val isActive: Boolean
        get() = triggerTime != -1L || pauseWhenSongEnd

    /** Whether the volume fades out before the timer stops playback. */
    var fadeEnabled: Boolean = true

    /** Length of the fade-out, in milliseconds. */
    var fadeDurationMs: Long = SleepTimerDefaults.FADE_DURATION_SECONDS * 1000L

    private val _fadeFactor = MutableStateFlow(1f)
    val fadeFactor: StateFlow<Float> = _fadeFactor.asStateFlow()

    /** Called when the active sleep timer completes. */
    var onFinish: () -> Unit = { player.pause() }

    fun start(minute: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _fadeFactor.value = 1f
        pauseWhenSongEnd = false
        triggerTime = -1L
        if (minute == -1) {
            pauseWhenSongEnd = true
            if (fadeEnabled) {
                sleepTimerJob = scope.launch {
                    // This job only updates fade volume. Timer completion is handled by
                    // player callbacks when playback ends or the current media item changes.
                    while (true) {
                        val duration = player.duration
                        val remaining = if (duration == C.TIME_UNSET || duration <= 0L) {
                            Long.MAX_VALUE
                        } else {
                            duration - player.currentPosition
                        }
                        if (remaining <= fadeDurationMs) {
                            _fadeFactor.value = fadeFactorFor(remaining)
                            delay(FADE_TICK_MS)
                        } else {
                            _fadeFactor.value = 1f
                            delay(FADE_POLL_MS)
                        }
                    }
                }
            }
        } else {
            val endTime = System.currentTimeMillis() + minute.minutes.inWholeMilliseconds
            triggerTime = endTime
            sleepTimerJob = scope.launch {
                if (fadeEnabled) {
                    val untilFade = endTime - fadeDurationMs - System.currentTimeMillis()
                    if (untilFade > 0) delay(untilFade)
                    while (true) {
                        val remaining = endTime - System.currentTimeMillis()
                        if (remaining <= 0) break
                        _fadeFactor.value = fadeFactorFor(remaining)
                        delay(FADE_TICK_MS)
                    }
                } else {
                    val untilEnd = endTime - System.currentTimeMillis()
                    if (untilEnd > 0) delay(untilEnd)
                }
                onFinish()
                triggerTime = -1L
                _fadeFactor.value = 1f
            }
        }
    }

    fun clear() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        pauseWhenSongEnd = false
        triggerTime = -1L
        _fadeFactor.value = 1f
    }

    private fun fadeFactorFor(remainingMs: Long): Float {
        if (remainingMs >= fadeDurationMs) return 1f
        return (remainingMs.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (pauseWhenSongEnd) finishSongEnd()
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && pauseWhenSongEnd) finishSongEnd()
    }

    /** Completes stop-after-this-song mode. */
    private fun finishSongEnd() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        pauseWhenSongEnd = false
        onFinish()
        _fadeFactor.value = 1f
    }

    companion object {
        private const val FADE_TICK_MS = 50L
        private const val FADE_POLL_MS = 1_000L
    }
}
