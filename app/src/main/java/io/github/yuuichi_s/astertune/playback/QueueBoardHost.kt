/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback

import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.models.MediaMetadata

/**
 * Player, settings, and database used by [QueueBoard].
 *
 * Player members follow the semantics of the same-named
 * [androidx.media3.common.Player] members, with songs given as [MediaMetadata].
 */
interface QueueBoardHost {
    val mediaItemCount: Int
    val currentMediaItemIndex: Int

    /** Song ID of the player item at [index]. */
    fun getMediaItemIdAt(index: Int): String

    /** Song ID of the current player item, or null if there is none. */
    val currentSongId: String?

    var shuffleModeEnabled: Boolean

    fun setMediaItems(songs: List<MediaMetadata>)
    fun setMediaItems(songs: List<MediaMetadata>, startIndex: Int, startPositionMs: Long)
    fun addMediaItems(songs: List<MediaMetadata>)
    fun removeMediaItems(fromIndex: Int, toIndex: Int)
    fun replaceMediaItems(fromIndex: Int, toIndex: Int, songs: List<MediaMetadata>)

    /** Called after a queue has been loaded into the player. Main thread only. */
    fun onQueueLoaded()

    /** Whether the "persistent queue" setting is enabled. */
    fun persistQueues(): Boolean

    val database: MusicDatabase
}
