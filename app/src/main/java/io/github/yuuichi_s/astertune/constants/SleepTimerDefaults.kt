package io.github.yuuichi_s.astertune.constants

/**
 * Default values, bounds and presets for the sleep timer.
 *
 * Durations are stored in minutes (timer length) and seconds (fade length).
 */
object SleepTimerDefaults {
    const val FADE_ENABLED = true

    /** Fade length in seconds. */
    const val FADE_DURATION_SECONDS = 30
    val FADE_DURATION_RANGE = 5..60

    /** Timer length in minutes. */
    const val DEFAULT_MINUTES = 60
    val MINUTES_RANGE = 1..120

    /** Fixed-minute preset buttons shown in the timer dialog. */
    val PRESET_MINUTES = listOf(15, 30, 45, 60, 75, 90)
}
