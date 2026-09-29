package io.github.yuuichi_s.astertune.db

import android.app.Application
import io.github.yuuichi_s.astertune.constants.SongSortType
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sorting of [SongsDao.songs], [SongsDao.likedSongs] and [SongsDao.downloadSongs], and the set of
 * [SongsDao.allLocalSongsFlow], on the real Room queries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SongsDaoSortTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun songsAreSortedByEveryType() = checkEntry(songsEntry)

    @Test
    fun likedSongsAreSortedByEveryType() = checkEntry(likedSongsEntry)

    @Test
    fun downloadSongsAreSortedByEveryType() = checkEntry(downloadSongsEntry)

    /** The local songs screen sorts this list in memory, so only the set is checked here. */
    @Test
    fun allLocalSongsAreLocalLibrarySongs() = runBlocking {
        val expectedIds = baseSongs.map { it.entity }.filter { it.isLocal && it.inLibrary != null }.map { it.id }
        val failure = setFailure("allLocalSongsFlow", database.allLocalSongsFlow().first().map { it.id }, expectedIds)
        assertTrue(failure.orEmpty(), failure == null)
    }

    @Test
    fun testDataSeparatesEverySortKeyFromOtherColumns() = runBlocking {
        val failures = listOf(songsEntry, likedSongsEntry, downloadSongsEntry).flatMap { entry ->
            separationFailures(
                entry.name, entry.query(SongSortType.CREATE_DATE, false).first(),
                SongSortType.entries, entry.sortColumn, SongColumn.entries,
            )
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun checkEntry(entry: Entry) = runBlocking {
        val expectedIds = baseSongs.map { it.entity }.filter(entry.inSet).map { it.id }
        val failures = sortFailures(entry.name, SongSortType.entries, entry.query, { it.id }, expectedIds, entry.sortColumn)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private class Entry(
        val name: String,
        val query: (SongSortType, Boolean) -> Flow<List<Song>>,
        val inSet: (SongEntity) -> Boolean,
        val sortColumn: (SongSortType) -> SongColumn,
    )

    private val songsEntry
        get() = Entry(
            name = "songs",
            query = database::songs,
            inSet = { it.inLibrary != null },
            sortColumn = ::librarySortColumn,
        )

    private val likedSongsEntry
        get() = Entry(
            name = "likedSongs",
            query = database::likedSongs,
            inSet = { it.liked },
            sortColumn = ::likedSortColumn,
        )

    private val downloadSongsEntry
        get() = Entry(
            name = "downloadSongs",
            query = database::downloadSongs,
            // Includes dateDownload 0 (failed or stopped) and 1 (queued or downloading), which the
            // download rescan writes. This records the current behavior; if the set is changed on
            // purpose, update this expectation in the same change.
            inSet = { !it.isLocal && it.dateDownload != null },
            sortColumn = ::librarySortColumn,
        )

    private fun librarySortColumn(sortType: SongSortType): SongColumn = when (sortType) {
        SongSortType.CREATE_DATE -> SongColumn.IN_LIBRARY
        SongSortType.MODIFIED_DATE -> SongColumn.DATE_MODIFIED
        SongSortType.RELEASE_DATE -> SongColumn.RELEASE_DATE
        SongSortType.NAME -> SongColumn.TITLE
        SongSortType.ARTIST -> SongColumn.ARTIST
        SongSortType.PLAY_COUNT -> SongColumn.PLAY_COUNT
    }

    private fun likedSortColumn(sortType: SongSortType): SongColumn = when (sortType) {
        SongSortType.CREATE_DATE -> SongColumn.LIKED_DATE
        SongSortType.MODIFIED_DATE -> SongColumn.DATE_MODIFIED
        SongSortType.RELEASE_DATE -> SongColumn.RELEASE_DATE
        SongSortType.NAME -> SongColumn.TITLE
        SongSortType.ARTIST -> SongColumn.ARTIST
        SongSortType.PLAY_COUNT -> SongColumn.PLAY_COUNT
    }
}
