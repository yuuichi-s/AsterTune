package io.github.yuuichi_s.astertune.db

import android.app.Application
import io.github.yuuichi_s.astertune.constants.PlaylistFilter
import io.github.yuuichi_s.astertune.constants.PlaylistSortType
import io.github.yuuichi_s.astertune.db.entities.Playlist
import io.github.yuuichi_s.astertune.db.entities.PlaylistEntity
import io.github.yuuichi_s.astertune.db.entities.PlaylistSongMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sorting and filtering of [PlaylistsDao.playlists] and the order of [PlaylistsDao.playlistSongs] on the real Room queries. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class PlaylistsDaoSortTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = createTestDatabase()
        playlists.forEach { testPlaylist ->
            database.insert(testPlaylist.entity)
            // Inserted in list order, which differs from the position order.
            testPlaylist.songs.forEach { (songId, position) ->
                database.insert(PlaylistSongMap(playlistId = testPlaylist.entity.id, songId = songId, position = position))
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
    fun libraryPlaylistsAreSortedByEveryType() = runBlocking {
        val failures = sortFailures(
            "playlists LIBRARY", PlaylistSortType.entries,
            { sortType, descending -> database.playlists(PlaylistFilter.LIBRARY, sortType, descending) },
            { it.id }, expectedIds(PlaylistFilter.LIBRARY, variant = 0, localSongsOnly = false), ::sortColumn,
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Covers the argument combinations used by library screens and the add-to-playlist dialog. */
    @Test
    fun playlistsMatchEveryFilterUsedByTheScreens() = runBlocking {
        val cases = PlaylistFilter.entries.map { Triple(it, 0, false) } +
            Triple(PlaylistFilter.LIBRARY, 0, true) +
            Triple(PlaylistFilter.LIBRARY, 1, false) +
            Triple(PlaylistFilter.LIBRARY, 2, false)
        val failures = cases.mapNotNull { (filter, variant, localSongsOnly) ->
            val rows = database.playlists(filter, PlaylistSortType.CREATE_DATE, false, variant, localSongsOnly).first()
            setFailure(
                "playlists $filter variant=$variant localSongsOnly=$localSongsOnly",
                rows.map { it.id }, expectedIds(filter, variant, localSongsOnly),
            )
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The custom order of a playlist is this query's position order; the screen keeps it as is. */
    @Test
    fun playlistSongsFollowPosition() = runBlocking {
        for (testPlaylist in playlists) {
            val expected = testPlaylist.songs.sortedBy { it.second }.map { it.first }
            val actual = database.playlistSongs(testPlaylist.entity.id).first().map { it.map.songId }
            assertEquals("playlistSongs ${testPlaylist.entity.id}", expected, actual)
        }
    }

    @Test
    fun testDataSeparatesEverySortKeyFromOtherColumns() = runBlocking {
        val rows = database.playlists(PlaylistFilter.LIBRARY, PlaylistSortType.CREATE_DATE, false).first()
        val failures = separationFailures("playlists LIBRARY", rows, PlaylistSortType.entries, ::sortColumn, PlaylistColumn.entries)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun expectedIds(filter: PlaylistFilter, variant: Int, localSongsOnly: Boolean): List<String> =
        playlists.filter { testPlaylist ->
            val playlist = testPlaylist.entity
            val songs = testPlaylist.songs.map { baseSong(it.first) }
            val inVariant = when (variant) {
                0 -> playlist.bookmarkedAt != null || playlist.isLocal
                1 -> playlist.isLocal && playlist.bookmarkedAt != null
                2 -> playlist.isEditable && playlist.bookmarkedAt != null
                else -> error("variant $variant is not used")
            }
            val inFilter = when (filter) {
                PlaylistFilter.LIBRARY -> true
                PlaylistFilter.DOWNLOADED -> songs.any { it.dateDownload != null }
            }
            // A playlist without songs counts as having only local songs.
            inVariant && inFilter && (!localSongsOnly || songs.all { it.isLocal })
        }.map { it.entity.id }

    private fun sortColumn(sortType: PlaylistSortType): PlaylistColumn = when (sortType) {
        PlaylistSortType.CREATE_DATE -> PlaylistColumn.ROW_ID
        PlaylistSortType.NAME -> PlaylistColumn.NAME
        PlaylistSortType.SONG_COUNT -> PlaylistColumn.SONG_COUNT
    }

    private enum class PlaylistColumn : SortKey<Playlist> {
        ROW_ID {
            override fun key(row: Playlist) = playlists.indexOfFirst { it.entity.id == row.id }
        },
        NAME {
            override fun key(row: Playlist) = asciiLowercase(row.playlist.name)
        },

        /** The number of songs linked in the database, not the remote song count. */
        SONG_COUNT {
            override fun key(row: Playlist) = playlists.single { it.entity.id == row.id }.songs.size
        },
    }

    private class TestPlaylist(
        val entity: PlaylistEntity,
        /** Song id to its position in the playlist. */
        val songs: List<Pair<String, Int>> = emptyList(),
    )

    private companion object {
        /** The values are chosen so that every pair of sort keys orders some playlists oppositely. */
        val playlists = listOf(
            TestPlaylist(
                PlaylistEntity(id = "pl-mango", name = "Mango", browseId = "PLmango", isEditable = false, bookmarkedAt = day(40)),
                songs = listOf("ytm-kilo" to 2, "ytm-oscar" to 0, "ytm-lima" to 1),
            ),
            TestPlaylist(
                // Local, not bookmarked.
                PlaylistEntity(id = "pl-local-delta", name = "delta", isLocal = true),
                songs = listOf("local-juliet" to 1, "local-sakura" to 0),
            ),
            TestPlaylist(
                // Local, with a remote song.
                PlaylistEntity(id = "pl-local-bravo", name = "Bravo", isLocal = true, bookmarkedAt = day(41)),
                songs = listOf("local-sakura" to 3, "ytm-mike" to 0, "local-hotel" to 2, "local-juliet" to 1),
            ),
            TestPlaylist(
                // Created on YouTube Music, without songs yet.
                PlaylistEntity(id = "pl-alpha", name = "alpha", browseId = "PLalpha", bookmarkedAt = day(42)),
            ),
            TestPlaylist(
                // Neither local nor bookmarked: in none of the lists.
                PlaylistEntity(id = "pl-zulu", name = "Zulu", browseId = "PLzulu"),
                songs = listOf("ytm-kilo" to 0),
            ),
            TestPlaylist(
                PlaylistEntity(id = "pl-echo", name = "Echo", browseId = "PLecho", isEditable = false, bookmarkedAt = day(43)),
                songs = listOf("ytm-emile-upper" to 0),
            ),
        )
    }
}
