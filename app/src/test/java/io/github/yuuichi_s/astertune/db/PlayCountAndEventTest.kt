package io.github.yuuichi_s.astertune.db

import android.app.Application
import io.github.yuuichi_s.astertune.db.entities.Event
import io.github.yuuichi_s.astertune.db.entities.PlayCountEntity
import io.github.yuuichi_s.astertune.db.entities.SongEntity
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
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Verifies that a play adds one to the current month's play count without resetting it, and that the
 * play time of a listen history entry can be updated through the ID returned when it was inserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class PlayCountAndEventTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = createEmptyTestDatabase()
        database.insert(SongEntity(id = SONG, title = SONG, localPath = null))
        database.insert(SongEntity(id = OTHER_SONG, title = OTHER_SONG, localPath = null))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun incrementPlayCountCreatesAndIncrementsCurrentMonth() = runBlocking {
        val now = LocalDateTime.now().atOffset(ZoneOffset.UTC)
        database.incrementPlayCount(SONG)
        assertEquals(1, database.getPlayCountByMonth(SONG, now.year, now.monthValue).first())
        database.incrementPlayCount(SONG)
        assertEquals(2, database.getPlayCountByMonth(SONG, now.year, now.monthValue).first())
    }

    @Test
    fun incrementPlayCountKeepsExistingCountAndOtherRows() = runBlocking {
        val now = LocalDateTime.now().atOffset(ZoneOffset.UTC)
        database.insert(PlayCountEntity(SONG, now.year, now.monthValue, 5))
        database.insert(PlayCountEntity(SONG, 2000, 1, 3))
        database.insert(PlayCountEntity(OTHER_SONG, now.year, now.monthValue, 7))

        database.incrementPlayCount(SONG)

        assertEquals(6, database.getPlayCountByMonth(SONG, now.year, now.monthValue).first())
        assertEquals(3, database.getPlayCountByMonth(SONG, 2000, 1).first())
        assertEquals(7, database.getPlayCountByMonth(OTHER_SONG, now.year, now.monthValue).first())
    }

    @Test
    fun updateEventPlayTimeChangesOnlyTheInsertedEvent() {
        val time = LocalDateTime.of(2026, 1, 1, 0, 0)
        val firstId = database.insert(Event(songId = SONG, timestamp = time, playTime = 10_000))
        val secondId = database.insert(Event(songId = SONG, timestamp = time.plusMinutes(5), playTime = 20_000))
        assertTrue(firstId > 0)
        assertTrue(secondId > 0)

        database.updateEventPlayTime(firstId, 90_000)

        assertEquals(listOf(90_000L, 20_000L), database.eventsBySong(SONG).map { it.playTime })
    }

    private companion object {
        const val SONG = "song"
        const val OTHER_SONG = "other_song"
    }
}
