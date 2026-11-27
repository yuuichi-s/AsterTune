package io.github.yuuichi_s.astertune.models

import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastSumBy
import androidx.media3.common.C
import io.github.yuuichi_s.astertune.db.entities.QueueEntity
import io.github.yuuichi_s.astertune.extensions.move

/**
 * @param title Queue title (and UID)
 * @param entries Songs of the queue
 * @param currentEntryId Entry ID of the current song, or null when [queuePos] points at no entry
 * @param outsideQueuePos Value last set to [queuePos], returned while it points at no entry
 * @param shuffleOrder Playback position of each entry of [entries] when shuffled
 */
data class MultiQueueObject(
    val id: Long,
    var title: String,
    /**
     * The order of songs are dynamic. This should not be accessed from outside QueueBoard.
     * Change it only through the functions of this class, which update [shuffleOrder] together.
     */
    val entries: MutableList<QueueEntry>,
    var shuffled: Boolean = false,
    private var currentEntryId: Long? = null,
    private var outsideQueuePos: Int = -1,
    var lastSongPos: Long = C.TIME_UNSET,
    var index: Int, // order of queue
    /**
     * Song id to start watch endpoint
     */
    var playlistId: String? = null,
    val shuffleOrder: MutableList<Int> = MutableList(entries.size) { it },
) {
    /**
     * Creates a queue with a new entry for each song of [queue].
     *
     * @param queuePos Index of the current song in [queue]
     */
    constructor(
        id: Long,
        title: String,
        queue: List<MediaMetadata>,
        shuffled: Boolean = false,
        queuePos: Int = -1,
        lastSongPos: Long = C.TIME_UNSET,
        index: Int,
        playlistId: String? = null,
        shuffleOrder: MutableList<Int> = MutableList(queue.size) { it },
    ) : this(
        id = id,
        title = title,
        entries = queue.mapTo(ArrayList()) { QueueEntry(it) },
        shuffled = shuffled,
        lastSongPos = lastSongPos,
        index = index,
        playlistId = playlistId,
        shuffleOrder = shuffleOrder,
    ) {
        this.queuePos = queuePos
    }

    /** Songs of [entries], in the same order. */
    val queue: List<MediaMetadata> = object : AbstractList<MediaMetadata>() {
        override val size get() = entries.size
        override fun get(index: Int) = entries[index].song
    }

    private var cachedQueuePos = -1

    /**
     * Index of the current song in [entries]. Setting it makes the entry at that index current,
     * which stays current when entries are added, removed, or moved.
     */
    var queuePos: Int
        get() {
            val entryId = currentEntryId ?: return outsideQueuePos
            if (entries.getOrNull(cachedQueuePos)?.entryId != entryId) {
                cachedQueuePos = entries.indexOfFirst { it.entryId == entryId }
            }
            return cachedQueuePos
        }
        set(value) {
            currentEntryId = entries.getOrNull(value)?.entryId
            outsideQueuePos = value
            cachedQueuePos = value
        }

    private fun playbackOrder(): List<Int> =
        if (shuffled) queue.indices.sortedBy { shuffleOrder[it] } else queue.indices.toList()

    /**
     * Retrieve the current queue in list form, with shuffle state taken in account
     *
     * @return A copy of the Metadata list
     */
    fun getCurrentQueueShuffled(): MutableList<MediaMetadata> {
        return if (shuffled) {
            playbackOrder().mapTo(ArrayList()) { queue[it] }
        } else {
            queue.toMutableList()
        }
    }

    /**
     * Retrieve the song at current position in the queue
     */
    fun getCurrentSong(): MediaMetadata? {
        validateQueuePos()
        return queue[queuePos]
    }

    /** Returns the current entry, or null if [queuePos] points at no entry. */
    fun getCurrentEntry(): QueueEntry? = entries.getOrNull(queuePos)

    /**
     * Retrieve a song given a song ID. Returns null if no song is found
     */
    fun findSong(mediaId: String): MediaMetadata? {
        val currentSong = getCurrentSong()
        if (currentSong?.id == mediaId) {
            return currentSong
        }

        return queue.fastFirstOrNull { it.id == mediaId }
    }

    /**
     * Returns the index of current queue position considering shuffle state
     */
    fun getQueuePosShuffled(): Int {
        validateQueuePos()
        return if (shuffled) {
            shuffleOrder[queuePos]
        } else {
            queuePos
        }
    }

    fun setCurrentQueuePos(index: Int) {
        if (getQueuePosShuffled() != index) {

            /**
             * queuePos will always track the index of the song in the unsorted queue, *even* if queue is shuffled.
             * To get the real queuePos of the song, look at the shuffle position that equals the index provided
             */
            val newQueuePos = if (shuffled) {
                indexOfShufflePos(index)
            } else {
                index
            }

            queuePos = newQueuePos
        }
    }

    fun validateQueuePos() {
        if (queuePos < 0 || queuePos >= queue.size) {
            // possible issues with migrating some queues, notably from 0.7.4 to newer versions.
            // Reset an invalid selection to index zero and restore unshuffled indices.
            shuffleOrder.indices.forEach { shuffleOrder[it] = it }
            shuffled = false
            queuePos = 0
        }
    }

    fun indexOfShufflePos(shufflePos: Int) = shuffleOrder.indexOf(shufflePos)

    fun renumberShuffleOrder() {
        playbackOrder().fastForEachIndexed { pos, i -> shuffleOrder[i] = pos }
    }

    fun shiftShuffleOrderFrom(from: Int, shift: Int) {
        val order = playbackOrder()
        order.subList(from, order.size).forEach { shuffleOrder[it] += shift }
    }

    fun setShuffleOrder(order: List<Int>) {
        order.fastForEachIndexed { pos, i -> shuffleOrder[i] = pos }
    }

    fun moveInShuffleOrder(from: Int, to: Int) {
        val order = playbackOrder().toMutableList()
        order.move(from, to)
        setShuffleOrder(order)
    }

    /** Inserts a new entry for each of [songs] at [index]. */
    fun addSongs(index: Int, songs: List<MediaMetadata>, shufflePositions: List<Int>) {
        entries.addAll(index, songs.map { QueueEntry(it) })
        shuffleOrder.addAll(index, shufflePositions)
    }

    fun removeSongAt(index: Int) {
        entries.removeAt(index)
        shuffleOrder.removeAt(index)
    }

    fun moveSong(from: Int, to: Int) {
        entries.move(from, to)
        shuffleOrder.move(from, to)
    }

    /**
     * Retrieve the total duration of all songs
     *
     * @return Duration in seconds
     */
    fun getDuration(): Int {
        return entries.fastSumBy {
            it.song.duration // seconds
        }
    }

    /**
     * Get the length of the queue
     */
    fun getSize() = entries.size

    fun replaceAll(mediaList: List<MediaMetadata>, shufflePositions: List<Int>) {
        entries.clear()
        shuffleOrder.clear()
        addSongs(0, mediaList, shufflePositions)
    }

    fun toQueueEntity() = QueueEntity(
        id = id,
        title = title,
        shuffled = shuffled,
        queuePos = queuePos,
        lastSongPos = lastSongPos,
        index = index,
        playlistId = playlistId
    )
}
