/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback

/**
 * Loudness of local songs known in this session, so that a playback can use a measurement before
 * its database write has finished. Used on the main thread only.
 */
class LocalLoudnessStore {
    // A null value records that the song has no loudness yet.
    private val values = HashMap<String, Double?>()

    operator fun contains(songId: String) = songId in values

    operator fun get(songId: String): Double? = values[songId]

    /** Initializes a song's loudness without replacing an existing entry. */
    fun remember(songId: String, loudnessDb: Double?) {
        if (songId !in values) values[songId] = loudnessDb
    }

    /**
     * Records a measurement. A complete one always replaces the value; a partial one is used only
     * for a song known to have no value, as the database write does.
     */
    fun onMeasured(songId: String, loudnessDb: Double, complete: Boolean) {
        if (complete || (songId in values && values[songId] == null)) {
            values[songId] = loudnessDb
        }
    }
}
