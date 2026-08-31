package io.github.yuuichi_s.astertune.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.LocalSnackbarHostState
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.CONTENT_TYPE_LIST
import io.github.yuuichi_s.astertune.constants.ListItemHeight
import io.github.yuuichi_s.astertune.constants.ListThumbnailSize
import io.github.yuuichi_s.astertune.constants.SwipeToQueueKey
import io.github.yuuichi_s.astertune.db.entities.Album
import io.github.yuuichi_s.astertune.db.entities.Artist
import io.github.yuuichi_s.astertune.db.entities.Playlist
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.playback.queues.ListQueue
import io.github.yuuichi_s.astertune.ui.component.ChipsRow
import io.github.yuuichi_s.astertune.ui.component.EmptyPlaceholder
import io.github.yuuichi_s.astertune.ui.component.LazyColumnScrollbar
import io.github.yuuichi_s.astertune.ui.component.items.AlbumListItem
import io.github.yuuichi_s.astertune.ui.component.items.ArtistListItem
import io.github.yuuichi_s.astertune.ui.component.items.PlaylistListItem
import io.github.yuuichi_s.astertune.ui.component.items.SongListItem
import io.github.yuuichi_s.astertune.utils.rememberPreference
import io.github.yuuichi_s.astertune.viewmodels.LocalFilter
import io.github.yuuichi_s.astertune.viewmodels.LocalSearchViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

@OptIn(FlowPreview::class)
@Composable
fun LocalSearchScreen(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    viewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val resources = LocalResources.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val result by viewModel.result.collectAsState()

    val lazyListState = rememberLazyListState()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        snapshotFlow { query }.debounce { 300L }.collectLatest {
            viewModel.query.value = query
        }
    }

    Column(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        ChipsRow(
            chips = listOf(
                LocalFilter.ALL to stringResource(R.string.filter_all),
                LocalFilter.SONG to stringResource(R.string.filter_songs),
                LocalFilter.ALBUM to stringResource(R.string.filter_albums),
                LocalFilter.ARTIST to stringResource(R.string.filter_artists),
                LocalFilter.PLAYLIST to stringResource(R.string.filter_playlists)
            ),
            currentValue = searchFilter,
            onValueUpdate = { viewModel.filter.value = it }
        )

        LazyColumn(
            state = lazyListState,
//            contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start).asPaddingValues(),
            modifier = Modifier.weight(1f)
        ) {
            result.map.forEach { (filter, items) ->
                if (result.filter == LocalFilter.ALL) {
                    item(
                        key = filter
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ListItemHeight)
                                .clickable { viewModel.filter.value = filter }
                                .padding(start = 12.dp, end = 18.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    when (filter) {
                                        LocalFilter.SONG -> R.string.filter_songs
                                        LocalFilter.ALBUM -> R.string.filter_albums
                                        LocalFilter.ARTIST -> R.string.filter_artists
                                        LocalFilter.PLAYLIST -> R.string.filter_playlists
                                        LocalFilter.ALL -> error("")
                                    }
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )

                            Icon(
                                Icons.AutoMirrored.Rounded.NavigateNext,
                                contentDescription = null
                            )
                        }
                    }
                }

                val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()
                items(
                    items = items,
                    key = { it.id },
                    contentType = { CONTENT_TYPE_LIST }
                ) { item ->
                    when (item) {
                        is Song -> {
                            SongListItem(
                                song = item,
                                navController = navController,
                                snackbarHostState = snackbarHostState,

                                isActive = item.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                inSelectMode = false,
                                isSelected = false,
                                onSelectedChange = { },
                                swipeEnabled = swipeEnabled,

                                thumbnailSize = thumbnailSize,
                                onPlay = {
                                    val songs = result.map
                                        .getOrDefault(LocalFilter.SONG, emptyList())
                                        .filterIsInstance<Song>()
                                        .map { it.toMediaMetadata() }
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "${resources.getString(R.string.queue_searched_songs_ot)} $query",
                                            items = songs,
                                            startIndex = songs.indexOfFirst { it.id == item.id }
                                        ))
                                },
                                modifier = Modifier.animateItem()
                            )
                        }

                        is Album -> AlbumListItem(
                            album = item,
                            isActive = item.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    navController.navigate("album/${item.id}")
                                }
                                .animateItem()
                        )

                        is Artist -> ArtistListItem(
                            artist = item,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    navController.navigate("artist/${item.id}")
                                }
                                .animateItem()
                        )

                        is Playlist -> PlaylistListItem(
                            playlist = item,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    navController.navigate("local_playlist/${item.id}")
                                }
                                .animateItem()
                        )
                    }
                }
            }

            if (result.query.isNotEmpty() && result.map.isEmpty()) {
                item(
                    key = "no_result"
                ) {
                    EmptyPlaceholder(
                        icon = Icons.Rounded.Search,
                        text = stringResource(R.string.no_results_found),
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
    LazyColumnScrollbar(
        state = lazyListState,
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}