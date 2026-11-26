/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The saved current queue. The table holds at most one row.
 *
 * @property queueId null when there is no current queue
 */
@Entity(tableName = "current_queue")
data class CurrentQueueEntity(
    @PrimaryKey val id: Int = ROW_ID,
    val queueId: Long?,
) {
    companion object {
        const val ROW_ID = 0
    }
}
