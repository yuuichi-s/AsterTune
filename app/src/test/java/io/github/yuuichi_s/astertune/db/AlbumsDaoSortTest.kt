package io.github.yuuichi_s.astertune.db

import android.app.Application
import io.github.yuuichi_s.astertune.constants.AlbumFilter
import io.github.yuuichi_s.astertune.constants.AlbumSortType
import io.github.yuuichi_s.astertune.db.entities.Album
import io.github.yuuichi_s.astertune.db.entities.AlbumArtistMap
import io.github.yuuichi_s.astertune.db.entities.AlbumEntity
import io.github.yuuichi_s.astertune.db.entities.SongAlbumMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Sorting and filtering of [AlbumsDao.albums] on the real Room query. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class AlbumsDaoSortTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = createTestDatabase()
        albums.forEach { testAlbum ->
            database.insert(testAlbum.entity)
            testAlbum.songIds.forEachIndexed { index, songId ->
                database.insert(SongAlbumMap(songId, testAlbum.entity.id, index))
            }
            testAlbum.artistIds.forEachIndexed { order, artistId ->
                database.insert(AlbumArtistMap(testAlbum.entity.id, artistId, order))
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Checks every sort type for LIBRARY; the filters do not change the ORDER BY, and a separate test
     * covers membership for the other filters.
     */
    @Test
    fun libraryAlbumsAreSortedByEveryType() = runBlocking {
        val failures = sortFailures(
            "albums LIBRARY", AlbumSortType.entries,
            { sortType, descending -> database.albums(AlbumFilter.LIBRARY, sortType, descending) },
            { it.id }, expectedIds(AlbumFilter.LIBRARY, localOnly = null), ::sortColumn,
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun albumsMatchEveryFilterUsedByTheScreens() = runBlocking {
        val cases = AlbumFilter.entries.map { it to null } + (AlbumFilter.LIBRARY to true)
        val failures = cases.mapNotNull { (filter, localOnly) ->
            val rows = database.albums(filter, AlbumSortType.CREATE_DATE, false, localOnly).first()
            setFailure("albums $filter localOnly=$localOnly", rows.map { it.id }, expectedIds(filter, localOnly))
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun testDataSeparatesEverySortKeyFromOtherColumns() = runBlocking {
        val rows = database.albums(AlbumFilter.LIBRARY, AlbumSortType.CREATE_DATE, false).first()
        val failures = separationFailures("albums LIBRARY", rows, AlbumSortType.entries, ::sortColumn, AlbumColumn.entries)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun expectedIds(filter: AlbumFilter, localOnly: Boolean?): List<String> =
        albums.filter { testAlbum ->
            val songs = testAlbum.songIds.map(::baseSong)
            val inFilter = when (filter) {
                AlbumFilter.LIBRARY -> songs.any { it.inLibrary != null }
                AlbumFilter.DOWNLOADED -> songs.any { it.dateDownload != null }
                AlbumFilter.LIKED -> testAlbum.entity.bookmarkedAt != null
            }
            inFilter && (localOnly == null || testAlbum.entity.isLocal == localOnly)
        }.map { it.entity.id }

    private fun sortColumn(sortType: AlbumSortType): AlbumColumn = when (sortType) {
        AlbumSortType.CREATE_DATE -> AlbumColumn.ROW_ID
        AlbumSortType.NAME -> AlbumColumn.TITLE
        AlbumSortType.ARTIST -> AlbumColumn.ARTIST
        AlbumSortType.YEAR -> AlbumColumn.YEAR
        // The stored column, not the number of linked songs.
        AlbumSortType.SONG_COUNT -> AlbumColumn.SONG_COUNT
        AlbumSortType.LENGTH -> AlbumColumn.DURATION
    }

    private enum class AlbumColumn : SortKey<Album> {
        ROW_ID {
            override fun key(row: Album) = albums.indexOfFirst { it.entity.id == row.id }
        },
        TITLE {
            override fun key(row: Album) = asciiLowercase(row.album.title)
        },

        /**
         * Checked only for albums with at most one artist: the order in which several artist names
         * are concatenated is not defined by SQLite. Local albums have no album_artist_map rows, so
         * their key is null.
         */
        ARTIST {
            override fun key(row: Album) = row.artists.singleOrNull()?.name?.let(::asciiLowercase)
            override fun appliesTo(row: Album) = row.artists.size <= 1
        },
        YEAR {
            override fun key(row: Album) = row.album.year
        },
        SONG_COUNT {
            override fun key(row: Album) = row.album.songCount
        },
        DURATION {
            override fun key(row: Album) = row.album.duration
        },
    }

    private class TestAlbum(
        val entity: AlbumEntity,
        val songIds: List<String>,
        val artistIds: List<String> = emptyList(),
    )

    private companion object {
        val bookmarked: LocalDateTime = day(40)

        /** The values are chosen so that every pair of sort keys orders some library albums oppositely. */
        val albums = listOf(
            TestAlbum(
                // Two library songs, so the album must still come back once.
                AlbumEntity(id = "alb-golf", title = "Golf", year = 2015, songCount = 12, duration = 300, bookmarkedAt = bookmarked),
                songIds = listOf("ytm-kilo", "ytm-kilo-lower"),
                artistIds = listOf("UCsingle08"),
            ),
            TestAlbum(
                // Only a downloaded song that is not in the library.
                AlbumEntity(id = "alb-echo", title = "echo", songCount = 1, duration = 200),
                songIds = listOf("ytm-lima"),
                artistIds = listOf("UCsingle01"),
            ),
            TestAlbum(
                // Local albums have no album artists.
                AlbumEntity(id = "alb-local-charlie", title = "Charlie", year = 1999, songCount = 2, duration = 500, isLocal = true),
                songIds = listOf("local-sakura", "local-hotel"),
            ),
            TestAlbum(
                AlbumEntity(id = "alb-alpha", title = "Alpha", year = 2020, songCount = 5, duration = 100, bookmarkedAt = bookmarked),
                songIds = listOf("ytm-mike"),
                artistIds = listOf("UCmulti_b", "UCmulti_c", "UCmulti_a"),
            ),
            TestAlbum(
                // Liked without any songs.
                AlbumEntity(id = "alb-foxtrot", title = "Foxtrot", year = 2005, songCount = 20, duration = 50, bookmarkedAt = bookmarked),
                songIds = emptyList(),
                artistIds = listOf("UCsingle05"),
            ),
            TestAlbum(
                // Only a local song removed from the library: in none of the lists.
                AlbumEntity(id = "alb-local-delta", title = "Delta", songCount = 0, duration = 0, isLocal = true),
                songIds = listOf("local-foxtrot"),
            ),
            TestAlbum(
                AlbumEntity(id = "alb-bravo", title = "Bravo", year = 2010, songCount = 3, duration = 900),
                songIds = listOf("ytm-oscar", "ytm-emile-upper"),
                artistIds = listOf("UCsingle03"),
            ),
            TestAlbum(
                AlbumEntity(id = "alb-india", title = "India", songCount = 7, duration = 50),
                songIds = listOf("ytm-emile-lower"),
                artistIds = listOf("UCsingle06"),
            ),
        )
    }
}
