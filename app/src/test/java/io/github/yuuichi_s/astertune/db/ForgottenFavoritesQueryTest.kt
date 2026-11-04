package io.github.yuuichi_s.astertune.db

import android.app.Application
import io.github.yuuichi_s.astertune.db.entities.Event
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ForgottenFavoritesQueryTest {
    @Test
    fun defaultCutoffUsesTheSameLocalTimeAsStoredHistory() = runBlocking {
        val database = createEmptyTestDatabase()
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT-08:00"))
            val id = "stillRecent"
            database.insert(SongEntity(id = id, title = id, localPath = null))
            database.insert(
                Event(
                    songId = id,
                    timestamp = LocalDateTime.now().minusDays(30).plusHours(4),
                    playTime = 10_000,
                )
            )

            assertEquals(emptyList<String>(), database.forgottenFavorites().first().map { it.id })
        } finally {
            TimeZone.setDefault(originalZone)
            database.close()
        }
    }

    @Test
    fun includesEligibleSongsAndKeepsTheMostPlayedWithinTheLimit() = runBlocking {
        val database = createEmptyTestDatabase()
        try {
            val now = LocalDateTime.of(2026, 11, 4, 0, 0)
            val cutoff = now.minusDays(30)
            val old = cutoff.minusSeconds(1)
            val recent = cutoff.plusSeconds(1)
            listOf("stopped", "reduced", "atThreshold", "atCutoff", "recentOnly", "unplayed").forEach { id ->
                database.insert(SongEntity(id = id, title = id, localPath = null))
            }
            listOf("stopped", "reduced", "atThreshold", "atCutoff").forEach { id ->
                database.insert(Event(songId = id, timestamp = old, playTime = 10_000))
            }
            database.insert(Event(songId = "reduced", timestamp = recent, playTime = 1_000))
            database.insert(Event(songId = "atThreshold", timestamp = recent, playTime = 1_000))
            database.insert(Event(songId = "atThreshold", timestamp = recent.plusSeconds(1), playTime = 1_000))
            database.insert(Event(songId = "atCutoff", timestamp = cutoff, playTime = 2_000))
            database.insert(Event(songId = "recentOnly", timestamp = recent, playTime = 1_000))

            val nowMillis = now.toInstant(ZoneOffset.UTC).toEpochMilli()
            val mostPlayed = setOf("stopped", "reduced")
            val candidates = database.forgottenFavorites(nowMillis).first()
            assertEquals(mostPlayed, candidates.map { it.id }.toSet())

            database.awaitTransaction {
                repeat(100) { index ->
                    val id = "shortPlay$index"
                    insert(SongEntity(id = id, title = id, localPath = null))
                    insert(Event(songId = id, timestamp = old, playTime = 1_000))
                }
            }
            val limitedIds = database.forgottenFavorites(nowMillis).first().map { it.id }
            assertEquals(100, limitedIds.size)
            assertTrue(limitedIds.containsAll(mostPlayed))
        } finally {
            database.close()
        }
    }
}
