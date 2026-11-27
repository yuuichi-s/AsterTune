/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback

import android.app.Application
import androidx.media3.common.C
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.createEmptyTestDatabase
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.models.MultiQueueObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Verifies the player's playlist, the selected queue, and the saved queues after [QueueBoard]
 * loads queues into the player, selects, deletes, and moves queues, and saves them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class QueueBoardQueueSwitchTest {
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

    // setCurrQueue()

    @Test
    fun switchToQueueStartingWithPlayingSongKeepsPlayingItem() {
        val a = board.addQueue("A", songs("a", "b", "c"), startIndex = 1)!!
        val b = board.addQueue("B", songs("b", "d", "e"), startIndex = 0)!!
        board.setCurrQueue(a)
        host.calls.clear()

        board.setCurrQueue(b)

        assertNoPlaylistReset()
        assertEquals(listOf("b", "d", "e"), host.songIds)
        assertEquals(0, host.currentMediaItemIndex)
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    @Test
    fun switchToQueueWithPlayingSongInMiddleKeepsPlayingItem() {
        val a = board.addQueue("A", songs("a", "b", "c"), startIndex = 1)!!
        val b = board.addQueue("B", songs("d", "e", "b", "f"), startIndex = 2)!!
        board.setCurrQueue(a)
        host.calls.clear()

        board.setCurrQueue(b)

        assertNoPlaylistReset()
        assertEquals(listOf("d", "e", "b", "f"), host.songIds)
        assertEquals(2, host.currentMediaItemIndex)
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    @Test
    fun switchToQueueWithOtherSongResumesSavedPosition() {
        board = storedBoard(storedQueue(1, "B", "x", "y"), storedQueue(2, "A", "a", "b", "c", queuePos = 1))
        board.setCurrQueue(queueTitled("B"))
        host.calls.clear()

        board.setCurrQueue(queueTitled("A"), shouldResume = true)

        assertEquals(
            listOf(FakeQueueBoardHost.Call("setMediaItems", listOf(listOf("a", "b", "c"), 1, SAVED_POSITION_MS))),
            host.calls.filter { it.name == "setMediaItems" }
        )
        assertEquals("A", board.getCurrentQueue()?.title)
    }

    @Test
    fun switchToQueueWithOtherSongWithoutResumeStartsFromBeginningOfSong() {
        board = storedBoard(storedQueue(1, "B", "x", "y"), storedQueue(2, "A", "a", "b", "c", queuePos = 1))
        board.setCurrQueue(queueTitled("B"))
        host.calls.clear()

        board.setCurrQueue(queueTitled("A"), shouldResume = false)

        assertEquals(
            listOf(FakeQueueBoardHost.Call("setMediaItems", listOf(listOf("a", "b", "c"), 1, C.TIME_UNSET))),
            host.calls.filter { it.name == "setMediaItems" }
        )
        assertEquals("A", board.getCurrentQueue()?.title)
    }

    @Test
    fun loadingNoQueueClearsPlayer() {
        val a = board.addQueue("A", songs("a", "b"))!!
        board.setCurrQueue(a)

        assertNull(board.setCurrQueue(null))

        assertEquals(emptyList<String>(), host.songIds)
    }

    @Test
    fun loadingAfterDeletingLastQueueClearsPlayer() {
        val a = board.addQueue("A", songs("a", "b"))!!
        board.setCurrQueue(a)

        // As deleting a queue on the queue screen does.
        assertEquals(0, board.deleteQueue(a))
        board.setCurrQueue()

        assertEquals(emptyList<String>(), host.songIds)
    }

    @Test
    fun loadingEmptiedQueueClearsPlayer() {
        val a = board.addQueue("A", songs("a"))!!
        board.setCurrQueue(a)
        assertTrue(board.removeCurrentQueueSong(0))

        assertNull(board.setCurrQueue(a))

        assertEquals(emptyList<String>(), host.songIds)
    }

    @Test
    fun loadingQueueSetsItsShuffleState() {
        val a = board.addQueue("A", songs("a", "b", "c"))!!
        val b = board.addQueue("B", songs(*SHUFFLE_IDS), shuffled = true)!!

        board.setCurrQueue(b)
        assertTrue(host.shuffleModeEnabled)

        board.setCurrQueue(a)
        assertFalse(host.shuffleModeEnabled)
    }

    // selectQueueInPlayer()

    @Test
    fun selectQueueInPlayerSelectsQueueHoldingPlayerSongs() {
        board = storedBoard(storedQueue(1, "A", "a", "b"), storedQueue(2, "B", "c", "d"))
        host.setMediaItems(songs("a", "b"))
        host.calls.clear()

        assertTrue(board.selectQueueInPlayer())

        assertEquals("A", board.getCurrentQueue()?.title)
        assertEquals(listOf("B", "A"), board.getAllQueues().map { it.title })
        assertEquals(emptyList<FakeQueueBoardHost.Call>(), host.calls)
    }

    @Test
    fun selectQueueInPlayerPrefersCurrentQueue() {
        board = storedBoard(storedQueue(1, "A", "a", "b"), storedQueue(2, "B", "a", "b"))
        board.move(1, 0)
        host.setMediaItems(songs("a", "b"))

        assertTrue(board.selectQueueInPlayer())

        assertEquals("B", board.getCurrentQueue()?.title)
        assertEquals(listOf("B", "A"), board.getAllQueues().map { it.title })
    }

    @Test
    fun selectQueueInPlayerKeepsSelectionWithoutMatch() {
        board = storedBoard(storedQueue(1, "A", "a", "b"), storedQueue(2, "B", "c", "d"))
        host.setMediaItems(songs("a", "c"))

        assertFalse(board.selectQueueInPlayer())

        assertEquals("B", board.getCurrentQueue()?.title)
        assertEquals(listOf("A", "B"), board.getAllQueues().map { it.title })
    }

    // deleteQueue()

    @Test
    fun deleteQueueAboveSelectedKeepsSelection() {
        selectMiddleOfThreeQueues()

        board.deleteQueue(queueTitled("A"))

        assertEquals(listOf("B", "C"), board.getAllQueues().map { it.title })
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    @Test
    fun deleteQueueBelowSelectedKeepsSelection() {
        selectMiddleOfThreeQueues()

        board.deleteQueue(queueTitled("C"))

        assertEquals(listOf("A", "B"), board.getAllQueues().map { it.title })
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    @Test
    fun deleteSelectedQueueSelectsQueueAbove() {
        selectMiddleOfThreeQueues()

        board.deleteQueue(queueTitled("B"))

        assertEquals(listOf("A", "C"), board.getAllQueues().map { it.title })
        assertEquals("A", board.getCurrentQueue()?.title)
    }

    // move()

    @Test
    fun moveQueueWithoutPassingSelectedKeepsSelection() {
        selectLastOfThreeQueues()

        board.move(0, 1)

        assertEquals(listOf("C", "A", "B"), board.getAllQueues().map { it.title })
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    @Test
    fun moveQueueOntoSelectedFromEarlierKeepsSelection() {
        selectLastOfThreeQueues()

        board.move(0, 2)

        assertEquals(listOf("C", "B", "A"), board.getAllQueues().map { it.title })
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    @Test
    fun moveQueueOntoSelectedFromLaterKeepsSelection() {
        selectMiddleOfThreeQueues()

        board.move(2, 1)

        assertEquals(listOf("A", "C", "B"), board.getAllQueues().map { it.title })
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    @Test
    fun moveQueuePastSelectedToLaterKeepsSelection() {
        selectMiddleOfThreeQueues()

        board.move(0, 2)

        assertEquals(listOf("B", "C", "A"), board.getAllQueues().map { it.title })
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    @Test
    fun moveQueuePastSelectedToEarlierKeepsSelection() {
        selectMiddleOfThreeQueues()

        board.move(2, 0)

        assertEquals(listOf("C", "A", "B"), board.getAllQueues().map { it.title })
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    @Test
    fun moveSelectedQueueKeepsSelection() {
        selectLastOfThreeQueues()

        board.move(2, 0)

        assertEquals(listOf("B", "A", "C"), board.getAllQueues().map { it.title })
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    // setCurrQueuePosIndex()

    @Test
    fun selectingSameSongKeepsSavedPosition() {
        board = storedBoard(storedQueue(1, "A", "a", "b", "c", queuePos = 0))

        board.setCurrQueuePosIndex(0)
        board.setCurrQueue()

        assertEquals(
            listOf(FakeQueueBoardHost.Call("setMediaItems", listOf(listOf("a", "b", "c"), 0, SAVED_POSITION_MS))),
            host.calls.filter { it.name == "setMediaItems" }
        )
    }

    @Test
    fun selectingOtherSongClearsSavedPosition() {
        board = storedBoard(storedQueue(1, "A", "a", "b", "c", queuePos = 0))

        board.setCurrQueuePosIndex(2)
        board.setCurrQueue()

        assertEquals(
            listOf(FakeQueueBoardHost.Call("setMediaItems", listOf(listOf("a", "b", "c"), 2, C.TIME_UNSET))),
            host.calls.filter { it.name == "setMediaItems" }
        )
    }

    // Saving and readQueue()

    @Test
    fun savedQueuesReadBackUnchanged() {
        val a = board.addQueue("A", songs("a", "b", "a", "c"), startIndex = 1)!!
        board.addQueue("B", songs("d", "e"), startIndex = 1)
        board.setCurrQueue(a)
        board.moveSong(0, 3)
        board.addSongsToQueue(a, Int.MAX_VALUE, songs("f"))
        board.setCurrQueuePosIndex(3)

        val restored = readBack()

        assertEquals(listOf("B", "A"), restored.getAllQueues().map { it.title })
        assertSameQueue(queueTitled("A"), restored.queueTitled("A"))
        assertSameQueue(queueTitled("B"), restored.queueTitled("B"))
        assertEquals(listOf("b", "a", "c", "a", "f"), restored.queueTitled("A").ids())
        assertEquals(3, restored.queueTitled("A").getQueuePosShuffled())
    }

    @Test
    fun savedShuffledQueueReadsBackUnchanged() {
        val a = board.addQueue("A", songs(*SHUFFLE_IDS), startIndex = 2)!!
        board.setCurrQueue(a)
        toggleShuffle()
        board.addSongsToQueue(a, host.currentMediaItemIndex + 1, songs("h"))
        board.setCurrQueuePosIndex(4)

        val restored = readBack()
        val restoredA = restored.queueTitled("A")

        assertTrue(restoredA.shuffled)
        assertSameQueue(a, restoredA)

        // The original order comes back after unshuffling.
        restored.unShuffleCurrent()
        board.unShuffleCurrent()
        assertEquals(a.ids(), restoredA.ids())
        assertEquals(a.selectedId(), restoredA.selectedId())
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

    private fun queueTitled(title: String) = board.queueTitled(title)

    private fun QueueBoard.queueTitled(title: String) = getAllQueues().single { it.title == title }

    /** A queue as [io.github.yuuichi_s.astertune.db.MusicDatabase.readQueue] returns it. */
    private fun storedQueue(id: Long, title: String, vararg ids: String, queuePos: Int = 0) = MultiQueueObject(
        id = id,
        title = title,
        queue = songs(*ids).toMutableList(),
        shuffleOrder = ids.indices.toMutableList(),
        queuePos = queuePos,
        lastSongPos = SAVED_POSITION_MS,
        index = id.toInt() - 1,
    )

    /** Creates a board from stored queues, as the service does at startup. */
    private fun storedBoard(vararg queues: MultiQueueObject) =
        QueueBoard(host, queues = queues.toMutableList(), maxQueues = 5, storedQueuesLoaded = true, random = Random(1))

    /** Makes A, C, B the queue order with B selected. */
    private fun selectLastOfThreeQueues() {
        board.addQueue("A", songs("a"))
        board.addQueue("B", songs("b"))
        board.addQueue("C", songs("c"))
        board.setCurrQueue(queueTitled("B"))
        assertEquals(listOf("A", "C", "B"), board.getAllQueues().map { it.title })
    }

    /** Makes A, B, C the queue order with B selected. */
    private fun selectMiddleOfThreeQueues() {
        selectLastOfThreeQueues()
        board.move(2, 1)
        assertEquals(listOf("A", "B", "C"), board.getAllQueues().map { it.title })
        assertEquals("B", board.getCurrentQueue()?.title)
    }

    private fun assertNoPlaylistReset() =
        assertEquals(emptyList<FakeQueueBoardHost.Call>(), host.calls.filter { it.name == "setMediaItems" })

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

    /** Waits for the saves, then reads the queues into a new board as the service does at startup. */
    private fun readBack(): QueueBoard {
        val queues = runBlocking {
            board.flushDatabaseWrites()
            database.readQueue().queues
        }
        return QueueBoard(
            FakeQueueBoardHost(database, persistQueues = false),
            queues = queues.toMutableList(),
            maxQueues = 5,
            storedQueuesLoaded = true,
        )
    }

    private fun assertSameQueue(expected: MultiQueueObject, actual: MultiQueueObject) {
        assertEquals(expected.ids(), actual.ids())
        assertEquals(expected.shuffled, actual.shuffled)
        assertEquals(expected.getQueuePosShuffled(), actual.getQueuePosShuffled())
        assertEquals(expected.selectedId(), actual.selectedId())
    }

    private companion object {
        const val SAVED_POSITION_MS = 30_000L
        val SHUFFLE_IDS = arrayOf("a", "b", "c", "d", "e", "f", "g", "b")
    }
}
