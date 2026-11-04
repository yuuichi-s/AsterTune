package io.github.yuuichi_s.astertune.ui.dialog

import android.app.Application
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import io.github.yuuichi_s.astertune.constants.ScannerM3uMatchCriteria
import io.github.yuuichi_s.astertune.db.createEmptyTestDatabase
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.SongArtistMap
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ImportM3uTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun importsExactVideoIdsAndLocalPathsAndRejectsMismatchedMetadata() = runBlocking {
        val database = createEmptyTestDatabase()
        try {
            val videoId = "abcdefghijk"
            val localId = "localSong"
            val noArtistId = "noArtist"
            val localPath = "/storage/emulated/0/Music/A, B.mp3"
            val artist = ArtistEntity("m3u-test-artist", "Artist")
            database.insert(SongEntity(id = videoId, title = "Video title", localPath = null))
            database.insert(SongEntity(id = "titleMatch", title = "Title from file", localPath = null))
            database.insert(SongEntity(id = noArtistId, title = "Track without an artist", localPath = null))
            database.insert(SongEntity(id = localId, title = "Updated local title", localPath = localPath, isLocal = true))
            database.insert(artist)
            database.insert(SongArtistMap(songId = "titleMatch", artistId = artist.id, position = 0))
            val rejectedEntry = "#EXTINF:180,Different artist - Title from file"
            val file = files.newFile("playlist.m3u").apply {
                writeText(
                    """
                    #EXTM3U
                    #EXTINF:180,Artist - Title from file
                    https://youtu.be/$videoId?si=share
                    #EXTINF:180,Artist - Title from file
                    https://music.youtube.com/watch?si=share&v=$videoId
                    #EXTINF:180,Artist - Title from file
                    https://www.youtube.com/watch?v=$videoId&si=share
                    #EXTINF:180,Artist - Title from file
                    https://youtube.com.example/watch?v=$videoId
                    $rejectedEntry
                    https://youtu.be/lmnopqrstuv
                    #EXTINF:180,Previous artist - Previous local title
                    $localId, $localPath
                    #EXTINF:180, - Track without an artist
                    https://youtube.com/watch?v=lmnopqrstuv
                    #EXTINF:180,Track without an artist
                    https://youtube.com/watch?v=lmnopqrstuv
                    """.trimIndent()
                )
            }
            val (songs, rejected) = loadM3u(
                RuntimeEnvironment.getApplication(),
                database,
                SnackbarHostState(),
                Uri.fromFile(file),
                matchStrength = ScannerM3uMatchCriteria.LEVEL_2,
            )

            assertEquals(
                listOf(videoId, videoId, videoId, "titleMatch", localId, noArtistId, noArtistId),
                songs.map { it.id },
            )
            assertEquals(listOf(rejectedEntry), rejected)
        } finally {
            database.close()
        }
    }
}
