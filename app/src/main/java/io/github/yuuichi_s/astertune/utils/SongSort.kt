/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils

import androidx.media3.exoplayer.offline.Download
import io.github.yuuichi_s.astertune.constants.FolderSongSortType
import io.github.yuuichi_s.astertune.constants.PlaylistSongSortType
import io.github.yuuichi_s.astertune.db.entities.PlaylistSong
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.extensions.reversed
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Song order of the local songs screen and the folder screen. */
fun sortLocalSongs(
    songs: List<Song>,
    sortType: FolderSongSortType,
    descending: Boolean,
): List<Song> {
    val comparator: Comparator<Song> = when (sortType) {
        FolderSongSortType.CREATE_DATE -> compareBy { numberToAlpha(it.song.inLibrary?.toEpochSecond(ZoneOffset.UTC) ?: -1L) }
        FolderSongSortType.MODIFIED_DATE -> compareBy { numberToAlpha(it.song.getDateModifiedLong() ?: -1L) }
        // numberToAlpha() cannot order the negative values of release dates before 1970.
        FolderSongSortType.RELEASE_DATE -> compareBy(nullsFirst<Long>()) { it.song.getDateLong() }
        FolderSongSortType.NAME -> compareBy { it.song.title.lowercase() }
        FolderSongSortType.ARTIST -> compareBy { it.artists.joinToString { artist -> artist.name }.lowercase() }
        FolderSongSortType.PLAY_COUNT -> compareBy { numberToAlpha((it.playCount?.sumOf { pc -> pc.count })?.toLong() ?: 0L) }
        FolderSongSortType.TRACK_NUMBER -> compareBy { numberToAlpha(it.song.trackNumber?.toLong() ?: Long.MAX_VALUE) }
    }
    val sorted = songs.sortedWith(comparator)
    return if (descending) sorted.reversed() else sorted
}

/**
 * Applies the song order used by local playlists.
 *
 * [PlaylistSongSortType.CUSTOM] keeps input order regardless of [descending].
 * [PlaylistSongSortType.DOWNLOAD_DATE] groups remote songs without a completed download, local
 * songs without a completed download, and completed downloads. Descending reverses the group and
 * completed-date order while equal keys retain input order.
 */
fun sortPlaylistSongs(
    songs: List<PlaylistSong>,
    sortType: PlaylistSongSortType,
    descending: Boolean,
): List<PlaylistSong> =
    when (sortType) {
        PlaylistSongSortType.CUSTOM -> songs
        PlaylistSongSortType.NAME -> songs.sortedBy { it.song.song.title.lowercase() }
        PlaylistSongSortType.ARTIST -> songs.sortedBy { song ->
            song.song.artists.joinToString { it.name }.lowercase()
        }
        PlaylistSongSortType.ADDED_DATE -> songs.sortedBy { it.song.song.inLibrary }
        PlaylistSongSortType.MODIFIED_DATE -> songs.sortedBy { it.song.song.dateModified }
        PlaylistSongSortType.RELEASE_DATE -> songs.sortedBy { it.song.song.getDateLong() }
        PlaylistSongSortType.DOWNLOAD_DATE -> songs.sortedWith(
            if (descending) downloadDateComparator.reversed() else downloadDateComparator
        )
    }.reversed(
        descending && sortType != PlaylistSongSortType.CUSTOM && sortType != PlaylistSongSortType.DOWNLOAD_DATE
    )

private val downloadDateComparator = compareBy<PlaylistSong>(
    { song ->
        when {
            song.completedDownloadDate() != null -> 2
            song.song.song.isLocal -> 1
            else -> 0
        }
    },
    { it.completedDownloadDate() }
)

private fun PlaylistSong.completedDownloadDate(): LocalDateTime? =
    song.song.dateDownload?.takeIf { getDownloadState(it) == Download.STATE_COMPLETED }
