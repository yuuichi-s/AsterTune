/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback

import android.app.Application
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.createEmptyTestDatabase
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.models.MultiQueueObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Verifies the song order, the selected song, and the player's playlist after
 * [QueueBoard] adds, moves, removes, and shuffles songs within queues.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class QueueBoardSongOperationsTest {
    private lateinit var database: MusicDatabase
    private lateinit var host: FakeQueueBoardHost
    private lateinit var board: QueueBoard

    @Before
    fun setUp() {
        database = createEmptyTestDatabase()
        host = FakeQueueBoardHost(database)
        board = QueueBoard(host, maxQueues = 5, random = Random(1))
    }

    @After
    fun tearDown() {
        runBlocking { board.flushDatabaseWrites() }
        database.close()
    }

    // addQueue()

    @Test
    fun addQueueCreatesQueueForNewTitle() {
        val q = board.addQueue("A", songs("a", "b", "a"), startIndex = 1)!!

        assertEquals(listOf("A"), board.getAllQueues().map { it.title })
        assertEquals(listOf("a", "b", "a"), q.ids())
        assertEquals("b", q.selectedId())
    }

    @Test
    fun addQueueRemovesOldestQueueAtLimit() {
        board = QueueBoard(host, maxQueues = 2, random = Random(1))
        board.addQueue("A", songs("a"))
        board.addQueue("B", songs("b"))

        board.addQueue("C", songs("c"))

        assertEquals(listOf("B", "C"), board.getAllQueues().map { it.title })
    }

    @Test
    fun addQueueReplacesContents() {
        val q = board.addQueue("A", songs("a", "b", "c"))!!

        val result = board.addQueue("A", songs("d", "e", "f"), replace = true, startIndex = 2)

        assertEquals(q.id, result?.id)
        assertEquals(listOf("A"), board.getAllQueues().map { it.title })
        assertEquals(listOf("d", "e", "f"), q.ids())
        assertEquals("f", q.selectedId())
    }

    @Test
    fun addQueueWithSameSongsOnlySelectsSong() {
        val q = board.addQueue("A", songs("a", "b", "a", "c"))!!

        val result = board.addQueue("A", songs("c", "a", "b", "a"), startIndex = 0)

        assertEquals(q.id, result?.id)
        assertEquals(listOf("a", "b", "a", "c"), q.ids())
        assertEquals("c", q.selectedId())
    }

    @Test
    fun addQueueWithDeltaAddsMissingSongs() {
        val q = board.addQueue("A", songs("a", "b", "c"))!!

        val result = board.addQueue("A", songs("b", "d", "e"), startIndex = 2)

        assertEquals(q.id, result?.id)
        assertEquals(listOf("a", "b", "c", "d", "e"), q.ids())
        assertEquals("e", q.selectedId())
    }

    @Test
    fun addQueueWithoutDeltaExtendsAndRenamesQueue() {
        val q = board.addQueue("A", songs("a", "b"))!!

        val result = board.addQueue("A", songs("b", "c"), forceInsert = true, delta = false)

        assertEquals(q.id, result?.id)
        assertEquals(listOf(EXTENSION_TITLE), board.getAllQueues().map { it.title })
        assertEquals(listOf("a", "b", "b", "c"), q.ids())
        assertEquals("a", q.selectedId())
        assertEquals(listOf("a", "b", "b", "c"), host.songIds)
    }

    @Test
    fun addQueueWithoutDeltaAddsToExistingExtensionQueue() {
        board.addQueue("A", songs("a", "b"))
        board.addQueue("A", songs("c"), forceInsert = true, delta = false)
        val newQueue = board.addQueue("A", songs("d"))!!

        val result = board.addQueue("A", songs("e"), forceInsert = true, delta = false)

        assertEquals(newQueue.id, result?.id)
        assertEquals(setOf("A", EXTENSION_TITLE), board.getAllQueues().map { it.title }.toSet())
        assertEquals(listOf("d"), queueTitled("A").ids())
        assertEquals(listOf("a", "b", "c", "e"), queueTitled(EXTENSION_TITLE).ids())
        assertEquals(listOf("a", "b", "c", "e"), host.songIds)
    }

    // addSongsToQueue()

    @Test
    fun addNextInsertsAfterSelectedSong() {
        val q = loadQueue("a", "b", "c", "d", startIndex = 1)

        addNext(q, "e", "a")

        assertEquals(listOf("a", "b", "e", "a", "c", "d"), q.ids())
        assertEquals("b", q.selectedId())
        assertEquals(q.ids(), host.songIds)
        assertEquals(1, host.currentMediaItemIndex)
    }

    @Test
    fun addEndAppendsSongs() {
        val q = loadQueue("a", "b", "c", startIndex = 1)

        addEnd(q, "d", "a")

        assertEquals(listOf("a", "b", "c", "d", "a"), q.ids())
        assertEquals("b", q.selectedId())
        assertEquals(q.ids(), host.songIds)
        assertEquals(1, host.currentMediaItemIndex)
    }

    @Test
    fun addNextWhileShuffledInsertsAfterSelectedSong() {
        val q = loadQueue("a", "b", "c", "d", "e", startIndex = 2)
        toggleShuffle()
        val before = q.ids()
        val pos = host.currentMediaItemIndex

        addNext(q, "f", "a")

        assertEquals(before.take(pos + 1) + listOf("f", "a") + before.drop(pos + 1), q.ids())
        assertEquals("c", q.selectedId())
        assertEquals(q.ids(), host.songIds)
        assertEquals(pos, host.currentMediaItemIndex)
    }

    @Test
    fun addEndWhileShuffledAppendsSongs() {
        val q = loadQueue("a", "b", "c", "d", "e", startIndex = 2)
        toggleShuffle()
        val before = q.ids()
        val pos = host.currentMediaItemIndex

        addEnd(q, "f", "a")

        assertEquals(before + listOf("f", "a"), q.ids())
        assertEquals("c", q.selectedId())
        assertEquals(q.ids(), host.songIds)
        assertEquals(pos, host.currentMediaItemIndex)
    }

    // removeCurrentQueueSong(), followed by the player as the queue screen does

    @Test
    fun removeSongBeforeSelectedKeepsSelection() {
        val q = loadQueue("a", "b", "c", "d", "b", startIndex = 2)

        swipeRemove(1)

        assertEquals(listOf("a", "c", "d", "b"), q.ids())
        assertEquals("c", q.selectedId())
    }

    @Test
    fun removeSongAfterSelectedKeepsSelection() {
        val q = loadQueue("a", "b", "c", "d", "b", startIndex = 2)

        swipeRemove(4)

        assertEquals(listOf("a", "b", "c", "d"), q.ids())
        assertEquals("c", q.selectedId())
    }

    @Test
    fun removeSelectedSongSelectsNextSong() {
        val q = loadQueue("a", "b", "c", "d", "b", startIndex = 2)

        swipeRemove(2)

        assertEquals(listOf("a", "b", "d", "b"), q.ids())
        assertEquals("d", q.selectedId())
    }

    // moveSong()

    @Test
    fun moveSelectedSongKeepsSelection() {
        val q = loadQueue("a", "b", "c", "d", "b", startIndex = 2)

        board.moveSong(2, 0)

        assertEquals(listOf("c", "a", "b", "d", "b"), q.ids())
        assertEquals("c", q.selectedId())
    }

    @Test
    fun moveSongPastSelectedToLaterKeepsSelection() {
        val q = loadQueue("a", "b", "c", "d", "b", startIndex = 2)

        board.moveSong(0, 3)

        assertEquals(listOf("b", "c", "d", "a", "b"), q.ids())
        assertEquals("c", q.selectedId())
    }

    @Test
    fun moveSongPastSelectedToEarlierKeepsSelection() {
        val q = loadQueue("a", "b", "c", "d", "b", startIndex = 2)

        board.moveSong(4, 0)

        assertEquals(listOf("b", "a", "b", "c", "d"), q.ids())
        assertEquals("c", q.selectedId())
    }

    @Test
    fun moveSongOntoSelectedFromEarlierKeepsSelection() {
        val q = loadQueue("a", "b", "c", "d", "b", startIndex = 2)

        board.moveSong(1, 2)

        assertEquals(listOf("a", "c", "b", "d", "b"), q.ids())
        assertEquals("c", q.selectedId())
    }

    @Test
    fun moveSongOntoSelectedFromLaterKeepsSelection() {
        val q = loadQueue("a", "b", "c", "d", "b", startIndex = 2)

        board.moveSong(4, 2)

        assertEquals(listOf("a", "b", "b", "c", "d"), q.ids())
        assertEquals("c", q.selectedId())
    }

    @Test
    fun moveSongAfterSelectedKeepsSelection() {
        val q = loadQueue("a", "b", "c", "d", "b", startIndex = 2)

        board.moveSong(4, 3)

        assertEquals(listOf("a", "b", "c", "b", "d"), q.ids())
        assertEquals("c", q.selectedId())
    }

    // shuffle() and unShuffle()

    @Test
    fun shuffleMovesSelectedSongFirst() {
        val q = loadQueue(*SHUFFLE_IDS, startIndex = 2)

        toggleShuffle()

        assertEquals("c", q.ids().first())
        assertEquals(SHUFFLE_IDS.sorted(), q.ids().sorted())
        assertEquals("c", q.selectedId())
        assertEquals(q.ids(), host.songIds)
        assertEquals(0, host.currentMediaItemIndex)
    }

    @Test
    fun unShuffleRestoresOrder() {
        val q = loadQueue(*SHUFFLE_IDS, startIndex = 2)
        toggleShuffle()
        assertNotEquals(SHUFFLE_IDS.toList(), q.ids())

        toggleShuffle()

        assertEquals(SHUFFLE_IDS.toList(), q.ids())
        assertEquals("c", q.selectedId())
        assertEquals(SHUFFLE_IDS.toList(), host.songIds)
        assertEquals(2, host.currentMediaItemIndex)
    }

    private fun song(id: String) = MediaMetadata(
        id = id,
        title = id,
        artists = emptyList(),
        duration = 0,
        genre = null,
    )

    private fun songs(vararg ids: String) = ids.map(::song)

    private fun MultiQueueObject.ids() = getCurrentQueueShuffled().map { it.id }

    private fun MultiQueueObject.selectedId() = getCurrentSong()?.id

    private fun queueTitled(title: String) = board.getAllQueues().single { it.title == title }

    /** Creates a queue and loads it into the player, as playing a list of songs does. */
    private fun loadQueue(vararg ids: String, startIndex: Int): MultiQueueObject {
        val q = board.addQueue("A", songs(*ids), startIndex = startIndex)!!
        board.setCurrQueue(q)
        return q
    }

    /** Adds songs as "Play next" does. */
    private fun addNext(q: MultiQueueObject, vararg ids: String) =
        board.addSongsToQueue(q, host.currentMediaItemIndex + 1, songs(*ids))

    /** Adds songs as "Add to queue" does. */
    private fun addEnd(q: MultiQueueObject, vararg ids: String) =
        board.addSongsToQueue(q, Int.MAX_VALUE, songs(*ids))

    /**
     * Removes a song as swiping it away on the queue screen does, then updates the selection
     * as the service does when the player reports a transition.
     */
    private fun swipeRemove(index: Int) {
        assertTrue(board.removeCurrentQueueSong(index))
        if (host.removeMediaItem(index)) {
            board.setCurrQueuePosIndex(host.currentMediaItemIndex)
        }
    }

    /** Toggles shuffle on the current queue as the shuffle button does. */
    private fun toggleShuffle() {
        board.setCurrQueuePosIndex(host.currentMediaItemIndex)
        if (board.getCurrentQueue()?.shuffled == false) {
            board.shuffleCurrent()
        } else {
            board.unShuffleCurrent()
        }
        board.setCurrQueue()
    }

    private companion object {
        const val EXTENSION_TITLE = "A +​"
        val SHUFFLE_IDS = arrayOf("a", "b", "c", "d", "e", "f", "g", "b")
    }
}
