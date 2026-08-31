package io.github.yuuichi_s.astertune.ui.screens.library

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.LocalMenuState
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.LocalSnackbarHostState
import io.github.yuuichi_s.astertune.MainActivity
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.AlbumSortType
import io.github.yuuichi_s.astertune.constants.ArtistSortType
import io.github.yuuichi_s.astertune.constants.CONTENT_TYPE_ALBUM
import io.github.yuuichi_s.astertune.constants.CONTENT_TYPE_ARTIST
import io.github.yuuichi_s.astertune.constants.CONTENT_TYPE_HEADER
import io.github.yuuichi_s.astertune.constants.CONTENT_TYPE_PLAYLIST
import io.github.yuuichi_s.astertune.constants.CONTENT_TYPE_SONG
import io.github.yuuichi_s.astertune.constants.FolderSongSortType
import io.github.yuuichi_s.astertune.constants.GridThumbnailHeight
import io.github.yuuichi_s.astertune.constants.LibraryViewType
import io.github.yuuichi_s.astertune.constants.ListThumbnailSize
import io.github.yuuichi_s.astertune.constants.LocalAlbumSortDescendingKey
import io.github.yuuichi_s.astertune.constants.LocalAlbumSortTypeKey
import io.github.yuuichi_s.astertune.constants.LocalArtistSortDescendingKey
import io.github.yuuichi_s.astertune.constants.LocalArtistSortTypeKey
import io.github.yuuichi_s.astertune.constants.LocalPlaylistSortDescendingKey
import io.github.yuuichi_s.astertune.constants.LocalPlaylistSortTypeKey
import io.github.yuuichi_s.astertune.constants.PlaylistSortType
import io.github.yuuichi_s.astertune.constants.LocalFilter
import io.github.yuuichi_s.astertune.constants.LocalFilterKey
import io.github.yuuichi_s.astertune.constants.LocalLibraryEnableKey
import io.github.yuuichi_s.astertune.constants.LocalSongSortDescendingKey
import io.github.yuuichi_s.astertune.constants.LocalSongSortTypeKey
import io.github.yuuichi_s.astertune.constants.LocalViewTypeKey
import io.github.yuuichi_s.astertune.constants.SwipeToQueueKey
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.playback.queues.ListQueue
import io.github.yuuichi_s.astertune.ui.component.ChipsRow
import io.github.yuuichi_s.astertune.ui.component.EmptyPlaceholder
import io.github.yuuichi_s.astertune.ui.component.LazyColumnScrollbar
import io.github.yuuichi_s.astertune.ui.component.LazyVerticalGridScrollbar
import io.github.yuuichi_s.astertune.ui.component.LibraryAlbumGridItem
import io.github.yuuichi_s.astertune.ui.component.LibraryAlbumListItem
import io.github.yuuichi_s.astertune.ui.component.LibraryArtistGridItem
import io.github.yuuichi_s.astertune.ui.component.LibraryArtistListItem
import io.github.yuuichi_s.astertune.ui.component.LibraryPlaylistGridItem
import io.github.yuuichi_s.astertune.ui.component.LibraryPlaylistListItem
import io.github.yuuichi_s.astertune.ui.component.ScrollToTopManager
import io.github.yuuichi_s.astertune.ui.component.SortHeader
import io.github.yuuichi_s.astertune.ui.component.button.IconButton
import io.github.yuuichi_s.astertune.ui.component.items.SongGridItem
import io.github.yuuichi_s.astertune.ui.component.items.SongListItem
import io.github.yuuichi_s.astertune.ui.utils.MEDIA_PERMISSION_LEVEL
import io.github.yuuichi_s.astertune.utils.rememberEnumPreference
import io.github.yuuichi_s.astertune.utils.rememberPreference
import io.github.yuuichi_s.astertune.viewmodels.LocalLibraryViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun LocalScreen(
    navController: NavController,
    viewModel: LocalLibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val density = LocalDensity.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val snackbarHostState = LocalSnackbarHostState.current

    var filter by rememberEnumPreference(LocalFilterKey, LocalFilter.SONGS)
    var viewType by rememberEnumPreference(LocalViewTypeKey, LibraryViewType.GRID)
    val (songSortType, onSongSortTypeChange) = rememberEnumPreference(LocalSongSortTypeKey, FolderSongSortType.TRACK_NUMBER)
    val (songSortDescending, onSongSortDescendingChange) = rememberPreference(LocalSongSortDescendingKey, false)
    val (albumSortType, onAlbumSortTypeChange) = rememberEnumPreference(LocalAlbumSortTypeKey, AlbumSortType.CREATE_DATE)
    val (albumSortDescending, onAlbumSortDescendingChange) = rememberPreference(LocalAlbumSortDescendingKey, true)
    val (artistSortType, onArtistSortTypeChange) = rememberEnumPreference(LocalArtistSortTypeKey, ArtistSortType.CREATE_DATE)
    val (artistSortDescending, onArtistSortDescendingChange) = rememberPreference(LocalArtistSortDescendingKey, true)
    val (playlistSortType, onPlaylistSortTypeChange) = rememberEnumPreference(LocalPlaylistSortTypeKey, PlaylistSortType.CREATE_DATE)
    val (playlistSortDescending, onPlaylistSortDescendingChange) = rememberPreference(LocalPlaylistSortDescendingKey, true)
    val localLibEnable by rememberPreference(LocalLibraryEnableKey, defaultValue = true)
    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)

    val songs by viewModel.localSongs.collectAsState()
    val albums by viewModel.localAlbums.collectAsState()
    val artists by viewModel.localArtists.collectAsState()
    val playlists by viewModel.localPlaylists.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    // search
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val filteredSongs = viewModel.filteredSongs
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(query) {
        snapshotFlow { query }.debounce(300L).collectLatest {
            viewModel.search(it.text)
        }
    }
    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }

    val filterContent = @Composable {
        var showStoragePerm by remember {
            mutableStateOf(context.checkSelfPermission(MEDIA_PERMISSION_LEVEL) != PackageManager.PERMISSION_GRANTED)
        }
        Column {
            if (localLibEnable && showStoragePerm) {
                TextButton(
                    onClick = {
                        showStoragePerm = false
                        (context as MainActivity).permissionLauncher.launch(MEDIA_PERMISSION_LEVEL)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = stringResource(R.string.missing_media_permission_warning),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                } else {
                    ChipsRow(
                        chips = listOf(
                            LocalFilter.SONGS to stringResource(R.string.songs),
                            LocalFilter.ALBUMS to stringResource(R.string.albums),
                            LocalFilter.ARTISTS to stringResource(R.string.artists),
                            LocalFilter.PLAYLISTS to stringResource(R.string.playlists),
                        ),
                        currentValue = filter,
                        onValueUpdate = { filter = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else {
                            isSearching = true
                        }
                    },
                    modifier = Modifier.padding(end = if (isSearching) 6.dp else 0.dp)
                ) {
                    Icon(
                        imageVector = if (isSearching) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = null
                    )
                }

                if (!isSearching) {
                    IconButton(
                        onClick = {
                            viewType = viewType.toggle()
                        },
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Icon(
                            imageVector = when (viewType) {
                                LibraryViewType.LIST -> Icons.AutoMirrored.Rounded.List
                                LibraryViewType.GRID -> Icons.Rounded.GridView
                            },
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }

    val songHeaderContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SortHeader(
                sortType = songSortType,
                sortDescending = songSortDescending,
                onSortTypeChange = onSongSortTypeChange,
                onSortDescendingChange = onSongSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        FolderSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                        FolderSongSortType.MODIFIED_DATE -> R.string.sort_by_date_modified
                        FolderSongSortType.RELEASE_DATE -> R.string.sort_by_date_released
                        FolderSongSortType.NAME -> R.string.sort_by_name
                        FolderSongSortType.ARTIST -> R.string.sort_by_artist
                        FolderSongSortType.PLAY_COUNT -> R.string.sort_by_play_count
                        FolderSongSortType.TRACK_NUMBER -> R.string.sort_by_track_number
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            songs?.let { songs ->
                Text(
                    text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    val albumHeaderContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SortHeader(
                sortType = albumSortType,
                sortDescending = albumSortDescending,
                onSortTypeChange = onAlbumSortTypeChange,
                onSortDescendingChange = onAlbumSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        AlbumSortType.CREATE_DATE -> R.string.sort_by_create_date
                        AlbumSortType.NAME -> R.string.sort_by_name
                        AlbumSortType.ARTIST -> R.string.sort_by_artist
                        AlbumSortType.YEAR -> R.string.sort_by_year
                        AlbumSortType.SONG_COUNT -> R.string.sort_by_song_count
                        AlbumSortType.LENGTH -> R.string.sort_by_length
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            albums?.let { albums ->
                Text(
                    text = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    val artistHeaderContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SortHeader(
                sortType = artistSortType,
                sortDescending = artistSortDescending,
                onSortTypeChange = onArtistSortTypeChange,
                onSortDescendingChange = onArtistSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        ArtistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        ArtistSortType.NAME -> R.string.sort_by_name
                        ArtistSortType.SONG_COUNT -> R.string.sort_by_song_count
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            artists?.let { artists ->
                Text(
                    text = pluralStringResource(R.plurals.n_artist, artists.size, artists.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    val playlistHeaderContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SortHeader(
                sortType = playlistSortType,
                sortDescending = playlistSortDescending,
                onSortTypeChange = onPlaylistSortTypeChange,
                onSortDescendingChange = onPlaylistSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        PlaylistSortType.NAME -> R.string.sort_by_name
                        PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            playlists?.let { playlists ->
                Text(
                    text = pluralStringResource(R.plurals.n_playlist, playlists.size, playlists.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        ScrollToTopManager(navController, lazyListState)
        if (isSearching) {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
            ) {
                item(
                    key = "filter",
                    contentType = CONTENT_TYPE_HEADER
                ) {
                    Column(
                        modifier = Modifier.background(MaterialTheme.colorScheme.background)
                    ) {
                        filterContent()
                    }
                }

                val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()
                itemsIndexed(
                    items = filteredSongs,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> CONTENT_TYPE_SONG }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                        isActive = song.song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        inSelectMode = false,
                        isSelected = false,
                        onSelectedChange = {},
                        swipeEnabled = swipeEnabled,
                        thumbnailSize = thumbnailSize,
                        onPlay = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = resources.getString(R.string.local_files),
                                    items = filteredSongs.map { it.toMediaMetadata() },
                                    startIndex = index
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    )
                }
            }
            LazyColumnScrollbar(
                state = lazyListState,
            )
        } else when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        Column(
                            modifier = Modifier.background(MaterialTheme.colorScheme.background)
                        ) {
                            filterContent()
                        }
                    }

                    when (filter) {
                        LocalFilter.SONGS -> {
                            item(
                                key = "header",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                songHeaderContent()
                            }

                            songs?.let { songs ->
                                if (songs.isEmpty()) {
                                    item {
                                        EmptyPlaceholder(
                                            icon = Icons.Rounded.MusicNote,
                                            text = stringResource(R.string.library_song_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                                val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()
                                itemsIndexed(
                                    items = songs,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> CONTENT_TYPE_SONG }
                                ) { index, song ->
                                    SongListItem(
                                        song = song,
                                        navController = navController,
                                        snackbarHostState = snackbarHostState,
                                        isActive = song.song.id == mediaMetadata?.id,
                                        isPlaying = isPlaying,
                                        inSelectMode = false,
                                        isSelected = false,
                                        onSelectedChange = {},
                                        swipeEnabled = swipeEnabled,
                                        thumbnailSize = thumbnailSize,
                                        onPlay = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = resources.getString(R.string.local_files),
                                                    items = songs.map { it.toMediaMetadata() },
                                                    startIndex = index
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem()
                                    )
                                }
                            }
                        }

                        LocalFilter.ALBUMS -> {
                            item(
                                key = "header",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                albumHeaderContent()
                            }

                            albums?.let { albums ->
                                if (albums.isEmpty()) {
                                    item {
                                        EmptyPlaceholder(
                                            icon = Icons.Rounded.Album,
                                            text = stringResource(R.string.library_album_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = albums,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> CONTENT_TYPE_ALBUM }
                                ) { _, album ->
                                    LibraryAlbumListItem(
                                        navController = navController,
                                        menuState = menuState,
                                        album = album,
                                        isActive = album.id == mediaMetadata?.album?.id,
                                        isPlaying = isPlaying,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }

                        LocalFilter.ARTISTS -> {
                            item(
                                key = "header",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                artistHeaderContent()
                            }

                            artists?.let { artists ->
                                if (artists.isEmpty()) {
                                    item {
                                        EmptyPlaceholder(
                                            icon = Icons.Rounded.Person,
                                            text = stringResource(R.string.library_artist_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = artists,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> CONTENT_TYPE_ARTIST }
                                ) { _, artist ->
                                    LibraryArtistListItem(
                                        navController = navController,
                                        menuState = menuState,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier.animateItem(),
                                        artist = artist
                                    )
                                }
                            }
                        }

                        LocalFilter.PLAYLISTS -> {
                            item(
                                key = "header",
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                playlistHeaderContent()
                            }

                            playlists?.let { playlists ->
                                if (playlists.isEmpty()) {
                                    item {
                                        EmptyPlaceholder(
                                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                            text = stringResource(R.string.library_playlist_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = playlists,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> CONTENT_TYPE_PLAYLIST }
                                ) { _, playlist ->
                                    LibraryPlaylistListItem(
                                        navController = navController,
                                        menuState = menuState,
                                        coroutineScope = coroutineScope,
                                        playlist = playlist,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
                LazyColumnScrollbar(
                    state = lazyListState,
                )
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        Column(
                            modifier = Modifier.background(MaterialTheme.colorScheme.background)
                        ) {
                            filterContent()
                        }
                    }

                    when (filter) {
                        LocalFilter.SONGS -> {
                            item(
                                key = "header",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                songHeaderContent()
                            }

                            songs?.let { songs ->
                                if (songs.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyPlaceholder(
                                            icon = Icons.Rounded.MusicNote,
                                            text = stringResource(R.string.library_song_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = songs,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> CONTENT_TYPE_SONG }
                                ) { index, song ->
                                    SongGridItem(
                                        song = song,
                                        isActive = song.song.id == mediaMetadata?.id,
                                        isPlaying = isPlaying,
                                        fillMaxWidth = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = resources.getString(R.string.local_files),
                                                        items = songs.map { it.toMediaMetadata() },
                                                        startIndex = index
                                                    )
                                                )
                                            }
                                            .animateItem()
                                    )
                                }
                            }
                        }

                        LocalFilter.ALBUMS -> {
                            item(
                                key = "header",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                albumHeaderContent()
                            }

                            albums?.let { albums ->
                                if (albums.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyPlaceholder(
                                            icon = Icons.Rounded.Album,
                                            text = stringResource(R.string.library_album_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = albums,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> CONTENT_TYPE_ALBUM }
                                ) { _, album ->
                                    LibraryAlbumGridItem(
                                        navController = navController,
                                        menuState = menuState,
                                        coroutineScope = coroutineScope,
                                        album = album,
                                        isActive = album.id == mediaMetadata?.album?.id,
                                        isPlaying = isPlaying,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }

                        LocalFilter.ARTISTS -> {
                            item(
                                key = "header",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                artistHeaderContent()
                            }

                            artists?.let { artists ->
                                if (artists.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyPlaceholder(
                                            icon = Icons.Rounded.Person,
                                            text = stringResource(R.string.library_artist_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = artists,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> CONTENT_TYPE_ARTIST }
                                ) { _, artist ->
                                    LibraryArtistGridItem(
                                        navController = navController,
                                        menuState = menuState,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier.animateItem(),
                                        artist = artist
                                    )
                                }
                            }
                        }

                        LocalFilter.PLAYLISTS -> {
                            item(
                                key = "header",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = CONTENT_TYPE_HEADER
                            ) {
                                playlistHeaderContent()
                            }

                            playlists?.let { playlists ->
                                if (playlists.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyPlaceholder(
                                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                            text = stringResource(R.string.library_playlist_empty),
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = playlists,
                                    key = { _, item -> item.id },
                                    contentType = { _, _ -> CONTENT_TYPE_PLAYLIST }
                                ) { _, playlist ->
                                    LibraryPlaylistGridItem(
                                        navController = navController,
                                        menuState = menuState,
                                        coroutineScope = coroutineScope,
                                        playlist = playlist,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
                LazyVerticalGridScrollbar(
                    state = lazyGridState,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}
