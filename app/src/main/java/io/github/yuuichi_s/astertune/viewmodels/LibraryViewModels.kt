/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.yuuichi_s.astertune.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yuuichi_s.astertune.constants.AlbumFilter
import io.github.yuuichi_s.astertune.constants.AlbumFilterKey
import io.github.yuuichi_s.astertune.constants.AlbumSortDescendingKey
import io.github.yuuichi_s.astertune.constants.AlbumSortType
import io.github.yuuichi_s.astertune.constants.AlbumSortTypeKey
import io.github.yuuichi_s.astertune.constants.ArtistFilter
import io.github.yuuichi_s.astertune.constants.ArtistFilterKey
import io.github.yuuichi_s.astertune.constants.ArtistSongSortDescendingKey
import io.github.yuuichi_s.astertune.constants.ArtistSongSortType
import io.github.yuuichi_s.astertune.constants.ArtistSongSortTypeKey
import io.github.yuuichi_s.astertune.constants.ArtistSortDescendingKey
import io.github.yuuichi_s.astertune.constants.ArtistSortType
import io.github.yuuichi_s.astertune.constants.ArtistSortTypeKey
import io.github.yuuichi_s.astertune.constants.FolderSongSortType
import io.github.yuuichi_s.astertune.constants.LibrarySortDescendingKey
import io.github.yuuichi_s.astertune.constants.LibrarySortType
import io.github.yuuichi_s.astertune.constants.LibrarySortTypeKey
import io.github.yuuichi_s.astertune.constants.LocalAlbumSortDescendingKey
import io.github.yuuichi_s.astertune.constants.LocalAlbumSortTypeKey
import io.github.yuuichi_s.astertune.constants.LocalArtistSortDescendingKey
import io.github.yuuichi_s.astertune.constants.LocalArtistSortTypeKey
import io.github.yuuichi_s.astertune.constants.LocalPlaylistSortDescendingKey
import io.github.yuuichi_s.astertune.constants.LocalPlaylistSortTypeKey
import io.github.yuuichi_s.astertune.constants.LocalSongSortDescendingKey
import io.github.yuuichi_s.astertune.constants.LocalSongSortTypeKey
import io.github.yuuichi_s.astertune.constants.PlaylistFilter
import io.github.yuuichi_s.astertune.constants.PlaylistFilterKey
import io.github.yuuichi_s.astertune.constants.PlaylistSortDescendingKey
import io.github.yuuichi_s.astertune.constants.PlaylistSortType
import io.github.yuuichi_s.astertune.constants.PlaylistSortTypeKey
import io.github.yuuichi_s.astertune.constants.SongFilter
import io.github.yuuichi_s.astertune.constants.SongFilterKey
import io.github.yuuichi_s.astertune.constants.SongSortDescendingKey
import io.github.yuuichi_s.astertune.constants.SongSortType
import io.github.yuuichi_s.astertune.constants.SongSortTypeKey
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.Album
import io.github.yuuichi_s.astertune.db.entities.Artist
import io.github.yuuichi_s.astertune.db.entities.Playlist
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.extensions.toEnum
import io.github.yuuichi_s.astertune.models.DirectoryTree
import io.github.yuuichi_s.astertune.ui.utils.STORAGE_ROOT
import io.github.yuuichi_s.astertune.ui.utils.cacheDirectoryTree
import io.github.yuuichi_s.astertune.ui.utils.getDirectoryTree
import io.github.yuuichi_s.astertune.utils.SyncUtils
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.numberToAlpha
import io.github.yuuichi_s.astertune.utils.reportException
import io.github.yuuichi_s.astertune.utils.scanners.LocalMediaScanner.Companion.refreshLocal
import com.zionhuang.innertube.YouTube
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class LibrarySongsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allSongs = getSyncedSongs(context, database)
    val isSyncingRemoteLikedSongs = syncUtils.isSyncingRemoteLikedSongs
    val isSyncingRemoteSongs = syncUtils.isSyncingRemoteSongs

    fun syncLibrarySongs(bypassCd: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncRemoteSongs(bypassCd) }
    }

    fun syncLikedSongs(bypassCd: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncRemoteLikedSongs(bypassCd) }
    }

    private fun getSyncedSongs(context: Context, database: MusicDatabase): StateFlow<List<Song>?> {

        return context.dataStore.data
            .map {
                Triple(
                    it[SongFilterKey].toEnum(SongFilter.LIKED),
                    it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                    (it[SongSortDescendingKey] != false)
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending)
                    SongFilter.LIKED -> database.likedSongs(sortType, descending)
                    SongFilter.DOWNLOADED -> database.downloadSongs(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }
}

@HiltViewModel
class LocalLibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    val filteredSongs = mutableStateListOf<Song>()

    val localSongs: StateFlow<List<Song>?> = combine(
        database.allLocalSongsFlow(),
        context.dataStore.data
            .map {
                it[LocalSongSortTypeKey].toEnum(FolderSongSortType.TRACK_NUMBER) to
                        (it[LocalSongSortDescendingKey] == true)
            }
            .distinctUntilChanged()
    ) { songs, (sortType, descending) ->
        sortLocalSongs(songs, sortType, descending)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val localAlbums: StateFlow<List<Album>?> = context.dataStore.data
        .map {
            it[LocalAlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE) to (it[LocalAlbumSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.albums(AlbumFilter.LIBRARY, sortType, descending, localOnly = true)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val localArtists: StateFlow<List<Artist>?> = context.dataStore.data
        .map {
            it[LocalArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE) to (it[LocalArtistSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            combine(
                database.artists(ArtistFilter.LIBRARY, sortType, descending, localOnly = true),
                database.localArtistThumbnails(),
            ) { artists, thumbnails ->
                val thumbnailByArtist = thumbnails
                    .filter { it.thumbnailUrl != null }
                    .associate { it.artistId to it.thumbnailUrl }
                artists.map { artist ->
                    val fallback = thumbnailByArtist[artist.id]
                    if (artist.artist.isLocal && artist.artist.thumbnailUrl == null && fallback != null) {
                        artist.copy(artist = artist.artist.copy(thumbnailUrl = fallback))
                    } else {
                        artist
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val localPlaylists: StateFlow<List<Playlist>?> = context.dataStore.data
        .map {
            it[LocalPlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[LocalPlaylistSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.playlists(PlaylistFilter.LIBRARY, sortType, descending, variant = 0, localSongsOnly = true)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun search(query: String) {
        if (query.isBlank()) {
            filteredSongs.clear()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = database.searchSongsAllLocal(query).first()
            filteredSongs.clear()
            filteredSongs.addAll(result)
        }
    }

    private fun sortLocalSongs(
        songs: List<Song>,
        sortType: FolderSongSortType,
        descending: Boolean,
    ): List<Song> {
        val sorted = songs.sortedBy {
            when (sortType) {
                FolderSongSortType.CREATE_DATE -> numberToAlpha(it.song.inLibrary?.toEpochSecond(ZoneOffset.UTC) ?: -1L)
                FolderSongSortType.MODIFIED_DATE -> numberToAlpha(it.song.getDateModifiedLong() ?: -1L)
                FolderSongSortType.RELEASE_DATE -> numberToAlpha(it.song.getDateLong() ?: -1L)
                FolderSongSortType.NAME -> it.song.title.lowercase()
                FolderSongSortType.ARTIST -> it.artists.joinToString { artist -> artist.name }.lowercase()
                FolderSongSortType.PLAY_COUNT -> numberToAlpha((it.playCount?.sumOf { pc -> pc.count })?.toLong() ?: 0L)
                FolderSongSortType.TRACK_NUMBER -> numberToAlpha(it.song.trackNumber?.toLong() ?: Long.MAX_VALUE)
            }
        }
        return if (descending) sorted.reversed() else sorted
    }
}

@HiltViewModel
class LibraryFoldersViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val TAG = LibraryFoldersViewModel::class.simpleName.toString()
    val path = savedStateHandle.get<String>("path")?.replace(';', '/') ?: STORAGE_ROOT

    val localSongDirectoryTree: MutableStateFlow<DirectoryTree> = MutableStateFlow(getDirectoryTree(path))
    val localSongDtSongCount = MutableStateFlow(0)
    val filteredSongs = mutableStateListOf<Song>()

    var uiInit = false
    var lastLocalScan = 0L

    /**
     * Trigger a scan of local directory
     */
    suspend fun getLocalSongs(dir: String? = null) {
        Log.d(TAG, "Loading folders page: ${dir ?: path}")
        val dt = refreshLocal(database, dir ?: path)
        dt.isSkeleton = false
        cacheDirectoryTree(dt)
        localSongDirectoryTree.value = dt
    }

    /**
     * Get total number of songs in directory
     */
    suspend fun getSongCount(dir: String? = null) {
        Log.d(TAG, "Loading folder song count: ${dir ?: path}")
        localSongDtSongCount.value = database.localSongCountInPath(dir ?: path).first()
    }

    /**
     * Update filteredSongs with search query
     */
    fun searchInDir(query: String, dir: String = path) {
        if (query.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                val dbSongs = database.searchSongsAllLocalInDir(dir, query).first()
                filteredSongs.clear()
                filteredSongs.addAll(dbSongs)
            }
        }
    }
}

@HiltViewModel
class LibraryArtistsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val isSyncingRemoteArtists = syncUtils.isSyncingRemoteArtists

    val allArtists = context.dataStore.data
        .map {
            Triple(
                it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                it[ArtistSortDescendingKey] ?: true
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sortType, descending) ->
            database.artists(filter, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun syncArtists(bypassCd: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncRemoteArtists(bypassCd) }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allArtists.collect { artists ->
                artists
                    ?.map { it.artist }
                    ?.filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }
                    ?.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryAlbumsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val isSyncingRemoteAlbums = syncUtils.isSyncingRemoteAlbums

    val allAlbums = context.dataStore.data
        .map {
            Triple(
                it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                it[AlbumSortDescendingKey] ?: true
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sortType, descending) ->
            database.albums(filter, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun syncAlbums(bypassCd: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncRemoteAlbums(bypassCd) }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
                albums
                    ?.filter {
                        !it.album.isLocal && it.album.songCount == 0
                    }?.forEach { album ->
                        YouTube.album(album.id).onSuccess { albumPage ->
                            database.query {
                                update(album.album, albumPage)
                            }
                        }.onFailure {
                            reportException(it)
                            if (it.message?.contains("NOT_FOUND") == true) {
                                database.query {
                                    delete(album.album)
                                }
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val isSyncingRemotePlaylists = syncUtils.isSyncingRemotePlaylists

    val allPlaylists = context.dataStore.data
        .map {
            Triple(
                it[PlaylistFilterKey].toEnum(PlaylistFilter.LIBRARY),
                it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE),
                it[PlaylistSortDescendingKey] ?: true
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sortType, descending) ->
            database.playlists(filter, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun syncPlaylists(bypassCd: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncRemotePlaylists(bypassCd) }
    }
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils
) : ViewModel() {

    val isSyncingRemoteLikedSongs = syncUtils.isSyncingRemoteLikedSongs
    val isSyncingRemoteSongs = syncUtils.isSyncingRemoteSongs
    val isSyncingRemoteAlbums = syncUtils.isSyncingRemoteAlbums
    val isSyncingRemoteArtists = syncUtils.isSyncingRemoteArtists
    val isSyncingRemotePlaylists = syncUtils.isSyncingRemotePlaylists

    var artists = database.artistsBookmarkedAsc().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = database.albumsLikedAsc().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = database.playlistInLibraryAsc().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allItems = context.dataStore.data
        .map {
            it[LibrarySortTypeKey].toEnum(LibrarySortType.CREATE_DATE) to (it[LibrarySortDescendingKey] != false)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            combine(artists, albums, playlists) { artists, albums, playlists ->
                val items = artists + albums + playlists
                items.sortedBy { item ->
                    when (sortType) {
                        LibrarySortType.CREATE_DATE -> when (item) {
                            is Album -> item.album.bookmarkedAt
                            is Artist -> item.artist.bookmarkedAt
                            is Playlist -> item.playlist.bookmarkedAt
                            else -> LocalDateTime.now()
                        }

                        else -> when (item) {
                            is Album -> item.album.title.lowercase()
                            is Artist -> item.artist.name.lowercase()
                            is Playlist -> item.playlist.name.lowercase()
                            else -> ""
                        }
                    }.toString()
                }.let { if (descending) it.reversed() else it }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncAll(bypassCd: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.tryAutoSync(bypassCd) }
    }
}

@HiltViewModel
class ArtistSongsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs = context.dataStore.data
        .map {
            it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey]
                ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.artistSongs(artistId, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
