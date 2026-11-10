package io.github.yuuichi_s.astertune.utils

import android.app.Application
import io.github.yuuichi_s.astertune.constants.ArtistFilter
import io.github.yuuichi_s.astertune.constants.ArtistSortType
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.createEmptyTestDatabase
import io.github.yuuichi_s.astertune.db.entities.AlbumEntity
import io.github.yuuichi_s.astertune.db.entities.Artist
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.SongArtistMap
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ArtistThumbnailFlowTest {
    private lateinit var database: MusicDatabase
    private val firstPath = "/storage/music/a.mp3"
    private val selectedPath = "/storage/music/b.mp3"
    private val albumPath = "/storage/music/c.mp3"
    private val remoteUrl = "https://example.com/remote.jpg"

    @Before
    fun setUp() {
        database = createEmptyTestDatabase()
        database.insert(ArtistEntity("LAlocal", "Local", isLocal = true))
        database.insert(ArtistEntity("UCremote", "Remote", thumbnailUrl = remoteUrl))
        database.insert(AlbumEntity("LBalbum", title = "Album", thumbnailUrl = albumPath, songCount = 3, duration = 300, isLocal = true))
        listOf(firstPath, selectedPath, albumPath).forEachIndexed { index, path ->
            database.insert(SongEntity(
                id = "local$index", title = "Song $index", thumbnailUrl = path, localPath = path,
                isLocal = true, inLibrary = LocalDateTime.now(), albumId = "LBalbum",
            ))
            database.insert(SongArtistMap("local$index", "LAlocal", 0))
        }
        database.insert(SongEntity("remote", "Remote song", localPath = null, inLibrary = LocalDateTime.now()))
        database.insert(SongArtistMap("remote", "UCremote", 0))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun artists() = database.artists(ArtistFilter.LIBRARY, ArtistSortType.NAME, false)

    private suspend fun Channel<List<Artist>>.awaitRows(predicate: (List<Artist>) -> Boolean): List<Artist> =
        withTimeout(5_000) {
            var rows = receive()
            while (!predicate(rows)) rows = receive()
            rows
        }

    @Test
    fun selectedArtworkFollowsSongExclusionAndRestorationWithoutChangingSavedChoice() = runBlocking<Unit> {
        database.update(database.artistById("LAlocal")!!.copy(thumbnailUrl = selectedPath))
        val rows = Channel<List<Artist>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { artists().withArtistThumbnails(database).collect(rows::send) }
        fun List<Artist>.local() = firstOrNull { it.id == "LAlocal" }
        try {
            rows.awaitRows { it.local()?.artist?.thumbnailUrl == selectedPath }
            database.disableLocalSong("local1")
            val remaining = rows.awaitRows { it.local()?.songCount == 2 && it.local()?.artist?.thumbnailUrl == albumPath }
            assertEquals(remoteUrl, remaining.single { it.id == "UCremote" }.artist.thumbnailUrl)
            assertEquals(selectedPath, database.artistById("LAlocal")!!.thumbnailUrl)

            database.disableLocalSong("local0")
            database.disableLocalSong("local2")
            rows.awaitRows { it.map { row -> row.id } == listOf("UCremote") }

            val song = database.song("local1").first()!!.song
            database.update(song.copy(localPath = selectedPath, inLibrary = LocalDateTime.now()))
            rows.awaitRows { it.local()?.songCount == 1 && it.local()?.artist?.thumbnailUrl == selectedPath }
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun albumArtworkUpdatesWithoutArtistRowsChanging() = runBlocking<Unit> {
        val original = artists().first()
        val rows = Channel<List<Artist>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { flowOf(original).withArtistThumbnails(database).collect(rows::send) }
        fun List<Artist>.localThumbnail() = single { it.id == "LAlocal" }.artist.thumbnailUrl
        try {
            rows.awaitRows { it.localThumbnail() == albumPath }
            database.update(database.albumById("LBalbum")!!.copy(thumbnailUrl = firstPath))
            rows.awaitRows { it.localThumbnail() == firstPath }
            assertNull(database.artistById("LAlocal")!!.thumbnailUrl)
        } finally {
            job.cancelAndJoin()
        }
    }
}
