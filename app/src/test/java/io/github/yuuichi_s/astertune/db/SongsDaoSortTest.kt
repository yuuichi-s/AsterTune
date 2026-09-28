package io.github.yuuichi_s.astertune.db

import android.app.Application
import androidx.room.Room
import io.github.yuuichi_s.astertune.constants.SongSortType
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

/** Runs the real Room queries on the JVM, without starting the Hilt application. */
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun songsByNameReturnsOnlyLibrarySongsInTitleOrder() = runBlocking {
        val added = LocalDateTime.of(2026, 1, 1, 0, 0)
        database.insert(SongEntity(id = "b", title = "bravo", localPath = null, inLibrary = added))
        database.insert(SongEntity(id = "c", title = "Charlie", localPath = null, inLibrary = added))
        database.insert(SongEntity(id = "a", title = "Alpha", localPath = null, inLibrary = added))
        database.insert(SongEntity(id = "x", title = "Aardvark", localPath = null))

        val ascending = database.songs(SongSortType.NAME, descending = false).first().map { it.id }
        val descending = database.songs(SongSortType.NAME, descending = true).first().map { it.id }

        assertEquals(listOf("a", "b", "c"), ascending)
        assertEquals(listOf("c", "b", "a"), descending)
    }
}
