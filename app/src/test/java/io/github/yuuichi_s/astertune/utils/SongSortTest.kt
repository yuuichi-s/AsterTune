package io.github.yuuichi_s.astertune.utils

import io.github.yuuichi_s.astertune.constants.FolderSongSortType
import io.github.yuuichi_s.astertune.constants.PlaylistSongSortType
import io.github.yuuichi_s.astertune.db.SortKey
import io.github.yuuichi_s.astertune.db.TestSong
import io.github.yuuichi_s.astertune.db.baseArtists
import io.github.yuuichi_s.astertune.db.baseSongs
import io.github.yuuichi_s.astertune.db.day
import io.github.yuuichi_s.astertune.db.epochMillis
import io.github.yuuichi_s.astertune.db.ReleaseKey
import io.github.yuuichi_s.astertune.db.releaseKey
import io.github.yuuichi_s.astertune.db.separationFailures
import io.github.yuuichi_s.astertune.db.sortFailures
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.PlaylistSong
import io.github.yuuichi_s.astertune.db.entities.PlaylistSongMap
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * In-memory sorting of [sortLocalSongs] and [sortPlaylistSongs], on the base song data of the DAO
 * tests and a few local songs with tags.
 */
class SongSortTest {
    @Test
    fun localSongsAreSortedByEveryType() = runBlocking {
        val failures = sortFailures(
            "sortLocalSongs", FolderSongSortType.entries,
            { sortType, descending -> flowOf(sortLocalSongs(localSongs, sortType, descending)) },
            { it.id }, localSongs.map { it.id }, ::localSortKey,
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun playlistSongsAreSortedByEveryType() = runBlocking {
        val sortTypes = PlaylistSongSortType.entries - PlaylistSongSortType.CUSTOM - PlaylistSongSortType.DOWNLOAD_DATE
        val failures = sortFailures(
            "sortPlaylistSongs", sortTypes,
            { sortType, descending -> flowOf(sortPlaylistSongs(playlistSongs, sortType, descending)) },
            { it.map.songId }, playlistSongs.map { it.map.songId }, ::playlistSortKey,
        )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The custom order is the position order the query returns, in both directions. */
    @Test
    fun playlistCustomOrderKeepsTheInput() {
        for (descending in listOf(false, true)) {
            assertEquals(
                "descending=$descending",
                playlistSongs.map { it.map.songId },
                sortPlaylistSongs(playlistSongs, PlaylistSongSortType.CUSTOM, descending).map { it.map.songId },
            )
        }
    }

    /**
     * Songs are grouped into remote songs not downloaded, local songs not downloaded, and downloaded
     * songs by download date. Descending reverses the groups and the dates, but songs within a group
     * keep the playlist order in both directions, so that songs still to download can be checked
     * against the playlist.
     */
    @Test
    fun playlistDownloadDateKeepsThePlaylistOrderWithinGroups() {
        // dateDownload 0 (failed or stopped) and 1 (downloading) are not downloaded, and a local song
        // with a download date counts as downloaded.
        fun isDownloaded(song: PlaylistSong) = song.song.song.dateDownload?.let { it > epochMillis(1) } == true
        val remote = playlistSongs.filter { !isDownloaded(it) && !it.song.song.isLocal }
        val local = playlistSongs.filter { !isDownloaded(it) && it.song.song.isLocal }
        val downloaded = playlistSongs.filter(::isDownloaded)
        assertTrue("every group needs several songs", listOf(remote, local, downloaded).all { it.size >= 2 })
        assertTrue("downloaded songs need a local one", downloaded.any { it.song.song.isLocal })
        assertTrue("downloaded songs need a tie", downloaded.groupBy { it.song.song.dateDownload }.any { it.value.size >= 2 })

        assertEquals(
            (remote + local + downloaded.sortedBy { it.song.song.dateDownload }).map { it.map.songId },
            sortPlaylistSongs(playlistSongs, PlaylistSongSortType.DOWNLOAD_DATE, false).map { it.map.songId },
        )
        assertEquals(
            (downloaded.sortedByDescending { it.song.song.dateDownload } + local + remote).map { it.map.songId },
            sortPlaylistSongs(playlistSongs, PlaylistSongSortType.DOWNLOAD_DATE, true).map { it.map.songId },
        )
    }

    @Test
    fun testDataSeparatesEverySortKeyFromOtherColumns() {
        val failures =
            separationFailures("sortLocalSongs", localSongs, FolderSongSortType.entries, ::localSortKey, LocalSongKey.entries) +
                separationFailures(
                    "sortPlaylistSongs", playlistSongs,
                    PlaylistSongSortType.entries - PlaylistSongSortType.CUSTOM - PlaylistSongSortType.DOWNLOAD_DATE,
                    ::playlistSortKey, PlaylistSongKey.entries,
                )
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun localSortKey(sortType: FolderSongSortType): LocalSongKey = when (sortType) {
        FolderSongSortType.CREATE_DATE -> LocalSongKey.IN_LIBRARY
        FolderSongSortType.MODIFIED_DATE -> LocalSongKey.DATE_MODIFIED
        FolderSongSortType.RELEASE_DATE -> LocalSongKey.RELEASE_DATE
        FolderSongSortType.NAME -> LocalSongKey.TITLE
        FolderSongSortType.ARTIST -> LocalSongKey.ARTIST
        FolderSongSortType.PLAY_COUNT -> LocalSongKey.PLAY_COUNT
        FolderSongSortType.TRACK_NUMBER -> LocalSongKey.TRACK_NUMBER
    }

    private fun playlistSortKey(sortType: PlaylistSongSortType): PlaylistSongKey = when (sortType) {
        PlaylistSongSortType.NAME -> PlaylistSongKey.TITLE
        PlaylistSongSortType.ARTIST -> PlaylistSongKey.ARTIST
        PlaylistSongSortType.ADDED_DATE -> PlaylistSongKey.IN_LIBRARY
        PlaylistSongSortType.MODIFIED_DATE -> PlaylistSongKey.DATE_MODIFIED
        PlaylistSongSortType.RELEASE_DATE -> PlaylistSongKey.RELEASE_DATE
        // Checked by their own tests.
        PlaylistSongSortType.CUSTOM, PlaylistSongSortType.DOWNLOAD_DATE -> error("$sortType has its own test")
    }

    /**
     * Keys of the local songs order. A missing date comes first ascending like the earliest date,
     * and a missing play count sorts as 0; both record the current behavior, so if either is changed
     * on purpose, update the key in the same change. A missing track number goes last on purpose.
     */
    private enum class LocalSongKey : SortKey<Song> {
        IN_LIBRARY {
            override fun key(row: Song) = row.song.inLibrary
        },
        DATE_MODIFIED {
            override fun key(row: Song) = row.song.dateModified
        },
        RELEASE_DATE {
            override fun key(row: Song) = releaseKey(row.song)
        },
        TITLE {
            override fun key(row: Song) = row.song.title.lowercase()
        },

        /** Checked only for songs with one artist, as in the DAO tests. */
        ARTIST {
            override fun key(row: Song) = row.artists.single().name.lowercase()
            override fun appliesTo(row: Song) = row.artists.size == 1
        },
        PLAY_COUNT {
            override fun key(row: Song) = row.playCount.orEmpty().sumOf { it.count.toLong() }
        },
        TRACK_NUMBER {
            override fun key(row: Song) = row.song.trackNumber?.toLong() ?: Long.MAX_VALUE
        },
        INPUT_ORDER {
            override fun key(row: Song) = localSongs.indexOf(row)
        },
    }

    /** Keys of the playlist order. Missing values come first ascending. */
    private enum class PlaylistSongKey : SortKey<PlaylistSong> {
        TITLE {
            override fun key(row: PlaylistSong) = row.song.song.title.lowercase()
        },

        /** Checked only for songs with one artist, as in the DAO tests. */
        ARTIST {
            override fun key(row: PlaylistSong) = row.song.artists.single().name.lowercase()
            override fun appliesTo(row: PlaylistSong) = row.song.artists.size == 1
        },
        IN_LIBRARY {
            override fun key(row: PlaylistSong) = row.song.song.inLibrary
        },
        DATE_MODIFIED {
            override fun key(row: PlaylistSong) = row.song.song.dateModified
        },
        RELEASE_DATE {
            override fun key(row: PlaylistSong) = releaseKey(row.song.song)
        },
        POSITION {
            override fun key(row: PlaylistSong) = row.map.position
        },
    }

    private companion object {
        val tagArtists = listOf(
            ArtistEntity(id = "LAtag00001", name = "Delta", isLocal = true),
            ArtistEntity(id = "LAtag00002", name = "charlie", isLocal = true),
            ArtistEntity(id = "LAtag00003", name = "Bravo", isLocal = true),
            ArtistEntity(id = "LAtag00004", name = "Alpha", isLocal = true),
            ArtistEntity(id = "LAtag00005", name = "Foxtrot", isLocal = true),
        )

        /** Local songs with track numbers and a year-only date. Track numbers differ in digits. */
        val tagSongs = listOf(
            TestSong(
                SongEntity(
                    id = "local-tango", title = "Tango", localPath = "/music/tango.flac", isLocal = true,
                    inLibrary = day(4), year = 1999, dateModified = day(-7), trackNumber = 10,
                ),
                artists = listOf("LAtag00001" to 0),
                playCounts = listOf(9),
            ),
            TestSong(
                SongEntity(
                    id = "local-sierra", title = "Sierra", localPath = "/music/sierra.flac", isLocal = true,
                    inLibrary = day(6), year = 2015, dateModified = day(-8), trackNumber = 9,
                    // Downloaded at the same time as local-romeo.
                    dateDownload = day(15),
                ),
                artists = listOf("LAtag00002" to 0),
            ),
            TestSong(
                SongEntity(
                    id = "local-romeo", title = "romeo", localPath = "/music/romeo.flac", isLocal = true,
                    inLibrary = day(7), date = LocalDateTime.of(2005, 3, 3, 0, 0), dateModified = day(-9),
                    trackNumber = 2, dateDownload = day(15),
                ),
                artists = listOf("LAtag00003" to 0),
                playCounts = listOf(5),
            ),
            TestSong(
                SongEntity(
                    id = "local-quebec", title = "Quebec", localPath = "/music/quebec.flac", isLocal = true,
                    inLibrary = day(9), trackNumber = 12,
                ),
                artists = listOf("LAtag00004" to 0),
            ),
        )

        /**
         * Only in the playlist: the local songs order treats dates before 1971 as missing, which is
         * to be fixed with its own test.
         */
        val oldTagSong = TestSong(
            SongEntity(
                id = "local-uniform", title = "Uniform", localPath = "/music/uniform.flac", isLocal = true,
                inLibrary = day(1), year = 1962,
            ),
            artists = listOf("LAtag00005" to 0),
        )

        fun isBefore1971(song: Song): Boolean =
            releaseKey(song.song)?.let { it < ReleaseKey(1971, 0) } == true

        val localSongs: List<Song> = (baseSongs + tagSongs).map { it.toSong(baseArtists + tagArtists) }
            .filterNot(::isBefore1971)

        val playlistSongs: List<PlaylistSong> = (baseSongs + tagSongs + oldTagSong).mapIndexed { position, testSong ->
            PlaylistSong(
                PlaylistSongMap(playlistId = "pl", songId = testSong.entity.id, position = position),
                testSong.toSong(baseArtists + tagArtists),
            )
        }
    }
}
