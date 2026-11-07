package io.github.yuuichi_s.astertune.viewmodels

import io.github.yuuichi_s.astertune.db.entities.GenreEntity
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import io.github.yuuichi_s.astertune.ui.screens.artist.hoursAndMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Values shown on the local artist page, computed from the artist's songs.
 */
class LocalArtistSummaryTest {

    private fun song(
        id: String,
        year: Int? = null,
        duration: Int = 0,
        genres: List<String> = emptyList(),
    ) = Song(
        song = SongEntity(id = id, title = id, duration = duration, localPath = null, year = year),
        artists = emptyList(),
        genre = genres.map { GenreEntity(id = it, title = it) },
    )

    @Test
    fun latestYear_ignoresSongsWithoutYear() {
        val songs = listOf(song("a", year = 2024), song("b"), song("c", year = 2026))
        assertEquals(2026, localArtistLatestYear(songs))
    }

    @Test
    fun latestYear_isNullWhenNoSongHasYear() {
        assertNull(localArtistLatestYear(listOf(song("a"), song("b"))))
    }

    @Test
    fun totalSeconds_countsUnknownLengthAsZero() {
        val songs = listOf(song("a", duration = 120), song("b", duration = -1), song("c", duration = 60))
        assertEquals(180, localArtistTotalSeconds(songs))
    }

    @Test
    fun hoursAndMinutes_exactlyOneHour() {
        assertEquals(1 to 0, hoursAndMinutes(3600))
    }

    @Test
    fun hoursAndMinutes_underOneHour() {
        assertEquals(0 to 59, hoursAndMinutes(3599))
    }

    @Test
    fun genres_mostSongsFirstAndCountedOncePerSong() {
        val songs = listOf(
            song("a", genres = listOf("Rock", "Rock")),
            song("b", genres = listOf("Pop")),
            song("c", genres = listOf("Pop")),
        )
        assertEquals(listOf("Pop", "Rock"), localArtistGenres(songs))
    }
}
