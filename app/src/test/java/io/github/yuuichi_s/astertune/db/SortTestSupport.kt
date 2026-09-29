package io.github.yuuichi_s.astertune.db

import androidx.room.Room
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.PlayCountEntity
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongArtistMap
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.robolectric.RuntimeEnvironment
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * Fixture constraints shared by the DAO and in-memory sort tests:
 * - Insert parent rows before mapping rows; INSERT OR IGNORE does not ignore foreign-key failures.
 * - artists() keeps only YouTube or local artists.
 * - albums() uses song_album_map and album_artist_map; artists() uses song_artist_map.
 * - LIBRARY and DOWNLOADED require linked songs for albums and artists; LIKED uses bookmarkedAt.
 * - playlists() variant 0 includes bookmarked or local playlists; DOWNLOADED and localSongsOnly
 *   further restrict that set.
 * - Set PlayCountEntity.count, which defaults to -1, and use unique year/month keys per song.
 * - Use unique PlaylistSongMap.position values to avoid order ties.
 * - For multi-artist songs, make ID, name, and position orders differ.
 */

/**
 * An in-memory database holding [baseArtists] and [baseSongs]. Tests run under Robolectric with
 * `@Config(application = Application::class)` so that the Hilt application is not started.
 */
internal fun createTestDatabase(): MusicDatabase {
    val database = MusicDatabase(
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    )
    baseArtists.forEach { database.insert(it) }
    baseSongs.forEach { testSong ->
        database.insert(testSong.entity)
        testSong.artists.forEach { (artistId, position) ->
            database.insert(SongArtistMap(testSong.entity.id, artistId, position))
        }
        testSong.playCounts.forEachIndexed { month, count ->
            database.insert(PlayCountEntity(testSong.entity.id, year = 2026, month = month + 1, count = count))
        }
    }
    return database
}

/** A value a query could sort by, written from the spec rather than copied from the queries. */
internal interface SortKey<T> {
    fun key(row: T): Comparable<*>?

    /** Rows the key is checked on; the others may appear anywhere in the list. */
    fun appliesTo(row: T): Boolean = true
}

/**
 * Checks [query] for every sort type in both directions against [expectedIds] and [sortKey].
 * Equal-key order is not checked because DAO queries have no secondary `ORDER BY`.
 *
 * Returns one message per failure.
 */
internal suspend fun <T, S> sortFailures(
    name: String,
    sortTypes: List<S>,
    query: (S, Boolean) -> Flow<List<T>>,
    id: (T) -> String,
    expectedIds: List<String>,
    sortKey: (S) -> SortKey<T>,
): List<String> {
    val failures = mutableListOf<String>()
    for (sortType in sortTypes) {
        for (descending in listOf(false, true)) {
            val rows = query(sortType, descending).first()
            val label = "$name $sortType descending=$descending"
            setFailure(label, rows.map(id), expectedIds)?.let { failures += it }

            // A null key sorts first ascending. Reversing the result places it last descending.
            // This placement records current behavior rather than a spec.
            val key = sortKey(sortType)
            val ascending = if (descending) rows.asReversed() else rows
            val keys = ascending.filter { key.appliesTo(it) }.map { key.key(it) }
            if (keys.zipWithNext().any { (a, b) -> compareKeys(a, b) > 0 }) {
                failures += "$label: ${rows.map(id)} is not ordered by $key $keys"
            }
        }
    }
    return failures
}

/** Sorting the ids also compares the counts, so a row returned twice is caught. */
internal fun setFailure(label: String, actualIds: List<String>, expectedIds: List<String>): String? =
    if (actualIds.sorted() != expectedIds.sorted()) {
        "$label: expected ids ${expectedIds.sorted()} but was ${actualIds.sorted()}"
    } else {
        null
    }

/**
 * Guards the test data: an order check passes by accident when the rows sorted by another column
 * come out in an order that also satisfies the expected key. For every sort type, some [rows] must
 * be ordered one way by its key and the other way by each of [columns].
 */
internal fun <T, S> separationFailures(
    name: String,
    rows: List<T>,
    sortTypes: List<S>,
    sortKey: (S) -> SortKey<T>,
    columns: List<SortKey<T>>,
): List<String> {
    val failures = mutableListOf<String>()
    for (sortType in sortTypes) {
        val key = sortKey(sortType)
        for (other in columns) {
            // A column with fewer than two values gives no order to mistake for the key.
            if (other == key || rows.filter { other.appliesTo(it) }.map { other.key(it) }.distinct().size <= 1) continue
            if (!hasInvertedPair(rows, key, other)) {
                failures += "$name $sortType: no rows ordered oppositely by $other"
            }
        }
    }
    return failures
}

/** `COLLATE NOCASE` and `LOWER()` fold only ASCII letters. */
internal fun asciiLowercase(text: String) = buildString {
    text.forEach { append(if (it in 'A'..'Z') it.lowercaseChar() else it) }
}

@Suppress("UNCHECKED_CAST")
internal fun compareKeys(a: Comparable<*>?, b: Comparable<*>?): Int = when {
    a == null && b == null -> 0
    a == null -> -1
    b == null -> 1
    else -> (a as Comparable<Any>).compareTo(b)
}

private fun <T> hasInvertedPair(rows: List<T>, key: SortKey<T>, other: SortKey<T>): Boolean {
    val candidates = rows.filter { key.appliesTo(it) && other.appliesTo(it) }
    return candidates.any { a ->
        candidates.any { b ->
            compareKeys(key.key(a), key.key(b)) < 0 && compareKeys(other.key(a), other.key(b)) > 0
        }
    }
}

/**
 * Release date order: by year, then by the time within the year, with a year-only tag on January 1.
 * Shared by the SQL and the in-memory sort tests so that both follow one rule.
 */
internal data class ReleaseKey(val year: Long, val secondOfYear: Long) : Comparable<ReleaseKey> {
    override fun compareTo(other: ReleaseKey) = compareValuesBy(this, other, { it.year }, { it.secondOfYear })
}

internal fun releaseKey(song: SongEntity): ReleaseKey? =
    song.date?.let {
        ReleaseKey(it.year.toLong(), it.toEpochSecond(ZoneOffset.UTC) - LocalDateTime.of(it.year, 1, 1, 0, 0).toEpochSecond(ZoneOffset.UTC))
    } ?: song.year?.let { ReleaseKey(it.toLong(), 0) }

/** Song values a query could sort by. */
internal enum class SongColumn : SortKey<Song> {
    IN_LIBRARY {
        override fun key(row: Song) = row.song.inLibrary
    },
    LIKED_DATE {
        override fun key(row: Song) = row.song.likedDate
    },
    RELEASE_DATE {
        override fun key(row: Song) = releaseKey(row.song)
    },
    DATE_MODIFIED {
        override fun key(row: Song) = row.song.dateModified
    },
    TITLE {
        override fun key(row: Song) = asciiLowercase(row.song.title)
    },

    /**
     * Checked only for songs with one artist: the order in which several artist names are
     * concatenated is not defined by SQLite.
     */
    ARTIST {
        override fun key(row: Song) = asciiLowercase(row.artists.single().name)
        override fun appliesTo(row: Song) = row.artists.size == 1
    },
    PLAY_COUNT {
        override fun key(row: Song) =
            row.playCount.orEmpty().takeIf { it.isNotEmpty() }?.sumOf { it.count.toLong() }
    },
    DATE_DOWNLOAD {
        override fun key(row: Song) = row.song.dateDownload
    },
    ROW_ID {
        override fun key(row: Song) = baseSongs.indexOfFirst { it.entity.id == row.id }
    },
}

internal class TestSong(
    val entity: SongEntity,
    /** Artist id to its position in the song's artist list. */
    val artists: List<Pair<String, Int>>,
    /** One play count row per month. */
    val playCounts: List<Int> = emptyList(),
) {
    /** The [Song] a query would return, for tests that sort without a database. */
    fun toSong(artistPool: List<ArtistEntity> = baseArtists) = Song(
        song = entity,
        artists = artists.sortedBy { it.second }.map { (id, _) -> artistPool.single { it.id == id } },
        playCount = playCounts.mapIndexed { month, count ->
            PlayCountEntity(entity.id, year = 2026, month = month + 1, count = count)
        },
    )
}

private val base: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
internal fun day(n: Long): LocalDateTime = base.plusDays(n)
internal fun epochMillis(millis: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

internal fun baseSong(id: String): SongEntity = baseSongs.single { it.entity.id == id }.entity

internal val baseArtists = listOf(
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
    ArtistEntity(id = "LAloc00005", name = "Charlie", isLocal = true),
    ArtistEntity(id = "LAloc00006", name = "Alfred", isLocal = true),
    ArtistEntity(id = "UCdated_01", name = "Mango"),
    ArtistEntity(id = "UCdated_02", name = "Lemon"),
    ArtistEntity(id = "UCdated_03", name = "Kiwi"),
    // Id order, name order and position order all differ.
    ArtistEntity(id = "UCmulti_a", name = "Yankee"),
    ArtistEntity(id = "UCmulti_b", name = "Zulu"),
    ArtistEntity(id = "UCmulti_c", name = "Xray"),
)

/** Shared song fixtures; `ytm-dated-*` intentionally carry release and modified dates. */
internal val baseSongs = listOf(
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
    // Tags with a year only, which the scanner stores in year instead of date.
    TestSong(
        SongEntity(
            id = "local-india", title = "India", localPath = "/music/india.flac", isLocal = true,
            inLibrary = day(0), liked = true, likedDate = day(26), year = 2005, dateModified = day(-6),
        ),
        artists = listOf("LAloc00005" to 0),
        playCounts = listOf(7),
    ),
    TestSong(
        SongEntity(
            id = "local-yankee", title = "Yankee", localPath = "/music/yankee.flac", isLocal = true,
            inLibrary = day(0), year = 1968, dateModified = day(-4),
        ),
        artists = listOf("LAloc00006" to 0),
    ),
    // These dates make RELEASE_DATE and MODIFIED_DATE differ from each other and from insertion order.
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
