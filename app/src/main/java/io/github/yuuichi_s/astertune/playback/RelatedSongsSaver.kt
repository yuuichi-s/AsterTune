/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.playback

import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.RelatedSongMap
import io.github.yuuichi_s.astertune.models.toMediaMetadata

/** Saves the related songs of a played song, which quick picks are built from. */
class RelatedSongsSaver(
    private val database: MusicDatabase,
    private val fetchRelatedSongs: suspend (mediaId: String) -> List<SongItem>? = { mediaId ->
        YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
            ?.let { YouTube.related(it).getOrNull() }?.songs
    },
) {
    /**
     * Fetches missing related songs and schedules a database write.
     * This method returns without waiting for the write to finish.
     */
    suspend fun saveIfMissing(mediaId: String) {
        if (database.hasRelatedSongs(mediaId)) return
        val songs = fetchRelatedSongs(mediaId) ?: return
        // Calls for the same song can overlap while fetching (one runs for every data source open).
        // Re-check inside the serialized transaction so only the first result is stored.
        database.transaction {
            if (hasRelatedSongs(mediaId)) return@transaction
            songs
                .distinctBy { it.id }
                .map(SongItem::toMediaMetadata)
                .onEach(::insert)
                .map {
                    RelatedSongMap(
                        songId = mediaId,
                        relatedSongId = it.id
                    )
                }
                .forEach(::insert)
        }
    }
}
