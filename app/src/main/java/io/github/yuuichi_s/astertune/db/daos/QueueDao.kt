package io.github.yuuichi_s.astertune.db.daos

import androidx.media3.common.C
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.yuuichi_s.astertune.db.entities.QueueEntity
import io.github.yuuichi_s.astertune.db.entities.QueueSong
import io.github.yuuichi_s.astertune.db.entities.QueueSongMap
import io.github.yuuichi_s.astertune.models.MultiQueueObject
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    /**
     * Returns the last [maxQueues] non-empty stored queues in queue order.
     *
     * Repairs indices and positions as described in [repairQueue] without changing the database.
     * The caller must save repaired queues after adopting them.
     *
     * @return the queues and the IDs of those that were repaired
     */
    suspend fun readQueue(maxQueues: Int = Int.MAX_VALUE): Pair<List<MultiQueueObject>, Set<Long>> {
        val resultQueues = ArrayList<MultiQueueObject>()
        val repairedIds = HashSet<Long>()
        val queues = getAllQueues().first()

        for (queue in queues.asReversed()) {
            if (resultQueues.size >= maxQueues) break
            val stored = withContext(Dispatchers.IO) { storedQueue(queue.id) } ?: continue
            val (q, repaired) = toRepairedQueue(stored) ?: continue
            resultQueues.add(q)
            if (repaired) repairedIds.add(q.id)
        }

        resultQueues.reverse()
        return resultQueues to repairedIds
    }

    /**
     * Returns the most recent stored queue for resuming playback.
     * Repairs indices and positions as described in [repairQueue] without changing the database.
     */
    suspend fun getResumptionQueue(): MultiQueueObject? {
        val queue = getAllQueues().first().lastOrNull() ?: return null
        val stored = withContext(Dispatchers.IO) { storedQueue(queue.id) } ?: return null
        return toRepairedQueue(stored)?.first
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
            queue = stored.songs.mapIndexed { i, it ->
                val s = it.song.toMediaMetadata()
                s.shuffleIndex = shuffledIndices?.get(i) ?: it.shuffledIndex
                s
            }.toMutableList(),
            shuffled = q.shuffled,
            queuePos = q.queuePos,
            lastSongPos = q.lastSongPos,
            index = q.index,
            playlistId = q.playlistId
        )
        return queue to (repaired != null)
    }
    // endregion

    // region Inserts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queue: QueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queueSong: QueueSongMap)
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

    @Query("DELETE FROM queue WHERE id NOT IN (:ids) AND id NOT IN (SELECT queueId FROM queue_song_map)")
    fun deleteEmptyQueuesExcept(ids: List<Long>)

    @Query("DELETE FROM queue WHERE id NOT IN (:ids)")
    fun deleteQueuesExcept(ids: List<Long>)
    // endregion
}

/** A stored queue with its song mappings and songs, both ordered by queue index. */
data class StoredQueue(
    val queue: QueueEntity,
    val maps: List<QueueSongMap>,
    val songs: List<QueueSong>,
)
