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
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.C
import io.github.yuuichi_s.astertune.constants.PersistentQueueKey
import io.github.yuuichi_s.astertune.constants.QUEUE_DEBUG
import io.github.yuuichi_s.astertune.db.entities.QueueEntity
import io.github.yuuichi_s.astertune.extensions.currentMetadata
import io.github.yuuichi_s.astertune.extensions.move
import io.github.yuuichi_s.astertune.extensions.toMediaItem
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.models.MultiQueueObject
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min


/**
 * Multiple queues manager. Methods will not automatically (re)load queues into the player unless
 * otherwise explicitly stated.
 */
class QueueBoard(
    private val player: MusicService,
    val masterQueues: SnapshotStateList<MultiQueueObject> = mutableStateListOf(),
    queues: MutableList<MultiQueueObject> = ArrayList(),
    private var maxQueues: Int
) {
    private val TAG = QueueBoard::class.simpleName.toString()

    private var masterIndex: Int // current queue index
    var detachedHead = false

    init {
        masterQueues.clear()
        if (maxQueues < 0) {
            maxQueues = 1
        }
        if (!queues.isEmpty()) {
            masterQueues.addAll(queues.subList((queues.size - maxQueues).coerceAtLeast(0), queues.size))
        }
        masterIndex = masterQueues.size - 1
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
        masterQueues.fastForEachIndexed { index, q -> q.index = index }
    }

    /**
     * Moves a queue to the end of [masterQueues] and selects it.
     *
     * @param item Queue to select
     */
    private fun bubbleUp(item: MultiQueueObject) = bubbleUp(masterQueues.indexOf(item))

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
        saveAllQueues(masterQueues)
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
     * Creating a queue at the queue limit removes the first queue from [masterQueues].
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

                mediaList.fastForEachIndexed { index, s ->
                    s?.shuffleIndex = index
                }

                match.replaceAll(mediaList.filterNotNull())
                match.queuePos = startIndex
                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
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
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
                }

                match.playlistId = continuationEndpoint

                saveQueue(match)
                return match
            } else if (delta) {
                if (QUEUE_DEBUG)
                    Log.d(TAG, "Adding to queue: delta additive")

                mediaList.fastForEachIndexed { index, s ->
                    s?.shuffleIndex = index
                }

                // add only the songs that are not already in the queue
                match.queue.addAll(mediaList.filter { s -> match.queue.none { s?.id == it.id } }.filterNotNull())

                // find the song in existing queue song, track the index to jump to
                val findSong = match.queue.firstOrNull { it.id == mediaList[startIndex]?.id }
                if (findSong != null) {
                    match.queuePos = match.queue.indexOf(findSong)
                }
                if (shuffled) {
                    shuffle(match, false, true)
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
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
                        anyExts.queuePos = anyExts.queue.indexOf(anyExts.queue.find { it.shuffleIndex == 0 })
                        anyExts.playlistId = continuationEndpoint
                    }
                } else {
                    addSongsToQueue(match, Int.MAX_VALUE, mediaList.filterNotNull(), saveToDb = false)
                    if (shuffled) {
                        shuffle(match, false, true)
                        match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
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
                    match.queuePos = match.queue.indexOf(match.queue.find { it.shuffleIndex == 0 })
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
            if (masterQueues.size >= maxQueues) {
                deleteQueue(masterQueues.first())
            }
            val q = ArrayList(mediaList.filterNotNull())
            q.fastForEachIndexed { index, s ->
                s?.shuffleIndex = index
            }

            val newQueue = MultiQueueObject(
                QueueEntity.generateQueueId(),
                title,
                q,
                false,
                startIndex,
                -1,
                masterQueues.size,
                continuationEndpoint
            )
            masterQueues.add(newQueue)
            if (shuffled) {
                shuffle(masterQueues.size - 1, false, true)
                newQueue.queuePos = newQueue.queue.indexOf(newQueue.queue.find { it.shuffleIndex == 0 })
            }

            saveQueueSongs(newQueue)
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
            val songsAfter = q.getCurrentQueueShuffled()
            songsAfter.subList(listPos, songsAfter.size).forEach {
                it.shuffleIndex += mediaList.size
            }
        }

        // add new items
        mediaList.fastForEachIndexed { index, s ->
            s.shuffleIndex = listPos + index
        }

        if (q.shuffled) {
            q.queue.addAll(mediaList)
        } else {
            q.queue.addAll(listPos, mediaList)
        }

        // adding before current playing song requires tracking new index
        if (q.getQueuePosShuffled() >= listPos) {
            if (q.shuffled) {
                // shuffle index current song + add size
                val newIndex = q.queue[q.queuePos].shuffleIndex + mediaList.size
                q.queuePos = q.queue.indexOf(q.queue.fastFirst { it.shuffleIndex == newIndex })
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
        if (q == null) {
            return false
        }
        return removeSong(q, index)
    }


    /**
     * Removes song from the queue
     *
     * @param item Queue
     * @param index Song index in playback order
     */
    fun removeSong(item: MultiQueueObject, index: Int): Boolean {
        var ret = false
        val currentMediaItemIndex = player.player.currentMediaItemIndex
        var newQueuePos = item.getQueuePosShuffled()

        if (item.shuffled) {
            Log.d(TAG, "Trying remove song at index: $index")
            val s = item.queue.find { it.shuffleIndex == index }
            if (s != null) {
                ret = item.queue.remove(s)
                Log.d(TAG, "Removing song: ${s.title}, $ret")
            }
        } else {
            item.queue.removeAt(index)
            ret = true
        }
        item.getCurrentQueueShuffled().fastForEachIndexed { index, s -> s.shuffleIndex = index }

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
     * Deletes a queue
     *
     * @param item Queue identifying the target by title
     * @return Number of remaining queues
     */
    fun deleteQueue(item: MultiQueueObject): Int {
        if (QUEUE_DEBUG)
            Log.d(TAG, "DELETING QUEUE ${item.title}")

        val match = masterQueues.firstOrNull { it.title == item.title }
        if (match != null) {
            masterQueues.remove(match)
            if (masterQueues.isNotEmpty()) {
                masterIndex -= 1
            } else {
                masterIndex = -1
            }

            CoroutineScope(Dispatchers.IO).launch {
                player.database.deleteQueue(match.id)
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

        val currentSong = item.queue[item.queuePos]

        // shuffle & push the current song to top if requested to
        shuffleInPlace(item.queue)
        if (preserveCurrent) {
            val s2 = item.queue.find { it.shuffleIndex == 0 }
            if (s2 != null && currentSong != s2) {
                currentSong.shuffleIndex = s2.shuffleIndex.also { s2.shuffleIndex = currentSong.shuffleIndex }
            }
            item.queuePos = item.queue.indexOf(currentSong)
        } else {
            item.queuePos = item.queue.indexOf(item.queue.fastFirstOrNull { it.shuffleIndex == 0 })
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
        saveAllQueues(masterQueues)
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
        val items = queue.getCurrentQueueShuffled()
        var newQueuePos = queue.getQueuePosShuffled()
        val currentMediaItemIndex = player.player.currentMediaItemIndex

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
            items.move(fromIndex, toIndex)
            items.fastForEachIndexed { index, s ->
                // items is a copy of queue.queue, assume all objects will exist *once* only
                queue.queue.find { it == s }?.shuffleIndex = index
            }
        } else {
            queue.queue.move(fromIndex, toIndex)
        }
        queue.getCurrentQueueShuffled().fastForEachIndexed { index, s -> s.shuffleIndex = index }

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

    /**
     * Returns an immutable snapshot of the queue list.
     */
    fun getAllQueues() = masterQueues.toImmutableList()


    /**
     * Get the index of the current queue
     */
    fun getMasterIndex() = masterIndex

    /**
     * Retrieve the current queue
     *
     * @return Current queue, or null if the current queue index is invalid
     */
    fun getCurrentQueue(): MultiQueueObject? {
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
            masterQueues.add(oldIndex, q.copy(title = newName))

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
            player.player.setMediaItems(ArrayList())
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
                    "ids: ${player.player.currentMetadata?.id}, ${mediaItems[queuePos].id}"
        )
        // Keep the playing item when it matches the target song.
        val seamlessSupported = (queuePos < mediaItems.size)
                && player.player.currentMetadata?.id == mediaItems[queuePos].id
        if (seamlessSupported) {
            Log.d(TAG, "Trying seamless queue switch. Is first song?: ${queuePos == 0}")
            val playerIndex = player.player.currentMediaItemIndex

            if (queuePos == 0) {
                val playerItemCount = player.player.mediaItemCount
                if (playerIndex < playerItemCount - 1) {
                    player.player.removeMediaItems(playerIndex + 1, playerItemCount)
                }
                if (playerIndex > 0) {
                    player.player.removeMediaItems(0, playerIndex)
                }
                // add all songs except the first one since it is already present and playing
                player.player.addMediaItems(mediaItems.drop(1).map { it.toMediaItem() })
            } else {
                // replace items up to current playing, then replace items after current
                player.player.replaceMediaItems(
                    0, playerIndex,
                    mediaItems.subList(0, queuePos).map { it.toMediaItem() })
                player.player.replaceMediaItems(
                    queuePos + 1, Int.MAX_VALUE,
                    mediaItems.subList(queuePos + 1, mediaItems.size).map { it.toMediaItem() })
            }
        } else {
            Log.d(TAG, "Seamless is not supported. Loading songs in directly")
            player.player.setMediaItems(mediaItems.map { it.toMediaItem() }, queuePos, lastSongPos)
        }

        bubbleUp(item)
        if (player.player.shuffleModeEnabled != item.shuffled) {
            player.player.shuffleModeEnabled = item.shuffled
        }
        return queuePos
    }

    /**
     * Update the current position index of the current queue
     *
     * @param index Song index in playback order
     */
    fun setCurrQueuePosIndex(index: Int) {
        getCurrentQueue()?.let {
            it.setCurrentQueuePos(index)
            saveQueue(it)
        }
    }


    /**
     * ========================
     * Database sync management
     * ========================
     */

    class PriorityJob(val priority: Int, val job: Job) : Comparable<PriorityJob> {
        override fun compareTo(other: PriorityJob): Int = this.priority - other.priority
    }

    var queueEntity = PriorityQueue<PriorityJob>()
    var queueSongMap = PriorityQueue<PriorityJob>()
    var jobActive = Mutex()
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Drains references to eagerly launched save jobs at five-second intervals,
     * checking song-mapping jobs before queue-entity jobs.
     */
    private suspend fun databaseDispatcher() {
        Log.d(TAG, "Starting database save task")
        if (jobActive.isLocked) {
            Log.d(TAG, "Database save task is already active, aborting")
            return
        }

        jobActive.withLock {
            while (queueEntity.isNotEmpty() || queueSongMap.isNotEmpty()) {
                runBlocking {
                    delay(5000L)
                }
                Log.d(TAG, "Running database save task")

                // A song save rewrites both the queue entity and its song mappings.
                if (!queueSongMap.isEmpty()) {
                    queueSongMap.last().job.start()
                    queueSongMap.clear()
                    continue
                }

                if (!queueEntity.isEmpty()) {
                    queueEntity.last().job.start()
                    queueEntity.clear()
                    continue
                }
            }
        }
        Log.d(TAG, "Exiting database save task")
    }

    fun shutdown() {
        queueSongMap.clear()
        queueEntity.clear()
    }

    private fun saveQueueSongs(mq: MultiQueueObject) {
        if (player.dataStore.get(PersistentQueueKey, true)) {
            queueSongMap.add(
                PriorityJob(
                    0,
                    coroutineScope.launch(start = CoroutineStart.DEFAULT) {
                        player.database.saveQueue(mq)
                    }
                )
            )
            CoroutineScope(Dispatchers.IO).launch {
                databaseDispatcher()
            }
        }
    }

    private fun saveQueue(mq: MultiQueueObject) {
        if (player.dataStore.get(PersistentQueueKey, true)) {
            queueEntity.add(
                PriorityJob(
                    0,
                    coroutineScope.launch(start = CoroutineStart.DEFAULT) {
                        player.database.updateQueue(mq)
                    }
                )
            )
            CoroutineScope(Dispatchers.IO).launch {
                databaseDispatcher()
            }
        }
    }

    private fun saveAllQueues(mq: MutableList<MultiQueueObject>) {
        if (player.dataStore.get(PersistentQueueKey, true)) {
            queueEntity.add(
                PriorityJob(
                    -1,
                    coroutineScope.launch(start = CoroutineStart.DEFAULT) {
                        player.database.updateAllQueues(mq)
                    }
                )
            )
            CoroutineScope(Dispatchers.IO).launch {
                databaseDispatcher()
            }
        }
    }

    companion object {

        fun shuffleInPlace(list: List<MediaMetadata>) {
            val rng = (0..(list.size - 1)).shuffled()

            list.forEachIndexed { index, s ->
                s.shuffleIndex = rng[index]
            }
        }
    }

}
