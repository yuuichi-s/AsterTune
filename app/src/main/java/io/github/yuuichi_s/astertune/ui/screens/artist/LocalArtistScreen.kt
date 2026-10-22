package io.github.yuuichi_s.astertune.ui.screens.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.LocalDatabase
import io.github.yuuichi_s.astertune.LocalMenuState
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.LocalSnackbarHostState
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.ArtistSongSortDescendingKey
import io.github.yuuichi_s.astertune.constants.ArtistSongSortType
import io.github.yuuichi_s.astertune.constants.ArtistSongSortTypeKey
import io.github.yuuichi_s.astertune.constants.ListThumbnailSize
import io.github.yuuichi_s.astertune.constants.SwipeToQueueKey
import io.github.yuuichi_s.astertune.constants.ThumbnailCornerRadius
import io.github.yuuichi_s.astertune.constants.TopBarInsets
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.extensions.toMediaItem
import io.github.yuuichi_s.astertune.extensions.togglePlayPause
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.playback.queues.ListQueue
import io.github.yuuichi_s.astertune.ui.component.SortHeader
import io.github.yuuichi_s.astertune.ui.component.SwipeToQueueBox
import io.github.yuuichi_s.astertune.ui.component.items.ItemThumbnail
import io.github.yuuichi_s.astertune.ui.component.items.ListItem
import io.github.yuuichi_s.astertune.ui.dialog.ListDialog
import io.github.yuuichi_s.astertune.ui.menu.AlbumMenu
import io.github.yuuichi_s.astertune.ui.menu.SongMenu
import io.github.yuuichi_s.astertune.utils.joinByBullet
import io.github.yuuichi_s.astertune.utils.makeTimeString
import io.github.yuuichi_s.astertune.utils.rememberEnumPreference
import io.github.yuuichi_s.astertune.utils.rememberPreference
import io.github.yuuichi_s.astertune.viewmodels.LocalArtistAlbum
import io.github.yuuichi_s.astertune.viewmodels.LocalArtistViewModel
import kotlin.math.roundToInt

/**
 * Artist page for artists created from local media tags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalArtistViewModel = hiltViewModel(),
) {
    val density = LocalDensity.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val snackbarHostState = LocalSnackbarHostState.current

    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSongSortTypeKey, ArtistSongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSongSortDescendingKey, true)
    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)

    val artist by viewModel.artist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val latestYear by viewModel.latestYear.collectAsState()
    val totalSeconds by viewModel.totalSeconds.collectAsState()
    val albumThumbnails by viewModel.albumThumbnails.collectAsState()
    val newestAlbumThumbnail by viewModel.newestAlbumThumbnail.collectAsState()
    val headerThumbnail by viewModel.headerThumbnail.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val artistName = artist?.artist?.name

    var showImagePicker by rememberSaveable { mutableStateOf(false) }
    if (showImagePicker && albums.isNotEmpty()) {
        LocalArtistImagePicker(
            albums = albums,
            albumThumbnails = albumThumbnails,
            autoThumbnail = newestAlbumThumbnail,
            onSelect = { thumbnailUrl ->
                artist?.artist?.let { entity ->
                    database.query { update(entity.copy(thumbnailUrl = thumbnailUrl)) }
                }
                showImagePicker = false
            },
            onDismiss = { showImagePicker = false }
        )
    }

    var albumsExpanded by rememberSaveable { mutableStateOf(true) }

    var isSearchingAlbums by rememberSaveable { mutableStateOf(false) }
    var albumQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    if (isSearchingAlbums && albums.isNotEmpty()) {
        BackHandler {
            isSearchingAlbums = false
            albumQuery = TextFieldValue()
        }
    }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }

    val visibleSongs = remember(songs, query, isSearching) {
        if (!isSearching || query.text.isEmpty()) {
            songs
        } else {
            songs.filter { song ->
                song.song.title.contains(query.text, ignoreCase = true) ||
                        song.artists.any { it.name.contains(query.text, ignoreCase = true) }
            }
        }
    }

    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item(key = "header") {
                LocalArtistHeader(
                    name = artistName.orEmpty(),
                    year = latestYear,
                    genres = genres,
                    totalDuration = totalDurationText(totalSeconds),
                    thumbnailUrl = headerThumbnail,
                    onImageClick = if (albums.isNotEmpty()) {
                        { showImagePicker = true }
                    } else {
                        null
                    },
                    playEnabled = songs.isNotEmpty(),
                    onPlayAll = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = artistName,
                                items = songs.map { it.toMediaMetadata() },
                            )
                        )
                    },
                    onShuffle = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = artistName,
                                items = songs.map { it.toMediaMetadata() },
                                startShuffled = true,
                            )
                        )
                    },
                    onAddToQueueEnd = {
                        playerConnection.enqueueEnd(songs.map { it.toMediaItem() })
                    }
                )
            }

            if (albums.isNotEmpty()) {
                item(key = "albums") {
                    LocalArtistAlbums(
                        albums = albums,
                        albumThumbnails = albumThumbnails,
                        expanded = albumsExpanded,
                        onExpandedChange = { albumsExpanded = it },
                        isSearching = isSearchingAlbums,
                        query = albumQuery,
                        onQueryChange = { albumQuery = it },
                        onSearchStart = {
                            albumsExpanded = true
                            isSearchingAlbums = true
                        },
                        onSearchClose = {
                            isSearchingAlbums = false
                            albumQuery = TextFieldValue()
                        },
                        activeAlbumId = mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        navController = navController
                    )
                }
            }

            item(key = "songs_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp)
                ) {
                    if (isSearching) {
                        SearchFieldRow(
                            query = query,
                            onQueryChange = { query = it },
                            onClose = {
                                isSearching = false
                                query = TextFieldValue()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.heading_with_count, stringResource(R.string.songs), songs.size),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        SortHeader(
                            sortType = sortType,
                            sortDescending = sortDescending,
                            onSortTypeChange = onSortTypeChange,
                            onSortDescendingChange = onSortDescendingChange,
                            sortTypeText = { type ->
                                when (type) {
                                    ArtistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                    ArtistSongSortType.NAME -> R.string.sort_by_name
                                }
                            }
                        )
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search))
                        }
                    }
                }
            }

            val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()
            itemsIndexed(
                items = visibleSongs,
                key = { _, song -> song.id }
            ) { index, song ->
                SwipeToQueueBox(
                    item = song.toMediaItem(),
                    swipeEnabled = swipeEnabled,
                    snackbarHostState = snackbarHostState
                ) {
                    LocalArtistSongItem(
                        song = song,
                        thumbnailSize = thumbnailSize,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        navController = navController,
                        onPlay = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = artistName,
                                    items = visibleSongs.map { it.toMediaMetadata() },
                                    startIndex = index,
                                )
                            )
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
            },
            windowInsets = TopBarInsets,
            scrollBehavior = scrollBehavior
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun LocalArtistHeader(
    name: String,
    year: Int?,
    genres: List<String>,
    totalDuration: String,
    thumbnailUrl: String?,
    playEnabled: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueueEnd: () -> Unit,
    onImageClick: (() -> Unit)?,
) {
    val density = LocalDensity.current
    val imageSize = 160.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (thumbnailUrl != null) {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                preferredSize = (imageSize.value * density.density).roundToInt(),
                isActive = false,
                isPlaying = false,
                shape = CircleShape,
                modifier = Modifier
                    .size(imageSize)
                    .clip(CircleShape)
                    .then(if (onImageClick != null) Modifier.clickable(onClick = onImageClick) else Modifier)
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(imageSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(imageSize / 2)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val yearAndGenres = (listOfNotNull(year?.toString()) + genres).joinToString(", ")
            if (yearAndGenres.isNotEmpty()) {
                Text(
                    text = yearAndGenres,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = totalDuration,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.size(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(onClick = onPlayAll, enabled = playEnabled) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.play_all))
                }
                FilledTonalIconButton(onClick = onShuffle, enabled = playEnabled) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.shuffle))
                }
                FilledTonalIconButton(onClick = onAddToQueueEnd, enabled = playEnabled) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.add_to_queue))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalArtistAlbums(
    albums: List<LocalArtistAlbum>,
    albumThumbnails: Map<String, String?>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isSearching: Boolean,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearchStart: () -> Unit,
    onSearchClose: () -> Unit,
    activeAlbumId: String?,
    isPlaying: Boolean,
    navController: NavController,
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        if (isSearching) {
            SearchFieldRow(
                query = query,
                onQueryChange = onQueryChange,
                onClose = onSearchClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.heading_with_count, stringResource(R.string.albums), albums.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSearchStart) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search))
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null
                )
            }
        }

        val visibleAlbums = if (isSearching && query.text.isNotEmpty()) {
            albums.filter { it.album.album.title.contains(query.text, ignoreCase = true) }
        } else {
            albums
        }

        if (expanded) visibleAlbums.forEach { item ->
            val album = item.album
            ListItem(
                title = album.album.title,
                subtitle = joinByBullet(
                    pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
                    item.year?.toString()
                ),
                thumbnailContent = {
                    ItemThumbnail(
                        thumbnailUrl = albumThumbnails[album.id] ?: album.album.thumbnailUrl,
                        preferredSize = thumbnailSize,
                        isActive = album.id == activeAlbumId,
                        isPlaying = isPlaying,
                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                        modifier = Modifier.size(ListThumbnailSize)
                    )
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            database.query { update(album.album.toggleLike()) }
                        }
                    ) {
                        Icon(
                            painter = painterResource(if (album.album.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                            tint = if (album.album.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.combinedClickable(
                    onClick = { navController.navigate("album/${album.id}") },
                    onLongClick = {
                        menuState.show {
                            AlbumMenu(
                                originalAlbum = album,
                                navController = navController,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalArtistSongItem(
    song: Song,
    thumbnailSize: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    navController: NavController,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val showMenu = {
        menuState.show {
            SongMenu(
                originalSong = song,
                navController = navController,
                onDismiss = menuState::dismiss
            )
        }
    }

    ListItem(
        title = song.song.title,
        subtitle = {
            Text(
                text = joinByBullet(
                    song.album?.title ?: song.song.albumName,
                    makeTimeString(song.song.duration * 1000L)
                ),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = song.song.thumbnailUrl,
                preferredSize = thumbnailSize,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                modifier = Modifier.size(ListThumbnailSize)
            )
        },
        trailingContent = {
            IconButton(
                onClick = {
                    val updated = if (song.song.isLocal) song.song.localToggleLike() else song.song.toggleLike()
                    database.query { update(updated) }
                }
            ) {
                Icon(
                    painter = painterResource(if (song.song.liked) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (song.song.liked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null
                )
            }
            IconButton(onClick = showMenu) {
                Icon(Icons.Rounded.MoreVert, contentDescription = null)
            }
        },
        isActive = isActive,
        modifier = modifier.combinedClickable(
            onClick = {
                if (isActive) playerConnection.player.togglePlayPause() else onPlay()
            },
            onLongClick = showMenu
        )
    )
}

@Composable
private fun totalDurationText(totalSeconds: Int): String {
    val (hours, minutes) = hoursAndMinutes(totalSeconds)
    return if (hours > 0) {
        stringResource(R.string.duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

/**
 * Dialog for choosing an album cover as the artist image.
 *
 * Uses [albumThumbnails], falling back to the album's own artwork.
 * Calls [onSelect] with the chosen URL, or null for automatic selection.
 */
@Composable
private fun LocalArtistImagePicker(
    albums: List<LocalArtistAlbum>,
    albumThumbnails: Map<String, String?>,
    autoThumbnail: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()

    ListDialog(onDismiss = onDismiss) {
        item(key = "auto") {
            ListItem(
                title = stringResource(R.string.artist_image_auto),
                subtitle = null as String?,
                thumbnailContent = {
                    ItemThumbnail(
                        thumbnailUrl = autoThumbnail,
                        preferredSize = thumbnailSize,
                        isActive = false,
                        isPlaying = false,
                        shape = CircleShape,
                        modifier = Modifier.size(ListThumbnailSize)
                    )
                },
                modifier = Modifier
                    .fillParentMaxWidth()
                    .clickable { onSelect(null) }
            )
        }
        items(
            items = albums,
            key = { it.album.id }
        ) { item ->
            val thumbnailUrl = albumThumbnails[item.album.id] ?: item.album.album.thumbnailUrl
            ListItem(
                title = item.album.album.title,
                subtitle = item.year?.toString(),
                thumbnailContent = {
                    ItemThumbnail(
                        thumbnailUrl = thumbnailUrl,
                        preferredSize = thumbnailSize,
                        isActive = false,
                        isPlaying = false,
                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                        modifier = Modifier.size(ListThumbnailSize)
                    )
                },
                modifier = Modifier
                    .fillParentMaxWidth()
                    .clickable { onSelect(thumbnailUrl) }
            )
        }
    }
}

/**
 * Search field with a close button. Takes focus once when it appears, not again when a list item
 * holding it scrolls back into view.
 */
@Composable
private fun SearchFieldRow(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var focusRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusRequested) {
            focusRequester.requestFocus()
            focusRequested = true
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = null)
        }
    }
}

/** Splits [totalSeconds] into whole hours and the remaining whole minutes. */
internal fun hoursAndMinutes(totalSeconds: Int): Pair<Int, Int> =
    totalSeconds / 3600 to totalSeconds % 3600 / 60
