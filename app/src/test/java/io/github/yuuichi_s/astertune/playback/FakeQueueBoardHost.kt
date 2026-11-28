/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback

import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.models.QueueEntry

/**
 * [QueueBoardHost] for tests. Keeps the player's playlist and current item as
 * [androidx.media3.common.Player] would, and records each call it receives.
 */
class FakeQueueBoardHost(
    override val database: MusicDatabase,
    var persistQueues: Boolean = true,
) : QueueBoardHost {
    data class Call(val name: String, val args: List<Any?> = emptyList())

    val calls = mutableListOf<Call>()

    private val songs = mutableListOf<QueueEntry>()

    /** Song IDs in the player's playlist. */
    val songIds get() = songs.map { it.song.id }

    override val mediaItemCount get() = songs.size
    override var currentMediaItemIndex = 0
        private set

    override fun getMediaItemIdAt(index: Int) = songs[index].song.id

    override val currentSongId get() = songs.getOrNull(currentMediaItemIndex)?.song?.id

    override var shuffleModeEnabled = false
        set(value) {
            calls += Call("setShuffleModeEnabled", listOf(value))
            field = value
        }

    override fun setMediaItems(entries: List<QueueEntry>) {
        calls += Call("setMediaItems", listOf(entries.map { it.song.id }))
        songs.clear()
        songs.addAll(entries)
        currentMediaItemIndex = 0
    }

    override fun setMediaItems(entries: List<QueueEntry>, startIndex: Int, startPositionMs: Long) {
        calls += Call("setMediaItems", listOf(entries.map { it.song.id }, startIndex, startPositionMs))
        songs.clear()
        songs.addAll(entries)
        currentMediaItemIndex = startIndex
    }

    override fun addMediaItems(entries: List<QueueEntry>) {
        calls += Call("addMediaItems", listOf(entries.map { it.song.id }))
        songs.addAll(entries)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        calls += Call("removeMediaItems", listOf(fromIndex, toIndex))
        replace(fromIndex, toIndex, emptyList())
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, entries: List<QueueEntry>) {
        calls += Call("replaceMediaItems", listOf(fromIndex, toIndex, entries.map { it.song.id }))
        replace(fromIndex, toIndex, entries)
    }

    /**
     * Removes the item at [index] as [androidx.media3.common.Player.removeMediaItem] would.
     * Not recorded in [calls], since [QueueBoard] does not make this call.
     *
     * @return Whether the current item changed, which makes the player report a transition
     */
    fun removeMediaItem(index: Int): Boolean {
        val removedCurrent = index == currentMediaItemIndex
        replace(index, index + 1, emptyList())
        return removedCurrent
    }

    /** Replaces the items in [fromIndex, toIndex), clamping [toIndex] to the playlist size. */
    private fun replace(fromIndex: Int, toIndex: Int, newSongs: List<QueueEntry>) {
        val end = minOf(toIndex, songs.size)
        val removed = end - fromIndex
        repeat(removed) { songs.removeAt(fromIndex) }
        songs.addAll(fromIndex, newSongs)
        currentMediaItemIndex = when {
            currentMediaItemIndex >= end -> currentMediaItemIndex - removed + newSongs.size
            currentMediaItemIndex >= fromIndex -> minOf(fromIndex, songs.size - 1).coerceAtLeast(0)
            else -> currentMediaItemIndex
        }
    }

    override fun onQueueLoaded() {
        calls += Call("onQueueLoaded")
    }

    override fun persistQueues() = persistQueues
}
