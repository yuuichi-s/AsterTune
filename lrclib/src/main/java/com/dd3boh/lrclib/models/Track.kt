package com.dd3boh.lrclib.models

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * A row of the LRCLIB search response.
 * [duration] is null for some rows in the live data, so it is nullable to keep one such row
 * from failing the whole response.
 */
@Serializable
data class Track(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double?,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

/**
 * Picks the track to adopt for automatic lookup.
 * Rows without a duration are never adopted,
 * including when the song's own duration is unknown (-1).
 */
internal fun List<Track>.bestMatchingFor(duration: Int): Track? =
    firstOrNull {
        val trackDuration = it.duration ?: return@firstOrNull false
        duration == -1 || abs(trackDuration.toInt() - duration) <= 8
    }

/**
 * Selects the lyrics shown as manual-search candidates, in response order.
 * Rows without a duration are never shown,
 * including when the song's own duration is unknown (-1).
 */
internal fun List<Track>.selectCandidates(duration: Int): List<String> {
    val candidates = mutableListOf<String>()
    var count = 0
    var plain = 0
    forEach {
        val trackDuration = it.duration ?: return@forEach
        if (count <= 4) {
            if (it.syncedLyrics != null && duration == -1) {
                count++
                candidates += it.syncedLyrics
            } else {
                if (it.syncedLyrics != null && abs(trackDuration - duration) <= 2) {
                    count++
                    candidates += it.syncedLyrics
                }
                if (it.plainLyrics != null && abs(trackDuration - duration) <= 2 && plain == 0) {
                    count++
                    plain++
                    candidates += it.plainLyrics
                }
            }
        }
    }
    return candidates
}
