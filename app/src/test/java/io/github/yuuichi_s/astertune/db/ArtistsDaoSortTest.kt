package io.github.yuuichi_s.astertune.db

import android.app.Application
import io.github.yuuichi_s.astertune.constants.ArtistFilter
import io.github.yuuichi_s.astertune.constants.ArtistSongSortType
import io.github.yuuichi_s.astertune.constants.ArtistSortType
import io.github.yuuichi_s.astertune.db.entities.Artist
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.SongArtistMap
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sorting and filtering of [ArtistsDao.artists] and [ArtistsDao.artistSongs] on the real Room queries. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ArtistsDaoSortTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = createTestDatabase()
        extraArtists.forEach { database.insert(it) }
        extraSongArtists.forEach { (artistId, songIds) ->
            songIds.forEach { songId -> database.insert(SongArtistMap(songId, artistId, position = 9)) }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Sort order is checked on the library list only: the filters change the WHERE clause, not the ORDER BY. */
    @Test
    fun libraryArtistsAreSortedByEveryType() = runBlocking {
        val failures = sortFailures(
            "artists LIBRARY", ArtistSortType.entries,
            { sortType, descending -> database.artists(ArtistFilter.LIBRARY, sortType, descending) },
            { it.id }, expectedIds(ArtistFilter.LIBRARY, localOnly = null), ::sortColumn,
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun artistsMatchEveryFilterUsedByTheScreens() = runBlocking {
        val cases = ArtistFilter.entries.map { it to null } + (ArtistFilter.LIBRARY to true)
        val failures = cases.mapNotNull { (filter, localOnly) ->
            val rows = database.artists(filter, ArtistSortType.CREATE_DATE, false, localOnly).first()
            setFailure("artists $filter localOnly=$localOnly", rows.map { it.id }, expectedIds(filter, localOnly))
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun artistSongsAreSortedByEveryType() = runBlocking {
        val expectedIds = songsOf(ARTIST_WITH_SONGS).filter { it.inLibrary != null }.map { it.id }
        val failures = sortFailures(
            "artistSongs", ArtistSongSortType.entries,
            { sortType, descending -> database.artistSongs(ARTIST_WITH_SONGS, sortType, descending) },
            { it.id }, expectedIds, ::artistSongSortColumn,
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun testDataSeparatesEverySortKeyFromOtherColumns() = runBlocking {
        val artists = database.artists(ArtistFilter.LIBRARY, ArtistSortType.CREATE_DATE, false).first()
        val songs = database.artistSongs(ARTIST_WITH_SONGS, ArtistSongSortType.CREATE_DATE, false).first()
        val failures =
            separationFailures("artists LIBRARY", artists, ArtistSortType.entries, ::sortColumn, ArtistColumn.entries) +
                separationFailures("artistSongs", songs, ArtistSongSortType.entries, ::artistSongSortColumn, SongColumn.entries)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun expectedIds(filter: ArtistFilter, localOnly: Boolean?): List<String> =
        (baseArtists + extraArtists).filter { artist ->
            val songs = songsOf(artist.id)
            val inFilter = when (filter) {
                ArtistFilter.LIBRARY -> songs.any { it.inLibrary != null }
                ArtistFilter.DOWNLOADED -> songs.any { it.dateDownload != null }
                ArtistFilter.LIKED -> artist.bookmarkedAt != null
            }
            // Artists that are neither YouTube nor local are dropped after the query.
            inFilter && (artist.isYouTubeArtist || artist.isLocal) &&
                (localOnly == null || artist.isLocal == localOnly)
        }.map { it.id }

    private fun sortColumn(sortType: ArtistSortType): ArtistColumn = when (sortType) {
        ArtistSortType.CREATE_DATE -> ArtistColumn.ROW_ID
        ArtistSortType.NAME -> ArtistColumn.NAME
        ArtistSortType.SONG_COUNT -> ArtistColumn.LIBRARY_SONG_COUNT
    }

    private fun artistSongSortColumn(sortType: ArtistSongSortType): SongColumn = when (sortType) {
        ArtistSongSortType.CREATE_DATE -> SongColumn.IN_LIBRARY
        ArtistSongSortType.NAME -> SongColumn.TITLE
    }

    private enum class ArtistColumn : SortKey<Artist> {
        ROW_ID {
            override fun key(row: Artist) = (baseArtists + extraArtists).indexOfFirst { it.id == row.id }
        },
        NAME {
            override fun key(row: Artist) = asciiLowercase(row.artist.name)
        },

        /** Counts the songs that pass the filter; on the library list, the library songs. */
        LIBRARY_SONG_COUNT {
            override fun key(row: Artist) = songsOf(row.id).count { it.inLibrary != null }
        },
    }

    private companion object {
        const val ARTIST_WITH_SONGS = "UCextra_alpha"

        val extraArtists = listOf(
            ArtistEntity(id = ARTIST_WITH_SONGS, name = "Alpha", bookmarkedAt = day(40)),
            ArtistEntity(id = "UCextra_bravo", name = "Bravo"),
            // Liked without any songs.
            ArtistEntity(id = "UCextra_charlie", name = "Charlie", bookmarkedAt = day(41)),
            // Neither a YouTube nor a local artist id.
            ArtistEntity(id = "XXextra_other", name = "Other", bookmarkedAt = day(42)),
        )

        /** Added to the base songs, which already have one artist each (ytm-mike has three). */
        val extraSongArtists = mapOf(
            // ytm-lima is not in the library, so it is left out of the library song count and of
            // artistSongs().
            ARTIST_WITH_SONGS to listOf("ytm-kilo", "local-juliet", "local-hotel", "ytm-emile-upper", "ytm-mike", "ytm-lima"),
            "UCextra_bravo" to listOf("ytm-oscar", "local-sakura"),
            "XXextra_other" to listOf("ytm-kilo"),
        )

        fun songsOf(artistId: String): List<SongEntity> {
            val baseSongIds = baseSongs.filter { song -> song.artists.any { it.first == artistId } }.map { it.entity.id }
            return (baseSongIds + extraSongArtists[artistId].orEmpty()).map(::baseSong)
        }
    }
}
