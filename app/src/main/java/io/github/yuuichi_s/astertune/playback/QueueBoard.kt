/*
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.C
import io.github.yuuichi_s.astertune.constants.QUEUE_DEBUG
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.QueueEntity
import io.github.yuuichi_s.astertune.db.entities.QueueSongMap
import io.github.yuuichi_s.astertune.extensions.move
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.models.MultiQueueObject
import io.github.yuuichi_s.astertune.utils.reportException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


/**
 * Multiple queues manager. Methods will not automatically (re)load queues into the player unless
 * otherwise explicitly stated.
 *
 * Opens at most [maxQueues] of the given queues, keeping the current queue and the newest
 * others. The current queue is the one with `currentQueueId`, or the last queue if none matches.
 */
class QueueBoard(
    private val host: QueueBoardHost,
    override val masterQueues: SnapshotStateList<MultiQueueObject> = mutableStateListOf(),
    queues: MutableList<MultiQueueObject> = ArrayList(),
    private var maxQueues: Int,
    minFirstQueueIndex: Int = 0,
    storedQueuesLoaded: Boolean = false,
    private val random: Random = Random,
    currentQueueId: Long? = null,
) : QueueBoardReader {
    private val TAG = QueueBoard::class.simpleName.toString()

    // Whether saves retain other non-empty stored queues.
    var storedQueuesLoaded = storedQueuesLoaded
        private set

    private var masterIndex: Int // current queue index

    // Base index for renumbering and saving queues.
    val firstQueueIndex: Int

    init {
        masterQueues.clear()
        if (maxQueues < 0) {
            maxQueues = 1
        }
        val firstOpen = (queues.size - maxQueues).coerceAtLeast(0)
        val currentIndex = queues.indexOfFirst { it.id == currentQueueId }
        // The current queue takes the place of the oldest open queue.
        val open = if (maxQueues > 0 && currentIndex in 0 until firstOpen) {
            listOf(queues[currentIndex]) + queues.subList(firstOpen + 1, queues.size)
        } else {
            queues.subList(firstOpen, queues.size)
        }

        val closed = queues.filter { q -> open.none { it === q } }
        firstQueueIndex = maxOf(minFirstQueueIndex, closed.maxOfOrNull { it.index + 1 } ?: 0)
        masterQueues.addAll(open)
        masterIndex = masterQueues.indexOfFirst { it.id == currentQueueId }
            .takeIf { it >= 0 } ?: (masterQueues.size - 1)
    }

    /**
     * ========================
     * Data structure management
     * ========================
     */


    /**
     * Regenerate indexes of queues to reflect their position
     */
    private fun regenerateIndexes() {
        masterQueues.fastForEachIndexed { index, q -> q.index = firstQueueIndex + index }
    }

    /**
     * Moves a queue to the end of [masterQueues] and selects it.
     *
     * @param item Queue to select
     */
    private fun bubbleUp(item: MultiQueueObject) = bubbleUp(masterQueues.indexOf(item))

    /**
     * Moves a queue to the end of [masterQueues] and makes it current without loading it into
     * the player.
     *
     * @param item Queue to select
     */
    fun selectQueue(item: MultiQueueObject) = bubbleUp(item)

    /**
     * Moves a queue to the end of [masterQueues] and selects it.
     *
     * @param index Index of the queue to select
     */
    private fun bubbleUp(index: Int) {
        if (index < 0 || index >= masterQueues.size) {
            Log.w(TAG, "Bubble up index out of bounds")
            return
        }

        val item = masterQueues[index]
        masterQueues.remove(item)
        masterQueues.add(item)
        masterIndex = masterQueues.size - 1

        regenerateIndexes()
        saveAllQueues()
        saveCurrentQueueId()
    }

    /**
     * Adds songs to a queue identified by [title].
     *
     * For a non-empty [mediaList], applies the first matching case:
     * 1. No matching queue: creates a queue with the supplied songs.
     * 2. [replace] is true: replaces the matching queue's contents.
     * 3. [forceInsert] is false, every input song ID exists, and the sizes match:
     *    keeps the songs and updates the selected song.
     * 4. [delta] is true: adds songs whose IDs are absent and updates the selected song.
     * 5. Otherwise: appends to an existing extension queue, or extends the matching queue
     *    and adds the extension suffix to its title.
     *
     * Appending loads the destination queue into the player and preserves the selected song
     * unless [shuffled] is true.
     * Creating a queue at the queue limit removes the oldest queue other than the current one.
     * If the current queue is the only queue, it is removed and the new queue becomes current.
     *
     * @param title Title used to find an existing queue
     * @param mediaList Songs to add; null entries are omitted from inserted content
     * @param shuffled Whether to reshuffle the affected queue and select its first shuffled song
     * @param forceInsert Whether to bypass case 3; [delta] still controls whether existing
     *     song IDs are added again
     * @param replace Whether to replace the matching queue's contents, overriding [forceInsert]
     *     and [delta]
     * @param delta Whether to add only missing song IDs when neither replacement nor case 3 applies
     * @param continuationEndpoint Video ID used as the radio seed, or null for no continuation
     * @param startIndex Initial position when creating or replacing a queue. In cases 3 and 4,
     *     selects the song identified by [mediaList] at this index, if found. Ignored when
     *     appending; [shuffled] overrides the selection.
     * @return The new queue or the queue matching [title], or null for an empty [mediaList].
     *     Appending to a separate extension queue still returns the original matching queue.
     */
    fun addQueue(
        title: String,
        mediaList: List<MediaMetadata?>,
        shuffled: Boolean = false,
        forceInsert: Boolean = false,
        replace: Boolean = false,
        delta: Boolean = true,
        continuationEndpoint: String? = null,
        startIndex: Int = 0
    ): MultiQueueObject? {
        if (QUEUE_DEBUG)
            Log.d(
                TAG,
                "Adding to queue \"$title\". medialist size = ${mediaList.size}. forceInsert/replace/delta/startIndex = $forceInsert/$replace/$delta/$startIndex"
            )

        if (mediaList.isEmpty()) {
            return null
        }

        val match = masterQueues.firstOrNull { it.title == title }
        if (match != null) {
            // Extension queue titles end with a plus and a zero-width space (U+200B).
            val anyExts = masterQueues.firstOrNull { it.title == match.title + " +\u200B" }
            if (replace) {
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: Replacing all queue items")

                val songs = mediaList.withIndex().filter { it.value != null }
                match.replaceAll(songs.map { it.value!! }, songs.map { it.index })
                match.queuePos = startIndex
                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.indexOfShufflePos(0)
                }

                match.playlistId = continuationEndpoint

                saveQueueSongs(match)
                return match
            }

            val containsAll = mediaList.all { s -> match.queue.any { s?.id == it.id } }
            if (containsAll && match.getSize() == mediaList.size && !forceInsert) {
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: jump only")
                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong)
                }
                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.indexOfShufflePos(0)
                }

                match.playlistId = continuationEndpoint

                saveQueue(match)
                return match
            } else if (delta) {
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: delta additive")

                // add only the songs that are not already in the queue
                val added = mediaList.withIndex().filter { (_, s) -> s != null && match.queue.none { s.id == it.id } }
                match.addSongs(match.getSize(), added.map { it.value!! }, added.map { it.index })

                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong)
                }
                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.indexOfShufflePos(0)
                }

                match.playlistId = continuationEndpoint

                saveQueueSongs(match)
                return match
            } else if (match.title.endsWith("+\u200B") || anyExts != null) {
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: extension queue additive")
                // add items to existing queue unconditionally
                if (anyExts != null) {
                    addSongsToQueue(anyExts, Int.MAX_VALUE, mediaList.filterNotNull(), saveToDb = false)
                    if (shuffled) {
                        shuffle(anyExts, false, true)
                        anyExts.queuePos = anyExts.indexOfShufflePos(0)
                        anyExts.playlistId = continuationEndpoint
                    }
                } else {
                    addSongsToQueue(match, Int.MAX_VALUE, mediaList.filterNotNull(), saveToDb = false)
                    if (shuffled) {
                        shuffle(match, false, true)
                        match.queuePos = match.indexOfShufflePos(0)
                        match.playlistId = continuationEndpoint
                    }
                }

                match.playlistId = continuationEndpoint

                // rewrite queue
                saveQueueSongs(anyExts ?: match)

                return match
            } else { // Extend and rename the matching queue.
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: extension queue rename + extension queue additive")
                // add items to existing queue unconditionally
                addSongsToQueue(match, Int.MAX_VALUE, mediaList.filterNotNull(), saveToDb = false)
                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.indexOfShufflePos(0)
                }

                match.title = "${match.title} +\u200B"
                match.playlistId = continuationEndpoint

                // rewrite queue
                saveQueueSongs(match)

                return match
            }
        } else {
            // add entirely new queue
            // Precondition(s): radio queues never include local songs
            var currentRemoved = false
            if (masterQueues.size >= maxQueues) {
                val currentId = masterQueues.getOrNull(masterIndex)?.id
                val oldest = masterQueues.firstOrNull { it.id != currentId } ?: masterQueues.first()
                currentRemoved = oldest.id == currentId
                deleteQueue(oldest)
            }
            val q = ArrayList(mediaList.filterNotNull())

            val newQueue = MultiQueueObject(
                QueueEntity.generateQueueId(),
                title,
                q,
                false,
                startIndex,
                -1,
                maxOf(firstQueueIndex, (masterQueues.maxOfOrNull { it.index } ?: -1) + 1),
                continuationEndpoint
            )
            masterQueues.add(newQueue)
            if (shuffled) {
                shuffle(masterQueues.size - 1, false, true)
                newQueue.queuePos = newQueue.indexOfShufflePos(0)
            }

            saveQueueSongs(newQueue)
            if (currentRemoved && masterIndex !in masterQueues.indices) {
                masterIndex = masterQueues.indexOf(newQueue)
                saveCurrentQueueId()
            }
            return newQueue
        }
    }


    /**
     * Add songs to end of CURRENT QUEUE & update it in the player
     */
    fun enqueueEnd(mediaList: List<MediaMetadata>) {
        getCurrentQueue()?.let {
            addSongsToQueue(it, Int.MAX_VALUE, mediaList)
        }
    }

    /**
     * Add songs to a queue and load it into the player.
     *
     * @param q Queue to update
     * @param pos Insertion index in playback order, clamped to the queue bounds
     * @param mediaList Songs to insert
     * @param saveToDb Whether to save song mappings; queue metadata can still be saved when false
     */
    fun addSongsToQueue(
        q: MultiQueueObject,
        pos: Int,
        mediaList: List<MediaMetadata>,
        saveToDb: Boolean = true,
    ) {
        val listPos = if (pos < 0) {
            0
        } else if (pos > q.getSize()) {
            q.getSize()
        } else {
            pos
        }

        Log.d(TAG, "Inserting at position: $listPos")

        // assign new indexes to items affected by inserted items
        if (q.shuffled) {
            q.shiftShuffleOrderFrom(listPos, mediaList.size)
        }

        // add new items
        val shufflePositions = List(mediaList.size) { listPos + it }
        if (q.shuffled) {
            q.addSongs(q.getSize(), mediaList, shufflePositions)
        } else {
            q.addSongs(listPos, mediaList, shufflePositions)
        }

        // adding before current playing song requires tracking new index
        if (q.getQueuePosShuffled() >= listPos) {
            if (q.shuffled) {
                val newIndex = q.shuffleOrder[q.queuePos] + mediaList.size
                q.queuePos = q.queue.indices.first { q.shuffleOrder[it] == newIndex }
            } else {
                q.queuePos += mediaList.size
            }
        }

        setCurrQueue(q, false)

        if (saveToDb) {
            saveQueueSongs(q)
        }

    }

    /**
     * Removes song from the current queue
     *
     * @param index Song index in playback order
     */
    fun removeCurrentQueueSong(index: Int): Boolean {
        val q = getCurrentQueue()
        return q != null && removeSong(q, index)
    }


    /**
     * Removes song from the queue. Removing the last song deletes the queue as [deleteQueue] does.
     *
     * @param item Queue
     * @param index Song index in playback order
     */
    fun removeSong(item: MultiQueueObject, index: Int): Boolean {
        var ret = false
        val currentMediaItemIndex = host.currentMediaItemIndex
        var newQueuePos = item.getQueuePosShuffled()

        if (item.shuffled) {
            Log.d(TAG, "Trying remove song at index: $index")
            val pos = item.indexOfShufflePos(index)
            if (pos >= 0) {
                val s = item.queue[pos]
                item.removeSongAt(pos)
                ret = true
                Log.d(TAG, "Removing song: ${s.title}, $ret")
            }
        } else {
            item.removeSongAt(index)
            ret = true
        }
        if (item.queue.isEmpty()) {
            deleteQueue(item)
            return ret
        }
        item.renumberShuffleOrder()

        // Adjust the selection after removal.
        if (index < currentMediaItemIndex) {
            newQueuePos--
        } else if (index == currentMediaItemIndex) {
            newQueuePos++
        } else {
            // no need to adjust
        }

        if (newQueuePos >= item.getSize()) {
            newQueuePos = item.getSize() - 1
        } else if (newQueuePos < 0) {
            newQueuePos = 0
        }
        item.queuePos = newQueuePos

        saveQueueSongs(item)
        return ret
    }

    /**
     * Deletes a queue. If it is the current queue, the queue above it becomes current, or no
     * queue if there is none above. Leaves the player unchanged.
     *
     * @param item Queue identifying the target by ID
     * @return Number of remaining queues
     */
    fun deleteQueue(item: MultiQueueObject): Int {
        if (QUEUE_DEBUG)
            Log.d(TAG, "DELETING QUEUE ${item.title}")

        val matchIndex = masterQueues.indexOfFirst { it.id == item.id }
        val match = masterQueues.getOrNull(matchIndex)
        if (match != null) {
            val wasCurrent = matchIndex == masterIndex
            masterQueues.removeAt(matchIndex)
            if (masterQueues.isNotEmpty()) {
                if (matchIndex <= masterIndex) masterIndex -= 1
            } else {
                masterIndex = -1
            }

            if (wasCurrent && persistQueues()) {
                val currentId = masterQueues.getOrNull(masterIndex)?.id
                writeToDatabase { deleteQueueAndSaveCurrent(match.id, currentId) }
            } else {
                writeToDatabase { deleteQueue(match.id) }
            }
        } else {
            Log.w(TAG, "Cannot find queue to delete: ${item.title}")
        }

        return masterQueues.size
    }


    /**
     * Un-shuffles current queue
     *
     * @return Selected song index in the underlying queue
     */
    fun unShuffleCurrent() = unShuffle(masterIndex)

    /**
     * Un-shuffles a queue
     *
     * @return Selected song index in the underlying queue
     */
    fun unShuffle(index: Int): Int {
        val item = masterQueues[index]
        if (item.shuffled) {
            if (QUEUE_DEBUG)
                Log.d(TAG, "Un-shuffling queue ${item.title}")

            item.shuffled = false
        }
        saveQueueSongs(item)
        bubbleUp(item)
        return item.queuePos
    }

    /**
     * Shuffles current queue
     */
    fun shuffleCurrent(preserveCurrent: Boolean = true, bypassSaveToDb: Boolean = false) =
        shuffle(masterIndex, preserveCurrent, bypassSaveToDb)


    /**
     * Shuffles a queue and enables shuffle mode.
     *
     * @param q Queue to shuffle
     * @param preserveCurrent Whether to keep the selected song first; false selects the first
     *     shuffled song and resets its saved playback position
     * @param bypassSaveToDb Whether to skip saving song mappings; queue metadata can still be saved
     * @return Song index in the underlying queue, or zero if [q] is not in [masterQueues]
     */
    fun shuffle(
        q: MultiQueueObject,
        preserveCurrent: Boolean = true,
        bypassSaveToDb: Boolean = false
    ) = shuffle(masterQueues.indexOf(q), preserveCurrent, bypassSaveToDb)

    /**
     * Shuffles a queue and enables shuffle mode.
     *
     * @param index Queue index
     * @param preserveCurrent Whether to keep the selected song first; false selects the first
     *     shuffled song and resets its saved playback position
     * @param bypassSaveToDb Whether to skip saving song mappings; queue metadata can still be saved
     * @return Selected song index in the underlying queue, or zero if [index] is negative
     */
    fun shuffle(
        index: Int,
        preserveCurrent: Boolean = true,
        bypassSaveToDb: Boolean = false
    ): Int {
        if (index <= -1) {
            return 0
        }

        val item = masterQueues[index]
        if (QUEUE_DEBUG)
            Log.d(TAG, "Shuffling queue ${item.title}")

        val currentPos = item.queuePos
        if (currentPos !in item.queue.indices) {
            throw IndexOutOfBoundsException("Index $currentPos out of bounds for length ${item.queue.size}")
        }

        // shuffle & push the current song to top if requested to
        shuffleInPlace(item, random)
        if (preserveCurrent) {
            val firstPos = item.indexOfShufflePos(0)
            if (firstPos >= 0 && firstPos != currentPos) {
                item.shuffleOrder[firstPos] = item.shuffleOrder[currentPos]
                item.shuffleOrder[currentPos] = 0
            }
            item.queuePos = currentPos
        } else {
            item.queuePos = item.indexOfShufflePos(0)
            item.lastSongPos = C.TIME_UNSET
        }

        item.shuffled = true

        if (!bypassSaveToDb) {
            saveQueueSongs(item)
        }
        bubbleUp(item)
        return item.queuePos
    }

    /**
     * Move a queue in masterQueues
     *
     * @param fromIndex Index of the queue to move
     * @param toIndex Destination queue index
     */
    fun move(fromIndex: Int, toIndex: Int) {
        // update current position only if the move will affect it
        if (masterIndex >= min(fromIndex, toIndex) && masterIndex <= max(fromIndex, toIndex)) {
            if (fromIndex == masterIndex) {
                masterIndex = toIndex
            } else if (masterIndex == toIndex) {
                if (masterIndex < fromIndex) {
                    masterIndex++
                } else {
                    masterIndex--
                }
            } else if (toIndex > masterIndex) {
                masterIndex--
            } else {
                masterIndex++
            }
        }

        masterQueues.move(fromIndex, toIndex)
        regenerateIndexes()
        saveAllQueues()
    }


    /**
     * Move a song in the current queue
     *
     * @param fromIndex Song index in playback order
     * @param toIndex Destination index in playback order
     * @return Updated current song index, or null if there is no current queue
     */
    fun moveSong(fromIndex: Int, toIndex: Int) =
        getCurrentQueue()?.let { moveSong(it, fromIndex, toIndex) }

    /**
     * Move a song, given a queue.
     *
     * @param queue Queue to operate on
     * @param fromIndex Song index in playback order
     * @param toIndex Destination index in playback order
     * @return Updated current song index
     */
    private fun moveSong(
        queue: MultiQueueObject,
        fromIndex: Int,
        toIndex: Int,
    ): Int {
        var newQueuePos = queue.getQueuePosShuffled()
        val currentMediaItemIndex = host.currentMediaItemIndex

        // update current position only if the move will affect it
        if (currentMediaItemIndex >= min(fromIndex, toIndex) && currentMediaItemIndex <= max(fromIndex, toIndex)) {
            if (fromIndex == currentMediaItemIndex) {
                newQueuePos = toIndex
            } else if (currentMediaItemIndex == toIndex) {
                if (currentMediaItemIndex < fromIndex) {
                    newQueuePos++
                } else {
                    newQueuePos--
                }
            } else if (toIndex > currentMediaItemIndex) {
                newQueuePos--
            } else {
                newQueuePos++
            }
        }
        queue.queuePos = newQueuePos

        if (queue.shuffled) {
            queue.moveInShuffleOrder(fromIndex, toIndex)
        } else {
            queue.moveSong(fromIndex, toIndex)
        }
        queue.renumberShuffleOrder()

        saveQueueSongs(queue)

        if (QUEUE_DEBUG)
            Log.d(TAG, "Moved item from $currentMediaItemIndex to ${queue.queuePos}")
        return queue.queuePos
    }


    /**
     * =================
     * Player management
     * =================
     */

    override fun getAllQueues(): ImmutableList<MultiQueueObject> = masterQueues.toImmutableList()


    /**
     * Get the index of the current queue
     */
    fun getMasterIndex() = masterIndex

    /**
     * Selects an open queue matching all player song IDs in playback order.
     * Prefers the current queue. A changed selection moves the queue to the end.
     * Saves the order when persistence is enabled. Leaves the player's playlist unchanged.
     * Main thread only.
     *
     * @return Whether a matching queue was found
     */
    fun selectQueueInPlayer(): Boolean {
        val playerIds = List(host.mediaItemCount) { host.getMediaItemIdAt(it) }
        if (playerIds.isEmpty()) return false
        fun inPlayer(q: MultiQueueObject) = q.getCurrentQueueShuffled().map { it.id } == playerIds
        if (getCurrentQueue()?.let { inPlayer(it) } == true) return true
        val index = masterQueues.indexOfLast { inPlayer(it) }
        if (index < 0) return false
        bubbleUp(index)
        return true
    }

    /** Returns the current queue, or null if its index is invalid. */
    override fun getCurrentQueue(): MultiQueueObject? {
        try {
            return masterQueues[masterIndex]
        } catch (e: IndexOutOfBoundsException) {
            masterIndex = masterQueues.size - 1 // reset var if invalid
            return null
        }
    }

    fun renameQueue(queue: MultiQueueObject, newName: String) {
        val found = masterQueues.any { it == queue }
        if (found) {
            val oldIndex = masterQueues.indexOf(queue)
            val q = masterQueues.removeAt(oldIndex)
            val renamed = q.copy(title = newName)
            masterQueues.add(oldIndex, renamed)
            saveQueue(renamed)

            if (QUEUE_DEBUG)
                Log.d(TAG, "Renamed queue from \"${queue.title}\" to \"$newName\"")
        }
    }

    /**
     * Loads a queue into the player. Must be called on the main thread.
     *
     * @param index Queue index
     * @param shouldResume Whether to use the saved position or the default start when reloading;
     *     an already-playing target keeps its position regardless of this flag
     * @return Selected queue, or null if a queue or song index is out of bounds
     */
    fun setCurrQueue(index: Int, shouldResume: Boolean = true): MultiQueueObject? {
        return try {
            val q = masterQueues[index]
            setCurrQueue(q, shouldResume)
            return q
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    /**
     * Loads the current queue into the player. Must be called on the main thread.
     *
     * A missing or empty queue clears the player.
     *
     * @param shouldResume Whether to use the saved position or the default start when reloading;
     *     an already-playing target keeps its position regardless of this flag
     * @return Current queue, or null if there is none
     */
    fun setCurrQueue(shouldResume: Boolean = true): MultiQueueObject? {
        val q = getCurrentQueue()
        setCurrQueue(q, shouldResume)
        return q
    }

    /**
     * Loads a queue into the player. Must be called on the main thread.
     *
     * @param item Queue to load; a null or empty queue clears the player
     * @param shouldResume Whether to use the saved position or the default start when reloading;
     *     an already-playing target keeps its position regardless of this flag
     * @return Selected song index in playback order, or null for a null or empty queue
     */
    fun setCurrQueue(item: MultiQueueObject?, shouldResume: Boolean = true): Int? {
        Log.d(
            TAG,
            "Loading queue ${item?.title ?: "null"} into player. Shuffle state = ${item?.shuffled}"
        )

        if (item == null || item.queue.isEmpty()) {
            host.setMediaItems(emptyList())
            return null
        }

        val queuePos = item.getQueuePosShuffled()
        val lastSongPos = if (shouldResume) item.lastSongPos else C.TIME_UNSET
        val realQueuePos = item.queuePos
        masterIndex = masterQueues.indexOf(item)

        val mediaItems: MutableList<MediaMetadata> = item.getCurrentQueueShuffled()

        Log.d(
            TAG, "Setting current queue. in bounds: ${queuePos >= 0 && queuePos < mediaItems.size}, " +
                    "queuePos: $queuePos, real queuePos: ${realQueuePos}, lastSongPos: $lastSongPos" +
                    "ids: ${host.currentSongId}, ${mediaItems[queuePos].id}"
        )
        // Keep the playing item when it matches the target song.
        val seamlessSupported = (queuePos < mediaItems.size)
                && host.currentSongId == mediaItems[queuePos].id
        if (seamlessSupported) {
            Log.d(TAG, "Trying seamless queue switch. Is first song?: ${queuePos == 0}")
            val playerIndex = host.currentMediaItemIndex

            if (queuePos == 0) {
                val playerItemCount = host.mediaItemCount
                if (playerIndex < playerItemCount - 1) {
                    host.removeMediaItems(playerIndex + 1, playerItemCount)
                }
                if (playerIndex > 0) {
                    host.removeMediaItems(0, playerIndex)
                }
                // add all songs except the first one since it is already present and playing
                host.addMediaItems(mediaItems.drop(1))
            } else {
                // replace items up to current playing, then replace items after current
                host.replaceMediaItems(
                    0, playerIndex,
                    mediaItems.subList(0, queuePos))
                host.replaceMediaItems(
                    queuePos + 1, Int.MAX_VALUE,
                    mediaItems.subList(queuePos + 1, mediaItems.size))
            }
        } else {
            Log.d(TAG, "Seamless is not supported. Loading songs in directly")
            host.setMediaItems(mediaItems, queuePos, lastSongPos)
        }

        bubbleUp(item)
        if (host.shuffleModeEnabled != item.shuffled) {
            host.shuffleModeEnabled = item.shuffled
        }
        host.onQueueLoaded()
        return queuePos
    }

    /**
     * Update the current position index of the current queue. Selecting a different song clears
     * the saved playback position.
     *
     * @param index Song index in playback order
     */
    fun setCurrQueuePosIndex(index: Int) {
        getCurrentQueue()?.let {
            val previousSongId = it.queue.getOrNull(it.queuePos)?.id
            it.setCurrentQueuePos(index)
            if (it.queue.getOrNull(it.queuePos)?.id != previousSongId) {
                it.lastSongPos = C.TIME_UNSET
            }
            saveQueue(it)
        }
    }


    /**
     * ========================
     * Database sync management
     * ========================
     */

    private fun persistQueues() = host.persistQueues()

    /**
     * Enqueues a database write to run after previously requested writes.
     */
    private fun writeToDatabase(block: MusicDatabase.() -> Unit) {
        writeCount++
        val database = host.database
        databaseScope.launch { database.block() }
    }

    /**
     * Waits until every write requested so far has finished.
     */
    suspend fun flushDatabaseWrites() = withContext(databaseWriter) {}

    private fun saveQueueSongs(mq: MultiQueueObject) {
        if (!persistQueues()) return
        // Copy the queue now: it keeps changing on the main thread while the write is pending.
        val queue = mq.toQueueEntity()
        val songs = mq.queue.toList()
        val shuffleOrder = mq.shuffleOrder.toList()
        val songMaps = songs.mapIndexed { index, song ->
            QueueSongMap(
                queueId = mq.id,
                songId = song.id,
                index = index.toLong(),
                shuffledIndex = shuffleOrder[index].toLong()
            )
        }
        writeToDatabase { saveQueue(queue, songs, songMaps) }
    }

    /**
     * Saves the songs and metadata of the open queues whose IDs are in [ids] when persistence
     * is enabled.
     */
    fun saveQueueSongs(ids: Set<Long>) {
        masterQueues.filter { it.id in ids }.forEach { saveQueueSongs(it) }
    }

    private fun saveQueue(mq: MultiQueueObject) {
        if (!persistQueues()) return
        val queue = mq.toQueueEntity()
        writeToDatabase { update(queue) }
    }

    /**
     * Enqueues a metadata update for the current queue when persistence is enabled.
     */
    fun saveCurrentQueue() {
        getCurrentQueue()?.let { saveQueue(it) }
    }

    /** Saves which queue is current when persistence is enabled. */
    fun saveCurrentQueueId() {
        if (!persistQueues()) return
        val id = masterQueues.getOrNull(masterIndex)?.id
        writeToDatabase { saveCurrentQueue(id) }
    }

    /** Saves that there is no current queue when persistence is enabled. */
    fun saveNoCurrentQueue() {
        if (!persistQueues()) return
        writeToDatabase { saveCurrentQueue(null) }
    }

    /**
     * Queues metadata updates for all open queues when persistence is enabled.
     * Deletes other stored queues, except non-empty queues when [storedQueuesLoaded] is true.
     */
    fun saveAllQueues() {
        if (!persistQueues()) return
        val queues = masterQueues.mapIndexed { index, q -> q.toQueueEntity().copy(index = firstQueueIndex + index) }
        val keepOtherQueues = storedQueuesLoaded
        writeToDatabase { updateAllQueues(queues, keepOtherQueues) }
        storedQueuesLoaded = true
    }

    companion object {
        /**
         * Number of database writes requested so far. Main thread only.
         */
        var writeCount = 0L
            private set

        // Queue writes run sequentially across QueueBoard instances.
        private val databaseWriter = Dispatchers.IO.limitedParallelism(1)
        private val databaseScope = CoroutineScope(
            SupervisorJob() + databaseWriter + CoroutineExceptionHandler { _, e -> reportException(e) }
        )

        private fun shuffleInPlace(q: MultiQueueObject, random: Random) {
            val rng = (0..(q.getSize() - 1)).shuffled(random)

            rng.forEachIndexed { index, pos ->
                q.shuffleOrder[index] = pos
            }
        }
    }

}
