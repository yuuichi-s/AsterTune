/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.models

import java.util.concurrent.atomic.AtomicLong

/**
 * A song in a queue. [entryId] tells apart the entries of a queue, including those of the same
 * song, while the process runs. It is not saved.
 */
data class QueueEntry(
    val song: MediaMetadata,
    val entryId: Long = nextEntryId.getAndIncrement(),
) {
    private companion object {
        val nextEntryId = AtomicLong()
    }
}
