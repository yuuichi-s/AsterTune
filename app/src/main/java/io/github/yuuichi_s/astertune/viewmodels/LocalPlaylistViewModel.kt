package io.github.yuuichi_s.astertune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import io.github.yuuichi_s.astertune.constants.PlaylistSongSortDescendingKey
import io.github.yuuichi_s.astertune.constants.PlaylistSongSortType
import io.github.yuuichi_s.astertune.constants.PlaylistSongSortTypeKey
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.PlaylistSong
import io.github.yuuichi_s.astertune.extensions.reversed
import io.github.yuuichi_s.astertune.extensions.toEnum
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.getDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlistWithSongs = combine(
        database.playlist(playlistId),
        database.playlistSongs(playlistId),
        context.dataStore.data
            .map {
                it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM) to
                        (it[PlaylistSongSortDescendingKey] ?: true)
            }
            .distinctUntilChanged()
    ) { playlist, songs, (sortType, sortDescending) ->
        val sortedSongs = when (sortType) {
            PlaylistSongSortType.CUSTOM -> songs
            PlaylistSongSortType.NAME -> songs.sortedBy { it.song.song.title.lowercase() }
            PlaylistSongSortType.ARTIST -> songs.sortedBy { song ->
                song.song.artists.joinToString { it.name }.lowercase()
            }
            PlaylistSongSortType.ADDED_DATE -> songs.sortedBy { it.song.song.inLibrary }
            PlaylistSongSortType.MODIFIED_DATE -> songs.sortedBy { it.song.song.dateModified }
            PlaylistSongSortType.RELEASE_DATE -> songs.sortedBy { it.song.song.getDateLong() }
            PlaylistSongSortType.DOWNLOAD_DATE -> songs.sortedWith(
                if (sortDescending) downloadDateComparator.reversed() else downloadDateComparator
            )
        }.reversed(
            sortDescending && sortType != PlaylistSongSortType.CUSTOM && sortType != PlaylistSongSortType.DOWNLOAD_DATE
        )

        Pair(playlist, sortedSongs)
    }.stateIn(viewModelScope, SharingStarted.Lazily, Pair(null, emptyList()))

    init {
        // Fix playlist song order
        viewModelScope.launch(Dispatchers.IO) {
            val sortedSongs = playlistWithSongs.first().second.sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, song ->
                    if (song.map.position != index) {
                        update(song.map.copy(position = index))
                    }
                }
            }
        }
    }
}

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
