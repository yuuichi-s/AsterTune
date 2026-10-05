package io.github.yuuichi_s.astertune.db

import android.app.Application
import androidx.room.Room
import io.github.yuuichi_s.astertune.db.entities.Event
import io.github.yuuichi_s.astertune.db.entities.RelatedSongMap
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
 * Verifies that [DatabaseDao.quickPicks] and [DatabaseDao.relatedSongs]
 * return the expected songs without duplicates when source songs share related songs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class QuickPicksQueryTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = MusicDatabase(
            Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), InternalDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        )
        // quickPicks() also takes the first 10 songs as sources; keep these fillers there and
        // without related songs, so only the play history decides which sources count.
        (0 until 10).forEach { database.insert(song("filler$it")) }
        listOf(SOURCE_A, SOURCE_B, SOURCE_C, RELATED).forEach { database.insert(song(it)) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun relatedSongOfPlayedSourceIsPickedWhenUnplayedSourceWasSavedFirst() = runBlocking {
        database.insert(RelatedSongMap(songId = SOURCE_A, relatedSongId = RELATED))
        database.insert(RelatedSongMap(songId = SOURCE_B, relatedSongId = RELATED))
        play(SOURCE_B)

        assertEquals(listOf(RELATED), database.quickPicks().first().map { it.id })
    }

    @Test
    fun relatedSongOfUnplayedSourcesIsNotPicked() = runBlocking {
        database.insert(RelatedSongMap(songId = SOURCE_A, relatedSongId = RELATED))
        database.insert(RelatedSongMap(songId = SOURCE_B, relatedSongId = RELATED))

        assertEquals(emptyList<String>(), database.quickPicks().first().map { it.id })
    }

    @Test
    fun relatedSongOfSeveralPlayedSourcesIsPickedOnce() = runBlocking {
        database.insert(RelatedSongMap(songId = SOURCE_B, relatedSongId = RELATED))
        database.insert(RelatedSongMap(songId = SOURCE_C, relatedSongId = RELATED))
        play(SOURCE_B)
        play(SOURCE_C)

        assertEquals(listOf(RELATED), database.quickPicks().first().map { it.id })
    }

    @Test
    fun relatedSongsOfSourceIncludeSongsAlsoRelatedToEarlierSources() {
        database.insert(RelatedSongMap(songId = SOURCE_A, relatedSongId = RELATED))
        database.insert(RelatedSongMap(songId = SOURCE_B, relatedSongId = RELATED))

        assertEquals(listOf(RELATED), database.relatedSongs(SOURCE_B).map { it.id })
    }

    private fun play(songId: String) =
        database.insert(Event(songId = songId, timestamp = LocalDateTime.now(), playTime = 60_000))

    private fun song(id: String) = SongEntity(id = id, title = id, localPath = null)

    private companion object {
        // Sorted after the fillers, so none of them is among the first 10 songs
        const val SOURCE_A = "source_a"
        const val SOURCE_B = "source_b"
        const val SOURCE_C = "source_c"
        const val RELATED = "related"
    }
}
