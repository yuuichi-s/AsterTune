package io.github.yuuichi_s.astertune.db

import android.app.Application
import androidx.room.Room
import io.github.yuuichi_s.astertune.db.entities.AlbumEntity
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.SongAlbumMap
import io.github.yuuichi_s.astertune.db.entities.SongArtistMap
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Order and years of a local artist's albums, which also decide the album whose artwork the
 * artist page shows when the artist has none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class LocalArtistAlbumsQueryTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = MusicDatabase(
            Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), InternalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        )
        database.insert(ArtistEntity(id = "LAartist", name = "artist", isLocal = true))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun album(id: String, title: String = id, year: Int? = null) =
        database.insert(AlbumEntity(id = id, title = title, year = year, songCount = 0, duration = 0, isLocal = true))

    /**
     * Inserts a library song for the artist.
     *
     * [mappedAlbumId] adds a song-album mapping so the album appears in the artist's album list.
     */
    private fun song(id: String, year: Int? = null, albumId: String? = null, mappedAlbumId: String? = null) {
        database.insert(
            SongEntity(
                id = id, title = id, year = year, albumId = albumId, isLocal = true,
                inLibrary = LocalDateTime.now(), localPath = null,
            )
        )
        database.insert(SongArtistMap(id, "LAartist", 0))
        mappedAlbumId?.let { database.insert(SongAlbumMap(id, it, 0)) }
    }

    private suspend fun albums() = database.artistSongAlbums("LAartist").first()

    @Test
    fun albums_newestFirstAndWithoutYearLast() = runBlocking {
        album("none")
        album("old", year = 2020)
        album("new", year = 2025)
        song("a", mappedAlbumId = "none")
        song("b", mappedAlbumId = "old")
        song("c", mappedAlbumId = "new")
        assertEquals(listOf("new", "old", "none"), albums().map { it.album.id })
    }

    @Test
    fun albums_sameYearByTitleIgnoringCase() = runBlocking {
        album("x", title = "Beta", year = 2025)
        album("y", title = "alpha", year = 2025)
        song("a", mappedAlbumId = "x")
        song("b", mappedAlbumId = "y")
        assertEquals(listOf("y", "x"), albums().map { it.album.id })
    }

    @Test
    fun albums_ownYearWinsOverSongYears() = runBlocking {
        album("x", year = 2020)
        song("a", year = 2025, albumId = "x", mappedAlbumId = "x")
        assertEquals(2020, albums().single().sortYear)
    }

    @Test
    fun albums_yearFromSongAlbumIdOnly() = runBlocking {
        album("x")
        song("listed", mappedAlbumId = "x")
        song("a", year = 2023, albumId = "x")
        song("b", year = 2024, albumId = "x")
        assertEquals(2024, albums().single().sortYear)
    }

    @Test
    fun albums_yearFromSongAlbumMapOnly() = runBlocking {
        album("x")
        song("a", year = 2022, mappedAlbumId = "x")
        assertEquals(2022, albums().single().sortYear)
    }

    @Test
    fun albums_songsOfOtherAlbumsAreIgnored() = runBlocking {
        album("x")
        album("y")
        song("a", year = 2030, albumId = "y")
        song("b", year = 2021, albumId = "x", mappedAlbumId = "x")
        assertEquals(2021, albums().single().sortYear)
    }
}
