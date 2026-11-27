/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.playback

import io.github.yuuichi_s.astertune.models.MultiQueueObject
import kotlinx.collections.immutable.ImmutableList

/**
 * Read-only view of [QueueBoard]. Changes to the queues go through [MusicService].
 */
interface QueueBoardReader {
    /** Open queues, oldest first. Reads are observable in Compose snapshots. */
    val masterQueues: List<MultiQueueObject>

    /** Returns an immutable snapshot of the queue list. */
    fun getAllQueues(): ImmutableList<MultiQueueObject>

    /** Returns the current queue, or null if there is none. */
    fun getCurrentQueue(): MultiQueueObject?
}
