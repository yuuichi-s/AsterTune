package io.github.yuuichi_s.astertune.db

import android.app.Application
import androidx.room.Room
import io.github.yuuichi_s.astertune.db.entities.QueueEntity
import io.github.yuuichi_s.astertune.db.entities.QueueSongMap
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

/**
 * Verifies which stored queues [DatabaseDao.updateAllQueues] keeps and which ones
 * [DatabaseDao.readQueue] reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class QueueDaoTest {
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
    fun updateAllQueuesKeepsOtherQueuesWithSongs() = runBlocking {
        val hidden = storeQueue(id = 1, index = 0)
        val open = storeQueue(id = 2, index = 1)

        database.updateAllQueues(listOf(open.copy(title = "renamed", index = 5)), keepOtherQueues = true)

        assertEquals(
            listOf(hidden, open.copy(title = "renamed", index = 5)),
            database.getAllQueues().first()
        )

        val openQueues = database.readQueue(maxQueues = 1).first
        assertEquals(listOf(2L), openQueues.map { it.id })
        assertEquals("renamed", openQueues.single().title)
        assertEquals(5, openQueues.single().index)
        assertEquals(listOf("song2"), openQueues.single().queue.map { it.id })

        val expandedQueues = database.readQueue(maxQueues = 2).first
        assertEquals(listOf(1L, 2L), expandedQueues.map { it.id })
        assertEquals(listOf(listOf("song1"), listOf("song2")), expandedQueues.map { q -> q.queue.map { it.id } })
    }

    @Test
    fun updateAllQueuesDeletesOtherQueuesWithoutSongs() = runBlocking {
        val empty = QueueEntity(id = 1, title = "empty", index = 0)
        database.insert(empty)
        val open = storeQueue(id = 2, index = 1)

        database.updateAllQueues(listOf(open), keepOtherQueues = true)

        assertEquals(listOf(open), database.getAllQueues().first())
    }

    @Test
    fun updateAllQueuesWithoutKeepingDeletesOtherQueues() = runBlocking {
        storeQueue(id = 1, index = 0)
        val open = storeQueue(id = 2, index = 1)

        database.updateAllQueues(listOf(open.copy(index = 0)), keepOtherQueues = false)

        assertEquals(listOf(open.copy(index = 0)), database.getAllQueues().first())
    }

    @Test
    fun readQueueReadsLastQueuesWithSongs() = runBlocking {
        storeQueue(id = 40, index = 2)
        storeQueue(id = 70, index = 0)
        storeQueue(id = 10, index = 1)
        database.insert(QueueEntity(id = 20, title = "empty", index = 3))

        val queues = database.readQueue(maxQueues = 2).first

        assertEquals(listOf(10L to 1, 40L to 2), queues.map { it.id to it.index })
    }

    @Test
    fun readQueueRestoresSongsAndResumeState() = runBlocking {
        storeQueue(
            QueueEntity(
                id = 50,
                title = "saved queue",
                shuffled = true,
                queuePos = 1,
                lastSongPos = 123_456,
                index = 7,
                playlistId = "playlist",
            ),
            listOf(
                QueueSongMap(queueId = 50, songId = "song_c", index = 2, shuffledIndex = 1),
                QueueSongMap(queueId = 50, songId = "song_a", index = 0, shuffledIndex = 2),
                QueueSongMap(queueId = 50, songId = "song_b", index = 1, shuffledIndex = 0),
            ),
        )

        val queue = database.readQueue().first.single()

        assertEquals(50L, queue.id)
        assertEquals("saved queue", queue.title)
        assertEquals(7, queue.index)
        assertEquals("playlist", queue.playlistId)
        assertEquals(listOf("song_a", "song_b", "song_c"), queue.queue.map { it.id })
        assertEquals(listOf(2, 0, 1), queue.queue.map { it.shuffleIndex })
        assertEquals(true, queue.shuffled)
        assertEquals(listOf("song_b", "song_c", "song_a"), queue.getCurrentQueueShuffled().map { it.id })
        assertEquals(1, queue.queuePos)
        assertEquals("song_b", queue.getCurrentSong()?.id)
        assertEquals(123_456L, queue.lastSongPos)
    }

    private fun storeQueue(id: Long, index: Int): QueueEntity = storeQueue(
        QueueEntity(id = id, title = "queue$id", index = index),
        listOf(QueueSongMap(queueId = id, songId = "song$id", index = 0, shuffledIndex = 0)),
    )

    private fun storeQueue(queue: QueueEntity, songMaps: List<QueueSongMap>): QueueEntity {
        database.insert(queue)
        songMaps.forEach { map ->
            database.insert(SongEntity(id = map.songId, title = map.songId, localPath = null))
            database.insert(map)
        }
        return queue
    }
}
