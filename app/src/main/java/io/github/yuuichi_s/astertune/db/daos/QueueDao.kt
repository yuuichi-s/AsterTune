package io.github.yuuichi_s.astertune.db.daos

import androidx.media3.common.C
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.yuuichi_s.astertune.db.entities.CurrentQueueEntity
import io.github.yuuichi_s.astertune.db.entities.QueueEntity
import io.github.yuuichi_s.astertune.db.entities.QueueSong
import io.github.yuuichi_s.astertune.db.entities.QueueSongMap
import io.github.yuuichi_s.astertune.models.MultiQueueObject
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Dao
interface QueueDao {

    // region Gets
    @Query("SELECT * from queue ORDER BY `index`")
    fun getAllQueues(): Flow<List<QueueEntity>>

    @Transaction
    @Query("SELECT song.*, queue_song_map.shuffledIndex from queue_song_map JOIN song ON queue_song_map.songId = song.id WHERE queueId = :queueId ORDER BY `index`")
    fun queueSongs(queueId: Long): List<QueueSong>

    @Query("SELECT * FROM queue WHERE id = :queueId")
    fun queueById(queueId: Long): QueueEntity?

    @Query("SELECT * FROM queue_song_map WHERE queueId = :queueId ORDER BY `index`")
    fun queueSongMaps(queueId: Long): List<QueueSongMap>

    /** Reads a queue, its song mappings, and its songs in one database transaction. */
    @Transaction
    fun storedQueue(queueId: Long): StoredQueue? {
        val queue = queueById(queueId) ?: return null
        return StoredQueue(queue, queueSongMaps(queueId), queueSongs(queueId))
    }

    @Query("SELECT * FROM queue ORDER BY `index`")
    fun queuesInOrder(): List<QueueEntity>

    @Query("SELECT * FROM current_queue WHERE id = 0")
    fun savedCurrentQueue(): CurrentQueueEntity?

    /**
     * Returns stored queues that have songs, at most [maxQueues], in queue order.
     * The current queue is always included, and the newest other queues fill the rest.
     *
     * Repairs indices and positions as described in [repairQueue] without changing the database.
     * The caller must save repaired queues after adopting them.
     */
    suspend fun readQueue(maxQueues: Int = Int.MAX_VALUE): OpenQueues =
        withContext(Dispatchers.IO) { openQueues(maxQueues) }

    @Transaction
    fun openQueues(maxQueues: Int): OpenQueues {
        val stored = queuesInOrder()
        val order = stored.map { it.id }
        val loader = RepairedQueueLoader(this)
        val currentId = chooseCurrentQueue(order, savedCurrentQueue()) { loader.load(it) != null }
        val openIds = HashSet<Long>()

        if (currentId != null && maxQueues > 0) openIds.add(currentId)
        for (id in order.asReversed()) {
            if (openIds.size >= maxQueues) break
            if (loader.load(id) != null) openIds.add(id)
        }

        val opened = order.filter { it in openIds }.map { loader.load(it)!! }
        val closedEnd = stored.filter { it.id !in openIds }.maxOfOrNull { it.index + 1 } ?: 0

        return OpenQueues(
            queues = opened.map { it.first },
            repairedIds = opened.filter { it.second }.mapTo(HashSet()) { it.first.id },
            currentQueueId = currentId,
            firstQueueIndex = maxOf(opened.firstOrNull()?.first?.index ?: 0, closedEnd),
        )
    }

    /**
     * Returns the current stored queue for resuming playback.
     * Repairs indices and positions as described in [repairQueue] without changing the database.
     */
    suspend fun getResumptionQueue(): MultiQueueObject? = withContext(Dispatchers.IO) { resumptionQueue() }

    @Transaction
    fun resumptionQueue(): MultiQueueObject? {
        val loader = RepairedQueueLoader(this)
        val order = queuesInOrder().map { it.id }
        val currentId = chooseCurrentQueue(order, savedCurrentQueue()) { loader.load(it) != null }
        return currentId?.let { loader.load(it)?.first }
    }

    /**
     * Chooses the current queue from the stored queues in [order].
     *
     * - Nothing saved, or the saved queue no longer exists: the last queue with songs
     * - Saved as none: none
     * - The saved queue has songs: that queue
     * - The saved queue has no songs: the nearest queue with songs above it, or none
     */
    fun chooseCurrentQueue(order: List<Long>, saved: CurrentQueueEntity?, hasSongs: (Long) -> Boolean): Long? {
        val savedIndex = if (saved == null) -1 else order.indexOf(saved.queueId ?: return null)
        if (savedIndex < 0) return order.lastOrNull(hasSongs)
        return (savedIndex downTo 0).map { order[it] }.firstOrNull(hasSongs)
    }

    /**
     * Builds the queue in [stored], repaired as described in [repairQueue].
     *
     * @return the queue and whether it was repaired, or null if it has no songs
     */
    fun toRepairedQueue(stored: StoredQueue): Pair<MultiQueueObject, Boolean>? {
        if (stored.songs.isEmpty()) return null
        val repaired = repairQueue(stored.queue, stored.maps)
        val q = repaired?.first ?: stored.queue
        // Song mappings and songs were read together in queue-index order.
        val shuffledIndices = repaired?.second?.map { it.shuffledIndex.toInt() }
        val queue = MultiQueueObject(
            id = q.id,
            title = q.title,
            queue = stored.songs.mapTo(ArrayList()) { it.song.toMediaMetadata() },
            shuffled = q.shuffled,
            queuePos = q.queuePos,
            lastSongPos = q.lastSongPos,
            index = q.index,
            playlistId = q.playlistId,
            shuffleOrder = stored.songs.mapIndexedTo(ArrayList()) { i, it ->
                shuffledIndices?.get(i) ?: it.shuffledIndex
            },
        )
        return queue to (repaired != null)
    }
    // endregion

    // region Inserts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queue: QueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queueSong: QueueSongMap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(currentQueue: CurrentQueueEntity)

    /** Saves [queueId] as the current queue. Null means there is no current queue. */
    fun saveCurrentQueue(queueId: Long?) = insert(CurrentQueueEntity(queueId = queueId))
    // endregion

    // region Updates
    @Update
    fun update(queue: QueueEntity)

    /**
     * Renumbers the queue entries in [songs] after entries have been removed from [queue].
     * [songs] must be ordered by index. Normal and shuffled indices become contiguous and
     * zero-based, preserving both orders.
     *
     * [QueueEntity.queuePos] refers to the unshuffled order before removal.
     * If the current song remains, the position follows that song.
     * Otherwise, it moves to the next remaining song, or the last one, and clears the saved
     * playback position. This also handles positions beyond the end after trailing songs
     * have been removed.
     *
     * @return the repaired queue and entries, or null if no repair is needed or [songs] is empty
     */
    fun repairQueue(queue: QueueEntity, songs: List<QueueSongMap>): Pair<QueueEntity, List<QueueSongMap>>? {
        if (songs.isEmpty()) return null
        val shuffleRank = songs.sortedBy { it.shuffledIndex }
            .withIndex()
            .associate { (rank, song) -> song.id to rank.toLong() }
        val consistent = queue.queuePos <= songs.lastIndex && songs.withIndex().all { (i, song) ->
            song.index == i.toLong() && song.shuffledIndex == shuffleRank.getValue(song.id)
        }
        if (consistent) return null

        val current = songs.indexOfFirst { it.index == queue.queuePos.toLong() }
        val repairedQueue = if (current >= 0) {
            queue.copy(queuePos = current)
        } else {
            val next = songs.indexOfFirst { it.index > queue.queuePos }
            queue.copy(queuePos = if (next >= 0) next else songs.lastIndex, lastSongPos = C.TIME_UNSET)
        }
        val repairedSongs = songs.mapIndexed { i, song ->
            song.copy(index = i.toLong(), shuffledIndex = shuffleRank.getValue(song.id))
        }
        return repairedQueue to repairedSongs
    }

    /**
     * Updates the supplied queues and deletes others.
     * [keepOtherQueues] retains other queues with songs.
     */
    @Transaction
    fun updateAllQueues(queues: List<QueueEntity>, keepOtherQueues: Boolean) {
        val ids = queues.map { it.id }
        if (keepOtherQueues) deleteEmptyQueuesExcept(ids) else deleteQueuesExcept(ids)
        queues.forEach { update(it) }
    }

    // endregion

    // region Deletes
    @Delete
    fun delete(mq: QueueEntity)

    @Query("DELETE FROM queue")
    fun deleteAllQueues()

    @Query("DELETE FROM queue_song_map WHERE queueId = :id")
    fun deleteAllQueueSongs(id: Long)

    @Query("DELETE FROM queue WHERE id = :id")
    fun deleteQueue(id: Long)

    @Transaction
    fun deleteQueueAndSaveCurrent(id: Long, currentQueueId: Long?) {
        deleteQueue(id)
        saveCurrentQueue(currentQueueId)
    }

    @Query("DELETE FROM queue WHERE id NOT IN (:ids) AND id NOT IN (SELECT queueId FROM queue_song_map)")
    fun deleteEmptyQueuesExcept(ids: List<Long>)

    @Query("DELETE FROM queue WHERE id NOT IN (:ids)")
    fun deleteQueuesExcept(ids: List<Long>)
    // endregion
}

/**
 * Result of [QueueDao.readQueue].
 *
 * @property currentQueueId null if there is no current queue
 * @property firstQueueIndex index to renumber the queues from, so that they follow the stored
 *     queues that were not opened
 */
data class OpenQueues(
    val queues: List<MultiQueueObject>,
    val repairedIds: Set<Long>,
    val currentQueueId: Long?,
    val firstQueueIndex: Int,
)

/** Loads stored queues with [QueueDao.toRepairedQueue], reading each at most once. */
private class RepairedQueueLoader(private val dao: QueueDao) {
    private val loaded = HashMap<Long, Pair<MultiQueueObject, Boolean>?>()

    /** Returns null for a queue that has no songs or does not exist. */
    fun load(id: Long): Pair<MultiQueueObject, Boolean>? =
        if (id in loaded) loaded[id]
        else dao.storedQueue(id)?.let(dao::toRepairedQueue).also { loaded[id] = it }
}

/** A stored queue with its song mappings and songs, both ordered by queue index. */
data class StoredQueue(
    val queue: QueueEntity,
    val maps: List<QueueSongMap>,
    val songs: List<QueueSong>,
)
