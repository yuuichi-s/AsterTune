package io.github.yuuichi_s.astertune.ui.component.items

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.offline.Download
import io.github.yuuichi_s.astertune.LocalDatabase
import io.github.yuuichi_s.astertune.LocalDownloadUtil
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.constants.ListThumbnailSize
import io.github.yuuichi_s.astertune.constants.ThumbnailCornerRadius
import io.github.yuuichi_s.astertune.db.entities.Album
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.playback.queues.ListQueue
import io.github.yuuichi_s.astertune.ui.utils.getNSongsString
import io.github.yuuichi_s.astertune.utils.getDownloadState
import io.github.yuuichi_s.astertune.utils.joinByBullet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableIntStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            val songs = songs.filterNot { it.song.isLocal }
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState = getDownloadState(songs.map { downloads[it.id] })
            }
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }

        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        album.takeIf { it.album.songCount != 0 }?.let { album ->
            getNSongsString(album.album.songCount, album.downloadCount)
        },
        album.album.year?.toString()
    ),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            placeholderIcon = Icons.Outlined.Album,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.size(ListThumbnailSize)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableIntStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            val songs = songs.filterNot { it.song.isLocal }
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState = getDownloadState(songs.map { downloads[it.id] })
            }
        }

        if (album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }

        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = album.album.title,
    subtitle = album.artists.joinToString { it.name },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem

        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            placeholderIcon = Icons.Outlined.Album,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(ThumbnailCornerRadius),
        )

        AlbumPlayButton(
            visible = !isActive,
            onClick = {
                coroutineScope.launch {
                    database.albumWithSongs(album.id).first()?.songs
                        ?.map { it.toMediaMetadata() }
                        ?.let {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = album.album.title,
                                    items = it
                                )
                            )
                        }
                }
            }
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)