package io.github.yuuichi_s.astertune.models

import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastSumBy
import androidx.media3.common.C
import io.github.yuuichi_s.astertune.db.entities.QueueEntity
import io.github.yuuichi_s.astertune.extensions.move

/**
 * @param title Queue title (and UID)
 * @param queue List of media items
 * @param shuffleOrder Playback position of each song of [queue] when shuffled
 */
data class MultiQueueObject(
    val id: Long,
    var title: String,
    /**
     * The order of songs are dynamic. This should not be accessed from outside QueueBoard.
     * Change it only through the functions of this class, which update [shuffleOrder] together.
     */
    val queue: MutableList<MediaMetadata>,
    var shuffled: Boolean = false,
    var queuePos: Int = -1, // position of current song
    var lastSongPos: Long = C.TIME_UNSET,
    var index: Int, // order of queue
    /**
     * Song id to start watch endpoint
     */
    var playlistId: String? = null,
    val shuffleOrder: MutableList<Int> = MutableList(queue.size) { it },
) {

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
            queue
        }
    }

    /**
     * Retrieve the song at current position in the queue
     */
    fun getCurrentSong(): MediaMetadata? {
        validateQueuePos()
        return queue[queuePos]
    }

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

    fun addSongs(index: Int, songs: List<MediaMetadata>, shufflePositions: List<Int>) {
        queue.addAll(index, songs)
        shuffleOrder.addAll(index, shufflePositions)
    }

    fun removeSongAt(index: Int) {
        queue.removeAt(index)
        shuffleOrder.removeAt(index)
    }

    fun moveSong(from: Int, to: Int) {
        queue.move(from, to)
        shuffleOrder.move(from, to)
    }

    /**
     * Retrieve the total duration of all songs
     *
     * @return Duration in seconds
     */
    fun getDuration(): Int {
        return queue.fastSumBy {
            it.duration // seconds
        }
    }

    /**
     * Get the length of the queue
     */
    fun getSize() = queue.size

    fun replaceAll(mediaList: List<MediaMetadata>, shufflePositions: List<Int>) {
        queue.clear()
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
