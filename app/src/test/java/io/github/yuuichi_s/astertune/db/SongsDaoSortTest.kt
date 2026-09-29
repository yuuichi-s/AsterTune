package io.github.yuuichi_s.astertune.db

import android.app.Application
import androidx.room.Room
import io.github.yuuichi_s.astertune.constants.SongSortType
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.PlayCountEntity
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongArtistMap
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Sorting of [SongsDao.songs], [SongsDao.likedSongs] and [SongsDao.downloadSongs] on the real Room
 * queries, without starting the Hilt application.
 *
 * Every sort type is checked in both directions against the same test data: the returned set, the
 * order of the sort key, and the position of rows without a key. The order among rows with equal
 * keys is not checked, because the queries have no second ordering term.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SongsDaoSortTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = MusicDatabase(
            Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), InternalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        )
        artists.forEach { database.insert(it) }
        songs.forEach { testSong ->
            database.insert(testSong.entity)
            testSong.artists.forEach { (artistId, position) ->
                database.insert(SongArtistMap(testSong.entity.id, artistId, position))
            }
            testSong.playCounts.forEachIndexed { month, count ->
                database.insert(PlayCountEntity(testSong.entity.id, year = 2026, month = month + 1, count = count))
            }
        }
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

    /**
     * Guards the test data: an order check passes by accident when the rows sorted by another column
     * come out in an order that also satisfies the expected key.
     */
    @Test
    fun testDataSeparatesEverySortKeyFromOtherColumns() = runBlocking {
        val failures = mutableListOf<String>()
        for (entry in listOf(songsEntry, likedSongsEntry, downloadSongsEntry)) {
            val rows = entry.query(SongSortType.CREATE_DATE, false).first()
            for (sortType in SongSortType.entries) {
                val column = entry.sortColumn(sortType)
                for (other in Column.entries) {
                    // A column with one value on every row gives no order to mistake for the key.
                    if (other == column || rows.filter { other.appliesTo(it) }.map { other.key(it) }.distinct().size == 1) continue
                    if (!hasInvertedPair(rows, column, other)) {
                        failures += "${entry.name} $sortType: no rows ordered oppositely by $other"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun checkEntry(entry: Entry) = runBlocking {
        val expectedIds = songs.map { it.entity }.filter(entry.inSet).map { it.id }.sorted()
        val failures = mutableListOf<String>()
        for (sortType in SongSortType.entries) {
            for (descending in listOf(false, true)) {
                val rows = entry.query(sortType, descending).first()
                val label = "${entry.name} $sortType descending=$descending"

                // Sorting the ids also compares the counts, so a row returned twice is caught.
                val actualIds = rows.map { it.id }.sorted()
                if (actualIds != expectedIds) {
                    failures += "$label: expected ids $expectedIds but was $actualIds"
                }

                // SQLite sorts NULL before any value, and descending reverses the ascending list, so a
                // row without a key comes first ascending and last descending. This records the current
                // behavior; if it is changed on purpose, update this expectation in the same change.
                val column = entry.sortColumn(sortType)
                val ascending = if (descending) rows.asReversed() else rows
                val keys = ascending.filter { column.appliesTo(it) }.map { column.key(it) }
                if (keys.zipWithNext().any { (a, b) -> compareKeys(a, b) > 0 }) {
                    failures += "$label: ${rows.map { it.id }} is not ordered by $column $keys"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private class Entry(
        val name: String,
        val query: (SongSortType, Boolean) -> Flow<List<Song>>,
        val inSet: (SongEntity) -> Boolean,
        val sortColumn: (SongSortType) -> Column,
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

    private fun librarySortColumn(sortType: SongSortType): Column = when (sortType) {
        SongSortType.CREATE_DATE -> Column.IN_LIBRARY
        SongSortType.MODIFIED_DATE -> Column.DATE_MODIFIED
        SongSortType.RELEASE_DATE -> Column.RELEASE_DATE
        SongSortType.NAME -> Column.TITLE
        SongSortType.ARTIST -> Column.ARTIST
        SongSortType.PLAY_COUNT -> Column.PLAY_COUNT
    }

    private fun likedSortColumn(sortType: SongSortType): Column = when (sortType) {
        SongSortType.CREATE_DATE -> Column.LIKED_DATE
        SongSortType.MODIFIED_DATE -> Column.DATE_MODIFIED
        SongSortType.RELEASE_DATE -> Column.RELEASE_DATE
        SongSortType.NAME -> Column.TITLE
        SongSortType.ARTIST -> Column.ARTIST
        SongSortType.PLAY_COUNT -> Column.PLAY_COUNT
    }

    /** Values a query could sort by, written from the spec rather than copied from the queries. */
    private enum class Column {
        IN_LIBRARY {
            override fun key(song: Song) = song.song.inLibrary
        },
        LIKED_DATE {
            override fun key(song: Song) = song.song.likedDate
        },
        RELEASE_DATE {
            override fun key(song: Song) =
                song.song.date ?: song.song.year?.let { LocalDateTime.of(it, 1, 1, 0, 0) }
        },
        DATE_MODIFIED {
            override fun key(song: Song) = song.song.dateModified
        },
        TITLE {
            override fun key(song: Song) = asciiLowercase(song.song.title)
        },

        /**
         * Checked only for songs with one artist: the order in which several artist names are
         * concatenated is not defined by SQLite.
         */
        ARTIST {
            override fun key(song: Song) = asciiLowercase(song.artists.single().name)
            override fun appliesTo(song: Song) = song.artists.size == 1
        },
        PLAY_COUNT {
            override fun key(song: Song) =
                song.playCount.orEmpty().takeIf { it.isNotEmpty() }?.sumOf { it.count.toLong() }
        },
        DATE_DOWNLOAD {
            override fun key(song: Song) = song.song.dateDownload
        },
        ROW_ID {
            override fun key(song: Song) = songs.indexOfFirst { it.entity.id == song.id }
        };

        abstract fun key(song: Song): Comparable<*>?
        open fun appliesTo(song: Song) = true
    }

    private class TestSong(
        val entity: SongEntity,
        /** Artist id to its position in the song's artist list. */
        val artists: List<Pair<String, Int>>,
        /** One play count row per month. */
        val playCounts: List<Int> = emptyList(),
    )

    private companion object {
        val base: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
        fun day(n: Long): LocalDateTime = base.plusDays(n)
        fun epochMillis(millis: Long): LocalDateTime =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

        /** `COLLATE NOCASE` and `LOWER()` fold only ASCII letters. */
        fun asciiLowercase(text: String) = buildString {
            text.forEach { append(if (it in 'A'..'Z') it.lowercaseChar() else it) }
        }

        @Suppress("UNCHECKED_CAST")
        fun compareKeys(a: Comparable<*>?, b: Comparable<*>?): Int = when {
            a == null && b == null -> 0
            a == null -> -1
            b == null -> 1
            else -> (a as Comparable<Any>).compareTo(b)
        }

        /** Whether some rows are ordered strictly one way by [column] and strictly the other way by [other]. */
        fun hasInvertedPair(rows: List<Song>, column: Column, other: Column): Boolean {
            val candidates = rows.filter { column.appliesTo(it) && other.appliesTo(it) }
            return candidates.any { a ->
                candidates.any { b ->
                    compareKeys(column.key(a), column.key(b)) < 0 && compareKeys(other.key(a), other.key(b)) > 0
                }
            }
        }

        val artists = listOf(
            // One artist per song, named in the reverse order of the song titles.
            ArtistEntity(id = "UCsingle01", name = "Victor"),
            ArtistEntity(id = "UCsingle02", name = "uniform"),
            ArtistEntity(id = "UCsingle03", name = "Tango"),
            ArtistEntity(id = "UCsingle05", name = "Sierra"),
            ArtistEntity(id = "UCsingle06", name = "Echo"),
            ArtistEntity(id = "UCsingle07", name = "romeo"),
            ArtistEntity(id = "UCsingle08", name = "Quebec"),
            ArtistEntity(id = "LAloc00001", name = "November", isLocal = true),
            ArtistEntity(id = "LAloc00002", name = "Whiskey", isLocal = true),
            ArtistEntity(id = "LAloc00003", name = "Xenon", isLocal = true),
            ArtistEntity(id = "LAloc00004", name = "Golf", isLocal = true),
            ArtistEntity(id = "UCdated_01", name = "Mango"),
            ArtistEntity(id = "UCdated_02", name = "Lemon"),
            ArtistEntity(id = "UCdated_03", name = "Kiwi"),
            // Id order, name order and position order all differ.
            ArtistEntity(id = "UCmulti_a", name = "Yankee"),
            ArtistEntity(id = "UCmulti_b", name = "Zulu"),
            ArtistEntity(id = "UCmulti_c", name = "Xray"),
        )

        /** YouTube Music songs have no date, modified date, year or track number, except the last rows. */
        val songs = listOf(
            TestSong(
                SongEntity(id = "ytm-oscar", title = "Oscar", localPath = null, inLibrary = day(8), dateDownload = epochMillis(0)),
                artists = listOf("UCsingle05" to 0),
                playCounts = listOf(999),
            ),
            TestSong(
                SongEntity(
                    id = "local-sakura", title = "さくら", localPath = "/music/sakura.flac", isLocal = true,
                    inLibrary = day(0), liked = true, likedDate = day(24),
                    date = LocalDateTime.of(1965, 5, 1, 0, 0), dateModified = day(-5),
                ),
                artists = listOf("LAloc00001" to 0),
                playCounts = listOf(1),
            ),
            TestSong(
                // Downloaded without being added to the library.
                SongEntity(id = "ytm-lima", title = "Lima", localPath = null, dateDownload = day(31)),
                artists = listOf("UCsingle03" to 0),
            ),
            TestSong(
                SongEntity(
                    id = "ytm-kilo", title = "Kilo", localPath = null, inLibrary = day(5),
                    liked = true, likedDate = day(20), dateDownload = day(28),
                ),
                artists = listOf("UCsingle01" to 0),
                playCounts = listOf(4, 6),
            ),
            TestSong(
                SongEntity(
                    id = "ytm-emile-upper", title = "Émile", localPath = null, inLibrary = day(1),
                    liked = true, likedDate = day(22), dateDownload = day(29),
                ),
                artists = listOf("UCsingle07" to 0),
                playCounts = listOf(9),
            ),
            TestSong(
                // Same title as ytm-kilo apart from case, added at the same time.
                SongEntity(id = "ytm-kilo-lower", title = "kilo", localPath = null, inLibrary = day(5)),
                artists = listOf("UCsingle02" to 0),
                playCounts = listOf(0),
            ),
            TestSong(
                SongEntity(
                    id = "local-juliet", title = "Juliet", localPath = "/music/juliet.flac", isLocal = true,
                    inLibrary = day(0), liked = true, likedDate = day(23),
                    date = LocalDateTime.of(2001, 6, 1, 0, 0), dateModified = day(-10),
                ),
                artists = listOf("LAloc00002" to 0),
            ),
            TestSong(
                SongEntity(
                    id = "ytm-mike", title = "Mike", localPath = null, inLibrary = day(2),
                    liked = true, likedDate = day(21), dateDownload = epochMillis(1),
                ),
                artists = listOf("UCmulti_b" to 0, "UCmulti_c" to 1, "UCmulti_a" to 2),
                playCounts = listOf(3),
            ),
            TestSong(
                // In none of the lists.
                SongEntity(id = "ytm-papa", title = "Papa", localPath = null),
                artists = listOf("UCsingle06" to 0),
            ),
            TestSong(
                SongEntity(id = "ytm-emile-lower", title = "émile", localPath = null, inLibrary = day(3)),
                artists = listOf("UCsingle08" to 0),
            ),
            TestSong(
                // Same date and modified date as local-juliet.
                SongEntity(
                    id = "local-hotel", title = "Hotel", localPath = "/music/hotel.flac", isLocal = true,
                    inLibrary = day(0), liked = true, likedDate = day(25),
                    date = LocalDateTime.of(2001, 6, 1, 0, 0), dateModified = day(-10),
                ),
                artists = listOf("LAloc00003" to 0),
                playCounts = listOf(2),
            ),
            TestSong(
                // Removed from the library.
                SongEntity(
                    id = "local-foxtrot", title = "Foxtrot", localPath = "/music/foxtrot.flac", isLocal = true,
                    date = LocalDateTime.of(2010, 1, 1, 0, 0), dateModified = day(-3),
                ),
                artists = listOf("LAloc00004" to 0),
            ),
            // Remote songs never get a date or a modified date today. Without these downloaded rows,
            // every song in downloadSongs() would tie on both, and sorting by another column would pass.
            TestSong(
                SongEntity(
                    id = "ytm-dated-bravo", title = "Bravo", localPath = null, dateDownload = day(11),
                    date = LocalDateTime.of(2020, 1, 1, 0, 0), dateModified = day(-3),
                ),
                artists = listOf("UCdated_02" to 0),
            ),
            TestSong(
                SongEntity(
                    id = "ytm-dated-alfa", title = "Alfa", localPath = null, dateDownload = day(10),
                    date = LocalDateTime.of(2010, 1, 1, 0, 0), dateModified = day(-1),
                ),
                artists = listOf("UCdated_01" to 0),
            ),
            TestSong(
                SongEntity(
                    id = "ytm-dated-delta", title = "Delta", localPath = null, dateDownload = day(12),
                    date = LocalDateTime.of(2000, 1, 1, 0, 0), dateModified = day(-2),
                ),
                artists = listOf("UCdated_03" to 0),
            ),
        )
    }
}
