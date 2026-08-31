/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.github.yuuichi_s.astertune.constants.SYNC_DEBUG
import io.github.yuuichi_s.astertune.constants.LastAlbumSyncKey
import io.github.yuuichi_s.astertune.constants.LastArtistSyncKey
import io.github.yuuichi_s.astertune.constants.LastFullSyncKey
import io.github.yuuichi_s.astertune.constants.LastLibSongSyncKey
import io.github.yuuichi_s.astertune.constants.LastLikeSongSyncKey
import io.github.yuuichi_s.astertune.constants.LastPlaylistSyncKey
import io.github.yuuichi_s.astertune.constants.LastRecentActivitySyncKey
import io.github.yuuichi_s.astertune.constants.SYNC_CD_SECONDS
import io.github.yuuichi_s.astertune.constants.SyncConflictResolution
import io.github.yuuichi_s.astertune.constants.SyncContent
import io.github.yuuichi_s.astertune.constants.YtmSyncConflictKey
import io.github.yuuichi_s.astertune.constants.YtmSyncContentKey
import io.github.yuuichi_s.astertune.constants.decodeSyncString
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.PlaylistEntity
import io.github.yuuichi_s.astertune.db.entities.PlaylistSongMap
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import io.github.yuuichi_s.astertune.extensions.isAutoSyncEnabled
import io.github.yuuichi_s.astertune.extensions.isInternetConnected
import io.github.yuuichi_s.astertune.extensions.isUserLoggedIn
import io.github.yuuichi_s.astertune.extensions.toEnum
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.playback.DownloadUtil
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.utils.completed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton class for syncing local data from remote YouTube Music
 */
@Singleton
class SyncUtils @Inject constructor(
    val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
    @ApplicationContext private val context: Context
) {
    private val TAG = "SyncUtils"

    private val scope =  CoroutineScope(syncCoroutine)

    private val _isSyncingRemoteLikedSongs = MutableStateFlow(false)
    private val _isSyncingRemoteSongs = MutableStateFlow(false)
    private val _isSyncingRemoteAlbums = MutableStateFlow(false)
    private val _isSyncingRemoteArtists = MutableStateFlow(false)
    private val _isSyncingRemotePlaylists = MutableStateFlow(false)
    private val _isSyncingRecentActivity = MutableStateFlow(false)

    val isSyncingRemoteLikedSongs: StateFlow<Boolean> = _isSyncingRemoteLikedSongs.asStateFlow()
    val isSyncingRemoteSongs: StateFlow<Boolean> = _isSyncingRemoteSongs.asStateFlow()
    val isSyncingRemoteAlbums: StateFlow<Boolean> = _isSyncingRemoteAlbums.asStateFlow()
    val isSyncingRemoteArtists: StateFlow<Boolean> = _isSyncingRemoteArtists.asStateFlow()
    val isSyncingRemotePlaylists: StateFlow<Boolean> = _isSyncingRemotePlaylists.asStateFlow()
    val isSyncingRecentActivity: StateFlow<Boolean> = _isSyncingRecentActivity.asStateFlow()

    companion object {
        const val DEFAULT_SYNC_CONTENT = "ARPLSC"
    }

    suspend fun tryAutoSync(bypassCd: Boolean = false) {
        if (SYNC_DEBUG) Log.d(TAG, "Starting auto sync job")
        // REQUIRED: signed in. A signed-out read is indistinguishable from an empty library.
        if (!context.isUserLoggedIn()) {
            return
        }
        // OPTIONAL: auto sync and cooldown. A manual request runs regardless of both.
        if (!bypassCd) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastFullSyncKey)) {
                return
            }
        }

        syncRemoteLikedSongs(bypassCd)
        syncRemoteSongs(bypassCd)
        syncRemoteAlbums(bypassCd)
        syncRemoteArtists(bypassCd)
        syncRemotePlaylists(bypassCd)
        context.dataStore.edit { settings ->
            settings[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        }
    }

    private fun checkEnabled(item: SyncContent): Boolean {
        return decodeSyncString(context.dataStore.get(YtmSyncContentKey, DEFAULT_SYNC_CONTENT)).contains(item)
    }

    private fun checkPartialSyncEligibility(key: Preferences.Key<Long>): Boolean {
        val lastSync = context.dataStore.get(key, 0L)
        val currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        val elapsed = currentTime - lastSync
        if (elapsed < SYNC_CD_SECONDS) {
            if (SYNC_DEBUG) Log.d(TAG, "Aborting auto sync. ${(SYNC_CD_SECONDS - elapsed) / 60} minutes until eligible")
            return false
        }
        return true
    }

    private fun checkOverwrite(item: SyncConflictResolution): Boolean {
        return context.dataStore.get(YtmSyncConflictKey, SyncConflictResolution.ADD_ONLY.name)
            .toEnum(defaultValue = SyncConflictResolution.ADD_ONLY) == item
    }

    /**
     * Like single song
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun likeSong(s: SongEntity) {
        scope.launch {
            YouTube.likeVideo(s.id, s.liked)
        }
    }

    /**
     * Add/remove to library single song
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun changeInLibrary(s: SongEntity) {
        scope.launch {
            // we don't have an api call yet
        }
    }

    /**
     * Singleton syncRemoteLikedSongs
     */
    suspend fun syncRemoteLikedSongs(bypass: Boolean = false) {
        // REQUIRED: signed in, internet and category enabled
        if (!context.isUserLoggedIn() || !checkEnabled(SyncContent.LIKED_SONGS) || !context.isInternetConnected()) {
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastLikeSongSyncKey)) {
                return
            }
        }
        // REQUIRED: no ongoing sync. Claim the flag atomically so two starts cannot both pass.
        if (!_isSyncingRemoteLikedSongs.compareAndSet(expect = false, update = true)) {
            if (SYNC_DEBUG) Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        var synced = false

        try {
            if (SYNC_DEBUG) Log.d(TAG, "Liked songs synchronization started")

            // Get remote and local liked songs
            YouTube.playlist("LM").completed().onSuccess { page ->
                if (!context.isInternetConnected()) {
                    return
                }

                val remoteSongs = page.songs.reversed()

                // Identify local songs to unlike
                val songsToUnlike = database.likedSongsByNameAsc().first()
                    .filterNot { it.song.isLocal }
                    .filterNot { localSong -> remoteSongs.any { it.id == localSong.id } }

                // Unlike local songs in the database
                runBlocking {
                    songsToUnlike.forEach { song ->
                        launch(Dispatchers.IO) {
                            database.update(song.song.localToggleLike())
                        }
                    }
                }

                // Insert or like songs in the database
                for (remoteSong in remoteSongs) {
                    val localSong = database.song(remoteSong.id).firstOrNull()
                    database.transaction {
                        if (localSong == null) {
                            insert(remoteSong.toMediaMetadata(), SongEntity::localToggleLike)
                        } else if (!localSong.song.liked) {
                            update(localSong.song.localToggleLike())
                        }
                    }
                }
                synced = true
            }

        } finally {
            // Release first: the suspending write below is skipped when the coroutine is cancelled.
            _isSyncingRemoteLikedSongs.value = false
            // Only a completed sync starts the cooldown.
            if (synced) {
                context.dataStore.edit { settings ->
                    settings[LastLikeSongSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                }
            }
            if (SYNC_DEBUG) Log.i(TAG, "Liked songs synchronization ended")
        }
    }

    /**
     * Singleton syncRemoteSongs
     */
    suspend fun syncRemoteSongs(bypass: Boolean = false) {
        // REQUIRED: signed in, internet and category enabled
        if (!context.isUserLoggedIn() || !checkEnabled(SyncContent.PRIVATE_SONGS) || !context.isInternetConnected()) {
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastLibSongSyncKey)) {
                return
            }
        }
        // REQUIRED: no ongoing sync. Claim the flag atomically so two starts cannot both pass.
        if (!_isSyncingRemoteSongs.compareAndSet(expect = false, update = true)) {
            if (SYNC_DEBUG) Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        var synced = false

        try {
            if (SYNC_DEBUG) Log.i(TAG, "Library songs synchronization started")

            // Get remote songs (from library and uploads)
            val remoteSongs =
                getRemoteData<SongItem>("FEmusic_liked_videos", "FEmusic_library_privately_owned_tracks") ?: return
            if (!context.isInternetConnected()) {
                return
            }

            if (checkOverwrite(SyncConflictResolution.OVERWRITE_WITH_REMOTE)) {
                // Identify local songs to remove
                val songsToRemoveFromLibrary = database.songsByNameAsc().first()
                    .filterNot { it.song.isLocal }
                    .filterNot { localSong -> remoteSongs.any { it.id == localSong.id } }

                // Remove local songs from the database
                runBlocking {
                    songsToRemoveFromLibrary.forEach { song ->
                        launch(Dispatchers.IO) {
                            database.update(song.song.toggleLibrary())
                        }
                    }
                }
            }

            // Inset or mark songs to library
            runBlocking {
                val jobs = remoteSongs.map { song ->
                    launch(Dispatchers.IO) {
                        val dbSong = database.song(song.id).firstOrNull()
                        database.transaction {
                            if (dbSong == null) {
                                insert(song.toMediaMetadata(), SongEntity::toggleLibrary)
                            } else if (dbSong.song.inLibrary == null) {
                                update(dbSong.song.toggleLibrary())
                            }
                        }
                    }
                }
                jobs.joinAll()
            }
            synced = true
        } finally {
            _isSyncingRemoteSongs.value = false
            if (synced) {
                context.dataStore.edit { settings ->
                    settings[LastLibSongSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                }
            }
            if (SYNC_DEBUG) Log.i(TAG, "Library songs synchronization ended")
        }
    }

    /**
     * Singleton syncRemoteAlbums
     */
    suspend fun syncRemoteAlbums(bypass: Boolean = false) {
        // REQUIRED: signed in, internet and category enabled
        if (!context.isUserLoggedIn() || !checkEnabled(SyncContent.ALBUMS) || !context.isInternetConnected()) {
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastAlbumSyncKey)) {
                return
            }
        }
        // REQUIRED: no ongoing sync. Claim the flag atomically so two starts cannot both pass.
        if (!_isSyncingRemoteAlbums.compareAndSet(expect = false, update = true)) {
            if (SYNC_DEBUG) Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        var synced = false

        try {
            if (SYNC_DEBUG) Log.i(TAG, "Library albums synchronization started")

            // Get remote albums (from library and uploads)
            val remoteAlbums =
                getRemoteData<AlbumItem>("FEmusic_liked_albums", "FEmusic_library_privately_owned_releases") ?: return
            if (!context.isInternetConnected()) {
                return
            }

            if (checkOverwrite(SyncConflictResolution.OVERWRITE_WITH_REMOTE)) {
                // Identify local albums to remove
                val albumsToRemoveFromLibrary = database.albumsLikedAsc().first()
                    .filterNot { it.album.isLocal }
                    .filterNot { localAlbum -> remoteAlbums.any { it.id == localAlbum.id } }

                // Remove albums from local database
                runBlocking {
                    albumsToRemoveFromLibrary.forEach { album ->
                        launch(Dispatchers.IO) {
                            database.update(album.album.localToggleLike())
                        }
                    }
                }
            }

            // Add or mark albums in local database
            runBlocking {
                remoteAlbums.forEach { remoteAlbum ->
                    launch(Dispatchers.IO) {
                        val localAlbum = database.album(remoteAlbum.id).firstOrNull()
                        if (localAlbum == null) {
                            database.insert(remoteAlbum)
                            database.album(remoteAlbum.id).firstOrNull()?.let {
                                database.update(it.album.localToggleLike())
                            }
                        } else if (localAlbum.album.bookmarkedAt == null) {
                            database.update(localAlbum.album.localToggleLike())
                        }
                    }
                }
            }
            synced = true
        } finally {
            _isSyncingRemoteAlbums.value = false
            if (synced) {
                context.dataStore.edit { settings ->
                    settings[LastAlbumSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                }
            }
            if (SYNC_DEBUG) Log.i(TAG, "Library albums synchronization ended")
        }
    }

    /**
     * Singleton syncRemoteArtists
     */
    suspend fun syncRemoteArtists(bypass: Boolean = false) {
        // REQUIRED: signed in, internet and category enabled
        if (!context.isUserLoggedIn() || !checkEnabled(SyncContent.ARTISTS) || !context.isInternetConnected()) {
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastArtistSyncKey)) {
                return
            }
        }
        // REQUIRED: no ongoing sync. Claim the flag atomically so two starts cannot both pass.
        if (!_isSyncingRemoteArtists.compareAndSet(expect = false, update = true)) {
            if (SYNC_DEBUG) Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        var synced = false

        try {
            if (SYNC_DEBUG) Log.i(TAG, "Artist subscriptions synchronization started")

            // Get remote artists (from library and uploads)
            val likedArtists = getRemoteData<ArtistItem>(
                "FEmusic_library_corpus_artists",
                "FEmusic_library_privately_owned_artists"
            ) ?: return
            val trackArtists = getRemoteData<ArtistItem>(
                "FEmusic_library_corpus_track_artists",
                "FEmusic_library_privately_owned_artists"
            ) ?: return
            val remoteArtists = mutableListOf<ArtistItem>().apply {
                addAll(likedArtists)
                addAll(trackArtists.filterNot { trackArtist ->
                    likedArtists.any { it.id == trackArtist.id }
                })
            }

            if (!context.isInternetConnected()) {
                return
            }

            if (checkOverwrite(SyncConflictResolution.OVERWRITE_WITH_REMOTE)) {
                // Get local artists
                val artistsToRemoveFromSubscriptions = database.artistsBookmarkedAsc().first()
                    .filterNot { it.artist.isLocal }
                    .filterNot { localArtist -> likedArtists.any { it.id == localArtist.id } }

                // Remove local artists from the database
                runBlocking {
                    artistsToRemoveFromSubscriptions.forEach { artist ->
                        launch(Dispatchers.IO) {
                            database.update(artist.artist.localToggleLike())
                        }
                    }
                }
            }

            // Add or mark artists in the database
            runBlocking {
                remoteArtists.forEach { remoteArtist ->
                    launch(Dispatchers.IO) {
                        val localArtist = database.artist(remoteArtist.id).firstOrNull()
                        val isLikedArtist = likedArtists.contains(remoteArtist)

                        database.transaction {
                            if (localArtist == null) {
                                insert(
                                    ArtistEntity(
                                        id = remoteArtist.id,
                                        name = remoteArtist.title,
                                        thumbnailUrl = remoteArtist.thumbnail,
                                        channelId = remoteArtist.channelId,
                                        bookmarkedAt = if (isLikedArtist) LocalDateTime.now() else null
                                    )
                                )
                            } else {
                                var updated = localArtist.artist
                                if (updated.bookmarkedAt == null && isLikedArtist) {
                                    updated = updated.localToggleLike()
                                }
                                if (updated.thumbnailUrl == null && remoteArtist.thumbnail != null) {
                                    updated = updated.copy(thumbnailUrl = remoteArtist.thumbnail)
                                }
                                if (updated != localArtist.artist) {
                                    update(updated)
                                }
                            }
                        }
                    }
                }
            }
            synced = true
        } finally {
            _isSyncingRemoteArtists.value = false
            if (synced) {
                context.dataStore.edit { settings ->
                    settings[LastArtistSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                }
            }
            if (SYNC_DEBUG) Log.i(TAG, "Artist subscriptions synchronization ended")
        }
    }

    /**
     * Singleton syncRemotePlaylists
     */
    suspend fun syncRemotePlaylists(bypass: Boolean = false) {
        // REQUIRED: signed in, internet and category enabled
        if (!context.isUserLoggedIn() || !checkEnabled(SyncContent.PLAYLISTS) || !context.isInternetConnected()) {
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastPlaylistSyncKey)) {
                return
            }
        }
        // REQUIRED: no ongoing sync. Claim the flag atomically so two starts cannot both pass.
        if (!_isSyncingRemotePlaylists.compareAndSet(expect = false, update = true)) {
            if (SYNC_DEBUG) Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        var synced = false

        try {
            if (SYNC_DEBUG) Log.i(TAG, "Library playlist synchronization started")

            // Get remote and local playlists
            YouTube.library("FEmusic_liked_playlists").completed().onSuccess { page ->
                if (!context.isInternetConnected()) {
                    return
                }

                val remotePlaylists = page.items.filterIsInstance<PlaylistItem>()
                    .filterNot { it.id == "LM" || it.id == "SE" }
                    .reversed()

                val localPlaylists = database.playlistInLibraryAsc().first()

                if (checkOverwrite(SyncConflictResolution.OVERWRITE_WITH_REMOTE)) {
                    // Identify playlists to remove
                    val playlistsToRemove = localPlaylists
                        .filterNot { it.playlist.isLocal }
                        .filterNot { it.playlist.browseId == null }
                        .filterNot { localPlaylist -> remotePlaylists.any { it.id == localPlaylist.playlist.browseId } }

                    // Remove playlists from the database
                    runBlocking {
                        playlistsToRemove.forEach { playlist ->
                            launch(Dispatchers.IO) {
                                database.update(playlist.playlist.localToggleLike())
                            }
                        }
                    }
                }

                // Add or update playlists in the database
                runBlocking {
                    remotePlaylists.forEach { remotePlaylist ->
                        launch(Dispatchers.IO) {
                            // forcefully assign isEditable. These playlists are at mercy of YouTube
                            var localPlaylist =
                                localPlaylists.find { remotePlaylist.id == it.playlist.browseId }?.playlist
                                    ?.copy(isEditable = remotePlaylist.isEditable)
                            if (localPlaylist == null) {
                                localPlaylist = PlaylistEntity(
                                    name = remotePlaylist.title,
                                    browseId = remotePlaylist.id,
                                    isEditable = remotePlaylist.isEditable,
                                    bookmarkedAt = LocalDateTime.now(),
                                    thumbnailUrl = remotePlaylist.thumbnail,
                                    remoteSongCount = remotePlaylist.songCountText?.let {
                                        Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                    },
                                    playEndpointParams = remotePlaylist.playEndpoint?.params,
                                    shuffleEndpointParams = remotePlaylist.shuffleEndpoint?.params,
                                    radioEndpointParams = remotePlaylist.radioEndpoint?.params
                                )
                                database.insert(localPlaylist)
                            } else {
                                database.update(localPlaylist, remotePlaylist)
                            }

                            // Fetch the playlist again after potential insertion/update
                            val updatedPlaylist =
                                database.playlistByBrowseId(remotePlaylist.id).firstOrNull()
                            updatedPlaylist?.let {
                                val playlistSongMaps = database.songMapsToPlaylist(updatedPlaylist.id)
                                if (updatedPlaylist.playlist.isEditable || playlistSongMaps.isNotEmpty()) {
                                    syncPlaylist(remotePlaylist.id, updatedPlaylist.id)
                                }
                            }
                        }
                    }
                }
                synced = true
            }
        } finally {
            _isSyncingRemotePlaylists.value = false
            if (synced) {
                context.dataStore.edit { settings ->
                    settings[LastPlaylistSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                }
            }
            if (SYNC_DEBUG) Log.i(TAG, "Library playlist synchronization ended")
        }
    }

    suspend fun syncPlaylist(browseId: String, playlistId: String) {
        // this is also used for individual playlist sync
        if (!context.isInternetConnected()) {
            return
        }
        YouTube.playlist(browseId).completed().onSuccess { playlistPage ->
            if (!context.isInternetConnected()) {
                return
            }

            runBlocking {
                launch(Dispatchers.IO) {
                    database.transaction {
                        clearPlaylist(playlistId)
                        val songEntities = playlistPage.songs
                            .map(SongItem::toMediaMetadata)
                            .onEach { insert(it) }

                        val playlistSongMaps = songEntities.mapIndexed { position, song ->
                            PlaylistSongMap(
                                songId = song.id,
                                playlistId = playlistId,
                                position = position,
                                setVideoId = song.setVideoId
                            )
                        }
                        playlistSongMaps.forEach { insert(it) }
                    }
                }
            }
        }
    }

    suspend fun syncRecentActivity(bypass: Boolean = false) {
        // REQUIRED: signed in, internet and category enabled
        if (!context.isUserLoggedIn() || !checkEnabled(SyncContent.RECENT_ACTIVITY) || !context.isInternetConnected()) {
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastRecentActivitySyncKey)) {
                return
            }
        }
        // REQUIRED: no ongoing sync. Claim the flag atomically so two starts cannot both pass.
        if (!_isSyncingRecentActivity.compareAndSet(expect = false, update = true)) {
            if (SYNC_DEBUG) Log.i(TAG, "Recent activity synchronization already in progress")
            return
        }
        var synced = false

        try {
            if (SYNC_DEBUG) Log.i(TAG, "Recent activity synchronization started")
            YouTube.libraryRecentActivity().onSuccess { page ->
                val recentActivity = page.items.drop(1)

                runBlocking {
                    launch(Dispatchers.IO) {
                        database.replaceRecentActivity(recentActivity)
                    }
                }
                synced = true
            }
        } finally {
            _isSyncingRecentActivity.value = false
            if (synced) {
                context.dataStore.edit { settings ->
                    settings[LastRecentActivitySyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                }
            }
            if (SYNC_DEBUG) Log.i(TAG, "Recent activity synchronization ended")
        }
    }

    /**
     * Returns null when either request failed, so the caller can tell an incomplete read apart
     * from a library that is genuinely empty.
     */
    private suspend inline fun <reified T> getRemoteData(libraryId: String, uploadsId: String): List<T>? {
        val browseIds = mapOf(
            libraryId to 0,
            uploadsId to 1
        )

        val remote = mutableListOf<T>()
        val fetched = runBlocking {
            browseIds.map { (browseId, tab) ->
                async {
                    YouTube.library(browseId, tab).completed().onSuccess { page ->
                        val data = page.items.filterIsInstance<T>().reversed()
                        synchronized(remote) { remote.addAll(data) }
                    }.isSuccess
                }
            }.awaitAll()
        }

        return if (fetched.all { it }) remote else null
    }
}
