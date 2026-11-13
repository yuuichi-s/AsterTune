/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.lastfm

import kotlinx.serialization.Serializable

/**
 * One listen for `track.scrobble`.
 *
 * [timestamp] is the listen's start time in Unix seconds (UTC).
 */
@Serializable
data class Scrobble(
    val artist: String,
    val track: String,
    val timestamp: Long,
    val album: String? = null,
    val durationSec: Long? = null,
)

/** Scrobbling rules from https://www.last.fm/api/scrobbling. */
object ScrobbleRules {
    private const val MIN_DURATION_MS = 30_000L
    private const val MAX_REQUIRED_PLAY_MS = 240_000L

    /**
     * Whether a track is longer than 30 seconds and has been played for at least half its duration
     * or 4 minutes, whichever is earlier.
     */
    fun isScrobblable(durationMs: Long, playedMs: Long): Boolean =
        durationMs > MIN_DURATION_MS && playedMs >= minOf(durationMs / 2, MAX_REQUIRED_PLAY_MS)
}

/** What to do with a sent batch, given the answer from Last.fm. */
enum class SendOutcome {
    /**
     * Remove the batch after success, including ignored scrobbles, or an API error outside the
     * retry and authentication groups.
     */
    DONE,

    /** Keep the batch and send it again later. */
    RETRY,

    /** Keep the batch and stop sending until the user signs in again. */
    SESSION_INVALID,

    /** Keep the batch and stop sending after an authentication, API key, or signature error. */
    API_KEY_REJECTED,
}

/**
 * Maps a scrobble result to the next queue action.
 *
 * API errors outside the retry and authentication groups map to [SendOutcome.DONE].
 */
fun ApiResult<*>.toSendOutcome(): SendOutcome = when (this) {
    is ApiResult.Success -> SendOutcome.DONE
    is ApiResult.Unavailable -> SendOutcome.RETRY
    is ApiResult.Error -> when (code) {
        // 11 service offline, 16 temporary error, 29 rate limit, 8 operation failed
        8, 11, 16, 29 -> SendOutcome.RETRY
        // 9 invalid session key
        9 -> SendOutcome.SESSION_INVALID
        // 4 authentication failed, 10 invalid API key, 13 invalid signature, 26 suspended API key
        4, 10, 13, 26 -> SendOutcome.API_KEY_REJECTED
        else -> SendOutcome.DONE
    }
}
