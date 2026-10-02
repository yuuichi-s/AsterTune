package com.dd3boh.lrclib

import com.dd3boh.lrclib.models.Track
import com.dd3boh.lrclib.models.bestMatchingFor
import com.dd3boh.lrclib.models.selectCandidates
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies decoding of null-duration rows and their exclusion from [bestMatchingFor]
 * and [selectCandidates].
 */
class LrcLibSelectionTest {

    private fun track(id: Int, duration: Double?, synced: String?, plain: String? = null) =
        Track(id, "title", "artist", duration, plain, synced)

    @Test
    fun decodesResponseContainingNullDuration() {
        val response = """
            [
              {"id": 1, "trackName": "title", "artistName": "artist", "albumName": "album",
               "duration": null, "instrumental": false,
               "plainLyrics": "null-plain", "syncedLyrics": "[00:01.00]null-synced"},
              {"id": 2, "trackName": "title", "artistName": "artist", "albumName": "album",
               "duration": 293.0, "instrumental": false,
               "plainLyrics": "valid-plain", "syncedLyrics": "[00:01.00]valid-synced"}
            ]
        """.trimIndent()

        val tracks = LrcLib.jsonFormat.decodeFromString<List<Track>>(response)

        assertEquals(
            listOf(
                track(1, null, "[00:01.00]null-synced", "null-plain"),
                track(2, 293.0, "[00:01.00]valid-synced", "valid-plain"),
            ),
            tracks,
        )
    }

    @Test
    fun automaticLookupSkipsNullDurationWithKnownSongDuration() {
        val tracks = listOf(
            track(1, null, "null-synced"),
            track(2, 293.0, "valid-synced"),
        )
        assertEquals(2, tracks.bestMatchingFor(293)?.id)
    }

    @Test
    fun manualSearchSkipsNullDurationWithKnownSongDuration() {
        val tracks = listOf(
            track(1, null, "null-synced", "null-plain"),
            track(2, 293.0, "valid-synced", "valid-plain"),
        )
        assertEquals(listOf("valid-synced", "valid-plain"), tracks.selectCandidates(293))
    }

    @Test
    fun automaticLookupSkipsNullDurationWithUnknownSongDuration() {
        val tracks = listOf(
            track(1, null, "null-synced"),
            track(2, 293.0, "valid-synced"),
        )
        assertEquals(2, tracks.bestMatchingFor(-1)?.id)
    }

    @Test
    fun manualSearchSkipsNullDurationWithUnknownSongDuration() {
        val tracks = listOf(
            track(1, null, "null-synced"),
            track(2, 293.0, "valid-synced"),
        )
        assertEquals(listOf("valid-synced"), tracks.selectCandidates(-1))
    }

    @Test
    fun manualSearchSkipsNullDurationNearZeroSongDuration() {
        val tracks = listOf(
            track(1, null, "null-synced", "null-plain"),
            track(2, 1.0, "valid-synced", "valid-plain"),
        )
        assertEquals(listOf("valid-synced", "valid-plain"), tracks.selectCandidates(1))
    }
}
