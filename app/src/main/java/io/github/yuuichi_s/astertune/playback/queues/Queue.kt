package io.github.yuuichi_s.astertune.playback.queues

import io.github.yuuichi_s.astertune.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?
    val playlistId: String?
    val startShuffled: Boolean
    suspend fun getInitialStatus(): Status
    fun hasNextPage(): Boolean
    suspend fun nextPage(): List<MediaMetadata>

    data class Status(
        val title: String?,
        val items: List<MediaMetadata>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    )
}
