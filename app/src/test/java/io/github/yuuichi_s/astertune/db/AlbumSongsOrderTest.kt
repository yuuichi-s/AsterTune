package io.github.yuuichi_s.astertune.db

import android.app.Application
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
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class AlbumSongsOrderTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = createEmptyTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun localAlbumUsesTitleWhenTracksAreUnnumberedAndKeepsSongRelations() = runBlocking {
        database.insert(AlbumEntity(id = "local", title = "Local", songCount = 4, duration = 0, isLocal = true))
        val artist = ArtistEntity(id = "artist", name = "Artist", isLocal = true)
        database.insert(artist)
        insertSong("local", "z", "Zeta", index = 0)
        insertSong("local", "m", "Middle", index = 1)
        insertSong("local", "a", "alpha", index = 1)
        insertSong("local", "excluded", "Aardvark", index = 2, inLibrary = false)
        database.insert(SongArtistMap("a", artist.id, 0))

        val songs = database.albumSongs("local").first()
        assertEquals(listOf("a", "m", "z"), songs.map { it.id })
        assertEquals(listOf(artist.id), songs.first().artists.map { it.id })
        assertEquals(songs, requireNotNull(database.albumWithSongs("local").first()).songs)
    }

    @Test
    fun onlineAlbumUsesStoredPositionsEvenForLinkedLocalSongs() = runBlocking {
        database.insert(AlbumEntity(id = "online", title = "Online", songCount = 2, duration = 0))
        insertSong("online", "a", "Alpha", index = 1, track = 1)
        insertSong("online", "z", "Zeta", index = 0, track = 2, isLocal = false, inLibrary = false)

        assertEquals(listOf("z", "a"), database.albumSongs("online").first().map { it.id })
    }

    private fun insertSong(
        albumId: String,
        id: String,
        title: String,
        index: Int,
        track: Int? = null,
        isLocal: Boolean = true,
        inLibrary: Boolean = true,
    ) {
        val song = SongEntity(
            id = id,
            title = title,
            trackNumber = track,
            isLocal = isLocal,
            inLibrary = if (inLibrary) LocalDateTime.of(2026, 1, 1, 0, 0) else null,
            localPath = if (isLocal && inLibrary) "/storage/music/$id.flac" else null,
        )
        database.insert(song)
        database.insert(SongAlbumMap(id, albumId, index))
    }
}
