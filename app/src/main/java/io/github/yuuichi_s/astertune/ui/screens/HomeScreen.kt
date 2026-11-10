package io.github.yuuichi_s.astertune.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.utils.parseCookieString
import io.github.yuuichi_s.astertune.LocalDatabase
import io.github.yuuichi_s.astertune.LocalMenuState
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.GridThumbnailHeight
import io.github.yuuichi_s.astertune.constants.HomeAccountPlaylistsKey
import io.github.yuuichi_s.astertune.constants.HomeChipsKey
import io.github.yuuichi_s.astertune.constants.HomeForgottenFavKey
import io.github.yuuichi_s.astertune.constants.HomeKeepListeningKey
import io.github.yuuichi_s.astertune.constants.HomeMoodKey
import io.github.yuuichi_s.astertune.constants.HomeQuickPicksKey
import io.github.yuuichi_s.astertune.constants.HomeSimilarToKey
import io.github.yuuichi_s.astertune.constants.HomeYtmSectionsKey
import io.github.yuuichi_s.astertune.constants.InnerTubeCookieKey
import io.github.yuuichi_s.astertune.constants.ListItemHeight
import io.github.yuuichi_s.astertune.constants.ListThumbnailSize
import io.github.yuuichi_s.astertune.constants.ThumbnailCornerRadius
import io.github.yuuichi_s.astertune.db.entities.Album
import io.github.yuuichi_s.astertune.db.entities.Artist
import io.github.yuuichi_s.astertune.db.entities.LocalItem
import io.github.yuuichi_s.astertune.db.entities.Playlist
import io.github.yuuichi_s.astertune.db.entities.RecentActivityEntity
import io.github.yuuichi_s.astertune.db.entities.RecentActivityType
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.extensions.togglePlayPauseOrReplay
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.playback.queues.ListQueue
import io.github.yuuichi_s.astertune.playback.queues.YouTubeAlbumRadio
import io.github.yuuichi_s.astertune.playback.queues.YouTubeQueue
import io.github.yuuichi_s.astertune.ui.component.ChipsRow
import io.github.yuuichi_s.astertune.ui.component.HideOnScrollFAB
import io.github.yuuichi_s.astertune.ui.component.NavigationTitle
import io.github.yuuichi_s.astertune.ui.component.ScrollToTopManager
import io.github.yuuichi_s.astertune.ui.component.items.AlbumGridItem
import io.github.yuuichi_s.astertune.ui.component.items.ArtistGridItem
import io.github.yuuichi_s.astertune.ui.component.items.RecentActivityTile
import io.github.yuuichi_s.astertune.ui.component.items.SongGridItem
import io.github.yuuichi_s.astertune.ui.component.items.SongListItem
import io.github.yuuichi_s.astertune.ui.component.items.YouTubeGridItem
import io.github.yuuichi_s.astertune.ui.component.items.YouTubeListItem
import io.github.yuuichi_s.astertune.ui.component.shimmer.GridItemPlaceHolder
import io.github.yuuichi_s.astertune.ui.component.shimmer.ShimmerHost
import io.github.yuuichi_s.astertune.ui.component.shimmer.TextPlaceholder
import io.github.yuuichi_s.astertune.ui.menu.AlbumMenu
import io.github.yuuichi_s.astertune.ui.menu.ArtistMenu
import io.github.yuuichi_s.astertune.ui.menu.SongMenu
import io.github.yuuichi_s.astertune.ui.menu.YouTubeAlbumMenu
import io.github.yuuichi_s.astertune.ui.menu.YouTubeArtistMenu
import io.github.yuuichi_s.astertune.ui.menu.YouTubePlaylistMenu
import io.github.yuuichi_s.astertune.ui.menu.YouTubeSongMenu
import io.github.yuuichi_s.astertune.ui.utils.SnapLayoutInfoProvider
import io.github.yuuichi_s.astertune.utils.rememberPreference
import io.github.yuuichi_s.astertune.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val density = LocalDensity.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queuePlaylistId by playerConnection.queuePlaylistId.collectAsState()

    val quickPicks by viewModel.quickPicks.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val recentActivity by viewModel.recentActivity.collectAsState()

    val selectedChip by viewModel.selectedChip.collectAsState()

    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val showQuickPicks by rememberPreference(HomeQuickPicksKey, true)
    val showForgottenFav by rememberPreference(HomeForgottenFavKey, true)
    val showKeepListening by rememberPreference(HomeKeepListeningKey, true)
    val showAccountPlaylists by rememberPreference(HomeAccountPlaylistsKey, true)
    val showSimilarTo by rememberPreference(HomeSimilarToKey, true)
    val showChips by rememberPreference(HomeChipsKey, true)
    val showYtmSections by rememberPreference(HomeYtmSectionsKey, true)
    val showMood by rememberPreference(HomeMoodKey, true)

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()

    LaunchedEffect(showChips) {
        if (!showChips) viewModel.clearChipSelection()
    }

    LaunchedEffect(showYtmSections) {
        if (!showYtmSections){
            return@LaunchedEffect
        }
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                    viewModel.loadMoreYouTubeItems(homePage?.continuation)
                }
            }
    }

    if (selectedChip != null) {
        BackHandler {
            // if a chip is selected, go back to the normal homepage first
            viewModel.toggleChip(selectedChip)
        }
    }


    val localGridItem: @Composable (LocalItem, String) -> Unit = { it, source ->
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (it.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPauseOrReplay()
                            } else {
                                val song = it.toMediaMetadata()
                                if (song.isLocal) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = source,
                                            items = listOf(song)
                                        )
                                    )
                                } else {
                                    playerConnection.playQueue(YouTubeQueue.radio(song), isRadio = true)
                                }
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                SongMenu(
                                    originalSong = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                isActive = it.id == mediaMetadata?.id,
                isPlaying = isPlaying,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            navController.navigate("album/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    )
            )

            is Artist -> ArtistGridItem(
                artist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            navController.navigate("artist/${it.id}")
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )

            is Playlist -> {}
        }
    }

    // Shared by the YouTube song cards and list rows so that both react to a tap the same way.
    val playYouTubeSong: (SongItem) -> Unit = { song ->
        if (song.id == mediaMetadata?.id) {
            playerConnection.player.togglePlayPauseOrReplay()
        } else {
            playerConnection.playQueue(
                YouTubeQueue(
                    song.endpoint ?: WatchEndpoint(videoId = song.id),
                    song.toMediaMetadata()
                ),
                isRadio = true,
            )
        }
    }

    val ytGridItem: @Composable (YTItem) -> Unit = { item ->
        YouTubeGridItem(
            item = item,
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> playYouTubeSong(item)

                            is AlbumItem -> navController.navigate("album/${item.id}")
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            when (item) {
                                is SongItem -> YouTubeSongMenu(
                                    song = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is AlbumItem -> YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )

                                is ArtistItem -> YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = menuState::dismiss
                                )

                                is PlaylistItem -> YouTubePlaylistMenu(
                                    navController = navController,
                                    playlist = item,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    }
                )
        )
    }

    val ytSongListItem: @Composable (SongItem, Modifier) -> Unit = { song, modifier ->
        val showMenu = {
            menuState.show {
                YouTubeSongMenu(
                    song = song,
                    navController = navController,
                    onDismiss = menuState::dismiss
                )
            }
        }
        YouTubeListItem(
            item = song,
            isActive = song.id == mediaMetadata?.id,
            isPlaying = isPlaying,
            trailingContent = {
                IconButton(onClick = showMenu) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = null
                    )
                }
            },
            modifier = modifier
                .combinedClickable(
                    onClick = { playYouTubeSong(song) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu()
                    }
                )
        )
    }

    LaunchedEffect(quickPicks) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    fun openRecentActivity(item: RecentActivityEntity) {
        when (item.type) {
            RecentActivityType.PLAYLIST -> {
                val entryAtTap = navController.currentBackStackEntry
                scope.launch {
                    val localPlaylistId = viewModel.localPlaylistIdToOpen(item.id)
                    // Skip when another tap or navigation has already moved away.
                    if (navController.currentBackStackEntry != entryAtTap) return@launch

                    if (localPlaylistId != null) {
                        navController.navigate("local_playlist/$localPlaylistId")
                    } else {
                        navController.navigate("online_playlist/${item.id}")
                    }
                }
            }

            RecentActivityType.ALBUM -> navController.navigate("album/${item.id}")

            RecentActivityType.ARTIST -> navController.navigate("artist/${item.id}")
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh(manual = true) }
            ),
        contentAlignment = Alignment.TopStart
    ) {
        val listThumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()

        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
        val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

        val listPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()

        val recentLayout = recentActivityLayout(maxWidth, listPadding)
        val recentPages = remember(recentActivity, recentLayout.columns) {
            recentActivity.orEmpty().chunked(RecentRows * recentLayout.columns)
        }
        // Keep pager state outside the list item to retain the current page while off screen.
        val recentPagerState = rememberPagerState { recentPages.size }
        // Use IDs to avoid resetting the page when sync only updates timestamps.
        val recentActivityIds = remember(recentActivity) { recentActivity?.map { it.id } }
        LaunchedEffect(recentActivityIds, recentLayout.columns) {
            recentPagerState.scrollToPage(0)
        }

        val quickPicksSnapLayoutInfoProvider =
            rememberGridItemSnapLayoutInfoProvider(quickPicksLazyGridState, horizontalLazyGridItemWidthFactor)
        val forgottenFavoritesSnapLayoutInfoProvider =
            rememberGridItemSnapLayoutInfoProvider(forgottenFavoritesLazyGridState, horizontalLazyGridItemWidthFactor)

        ScrollToTopManager(navController, lazylistState)
        LazyColumn(
            state = lazylistState,
            contentPadding = listPadding
        ) {
            if (showChips) {
                item(key = "chips") {
                    ChipsRow(
                        chips = homePage?.chips?.mapNotNull { it to it.title } ?: emptyList(),
                        currentValue = selectedChip,
                        onValueUpdate = {
                            viewModel.toggleChip(it)
                        }
                    )
                }
            }

            if (isLoggedIn && !recentActivity.isNullOrEmpty()) {
                item(key = "recent_activity_title") {
                    NavigationTitle(
                        title = stringResource(R.string.recent_activity),
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "recent_activity") {
                    RecentActivityPager(
                        pages = recentPages,
                        layout = recentLayout,
                        pagerState = recentPagerState,
                        queuePlaylistId = queuePlaylistId,
                        isPlaying = isPlaying,
                        onItemClick = ::openRecentActivity,
                        modifier = Modifier.animateItem()
                    )
                }
            }


            // Based on listening
            quickPicks?.takeIf { showQuickPicks && it.isNotEmpty() }?.let { quickPicks ->
                item(key = "quick_picks_title") {
                    NavigationTitle(
                        title = stringResource(R.string.quick_picks),
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "quick_picks") {
                    LazyHorizontalGrid(
                        state = quickPicksLazyGridState,
                        rows = GridCells.Fixed(4),
                        flingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider),
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * 4)
                            .animateItem()
                    ) {
                        items(
                            items = quickPicks,
                            key = { it.id }
                        ) { originalSong ->
                            SongListItem(
                                song = originalSong,
                                navController = navController,

                                isActive = originalSong.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                inSelectMode = null,
                                isSelected = false,
                                onSelectedChange = {},
                                swipeEnabled = false,

                                thumbnailSize = listThumbnailSize,
                                onPlay = {
                                    playerConnection.playQueue(
                                        YouTubeQueue.radio(originalSong.toMediaMetadata()),
                                        isRadio = true
                                    )
                                },
                                modifier = Modifier.width(horizontalLazyGridItemWidth)
                            )
                        }
                    }
                }
            }

            // Forgotten favorites
            forgottenFavorites?.takeIf { showForgottenFav && it.isNotEmpty() }?.let { forgottenFavorites ->
                item(key = "forgotten_favorites_title") {
                    NavigationTitle(
                        title = stringResource(R.string.forgotten_favorites),
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "forgotten_favorites") {
                    val queueTitle = stringResource(R.string.forgotten_favorites)
                    // take min in case list size is less than 4
                    val rows = min(4, forgottenFavorites.size)
                    LazyHorizontalGrid(
                        state = forgottenFavoritesLazyGridState,
                        rows = GridCells.Fixed(rows),
                        flingBehavior = rememberSnapFlingBehavior(forgottenFavoritesSnapLayoutInfoProvider),
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ListItemHeight * rows)
                            .animateItem()
                    ) {
                        itemsIndexed(
                            items = forgottenFavorites,
                            key = { _, song -> song.id }
                        ) { index, originalSong ->
                            SongListItem(
                                song = originalSong,
                                navController = navController,

                                isActive = originalSong.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                inSelectMode = null,
                                isSelected = false,
                                onSelectedChange = {},
                                swipeEnabled = false,

                                thumbnailSize = listThumbnailSize,
                                onPlay = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = queueTitle,
                                            items = forgottenFavorites.map { it.toMediaMetadata() },
                                            startIndex = index
                                        )
                                    )
                                },
                                modifier = Modifier.width(horizontalLazyGridItemWidth)
                            )
                        }
                    }
                }
            }

            // Keep listening
            keepListening?.takeIf { showKeepListening && it.isNotEmpty() }?.let { keepListening ->
                item(key = "keep_listening_title") {
                    NavigationTitle(
                        title = stringResource(R.string.keep_listening),
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "keep_listening") {
                    LazyHorizontalGrid(
                        state = rememberLazyGridState(),
                        rows = GridCells.Fixed(1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(GridThumbnailHeight + with(LocalDensity.current) {
                                MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                                        MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                            })
                            .animateItem()
                    ) {
                        items(keepListening) {
                            localGridItem(it, stringResource(R.string.keep_listening))
                        }
                    }
                }
            }

            accountPlaylists?.takeIf { showAccountPlaylists && it.isNotEmpty() }?.let { accountPlaylists ->
                item(key = "account_playlists_title") {
                    NavigationTitle(
                        title = stringResource(R.string.your_youtube_playlists),
                        onClick = {
                            navController.navigate("account")
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                item(key = "account_playlists") {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = accountPlaylists,
                            key = { it.id },
                        ) { item ->
                            ytGridItem(item)
                        }
                    }
                }
            }

            similarRecommendations?.takeIf { showSimilarTo }?.forEach {
                item {
                    NavigationTitle(
                        label = stringResource(R.string.similar_to),
                        title = it.title.title,
                        thumbnail = it.title.thumbnailUrl?.let { thumbnailUrl ->
                            {
                                val shape =
                                    if (it.title is Artist) CircleShape else RoundedCornerShape(
                                        ThumbnailCornerRadius
                                    )
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(ListThumbnailSize)
                                        .clip(shape)
                                )
                            }
                        },
                        onClick = {
                            when (it.title) {
                                is Song -> navController.navigate("album/${it.title.album!!.id}")
                                is Album -> navController.navigate("album/${it.title.id}")
                                is Artist -> navController.navigate("artist/${it.title.id}")
                                is Playlist -> {}
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                item {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars
                            .only(WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.animateItem()
                    ) {
                        items(it.items) { item ->
                            ytGridItem(item)
                        }
                    }
                }
            }

            homePage?.sections?.takeIf { showYtmSections }?.forEach {
                item {
                    NavigationTitle(
                        title = it.title,
                        label = it.label,
                        thumbnail = it.thumbnail?.let { thumbnailUrl ->
                            {
                                val shape =
                                    if (it.endpoint?.isArtistEndpoint == true) CircleShape else RoundedCornerShape(
                                        ThumbnailCornerRadius
                                    )
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(ListThumbnailSize)
                                        .clip(shape)
                                )
                            }
                        },
                        onClick = it.endpoint?.browseId?.let { browseId ->
                            {
                                if (browseId == "FEmusic_moods_and_genres")
                                    navController.navigate("mood_and_genres")
                                else
                                    navController.navigate("browse/$browseId")
                            }
                        },
                        onPlayClick = if (!it.isListStyle) null else it.playEndpoint?.let { playEndpoint ->
                            {
                                // No preload item: playQueue() would replace the fetched queue's
                                // first song with it.
                                playerConnection.playQueue(YouTubeQueue(playEndpoint))
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }

                if (it.isListStyle) {
                    item {
                        val songs = remember(it.items) { it.items.filterIsInstance<SongItem>() }
                        val songIds = remember(songs) { songs.map { song -> song.id } }
                        val rows = min(4, songs.size)
                        // Don't carry the scroll position over to a different shelf.
                        key(it.title, songIds) {
                            val lazyGridState = rememberLazyGridState()
                            val snapLayoutInfoProvider =
                                rememberGridItemSnapLayoutInfoProvider(lazyGridState, horizontalLazyGridItemWidthFactor)
                            LazyHorizontalGrid(
                                state = lazyGridState,
                                rows = GridCells.Fixed(rows),
                                flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                contentPadding = WindowInsets.systemBars
                                    .only(WindowInsetsSides.Horizontal)
                                    .asPaddingValues(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(ListItemHeight * rows)
                                    .animateItem()
                            ) {
                                items(songs) { song ->
                                    ytSongListItem(song, Modifier.width(horizontalLazyGridItemWidth))
                                }
                            }
                        }
                    }
                } else {
                    item {
                        val itemIds = remember(it.items) { it.items.map { item -> item.id } }
                        // Don't carry the scroll position over to a different shelf.
                        key(it.title, itemIds) {
                            LazyRow(
                                state = rememberLazyListState(),
                                contentPadding = WindowInsets.systemBars
                                    .only(WindowInsetsSides.Horizontal)
                                    .asPaddingValues(),
                                modifier = Modifier.animateItem()
                            ) {
                                items(it.items) { item ->
                                    ytGridItem(item)
                                }
                            }
                        }
                    }
                }
            }

            if (showYtmSections && homePage?.continuation != null && homePage?.sections?.isNotEmpty() == true) {
                item(key = "youtube_continuation_placeholder") {
                    ShimmerHost(
                        modifier = Modifier.animateItem()
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .width(250.dp),
                        )
                        LazyRow {
                            items(4) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            }

            explorePage?.moodAndGenres?.takeIf { showMood }?.let { moodAndGenres ->
                item(key = "mood_and_genres_title") {
                    NavigationTitle(
                        title = stringResource(R.string.mood_and_genres),
                        onClick = {
                            navController.navigate("mood_and_genres")
                        },
                        modifier = Modifier.animateItem()
                    )
                }
                item(key = "mood_and_genres") {
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(4),
                        contentPadding = PaddingValues(6.dp),
                        modifier = Modifier
                            .height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp)
                            .animateItem()
                    ) {
                        items(moodAndGenres) {
                            MoodAndGenresButton(
                                title = it.title,
                                onClick = {
                                    navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                                },
                                modifier = Modifier
                                    .padding(6.dp)
                                    .width(180.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item(key = "loading_placeholder") {
                    ShimmerHost(
                        modifier = Modifier.animateItem()
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .width(250.dp),
                        )
                        LazyRow {
                            items(4) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            }
        }

        HideOnScrollFAB(
            visible = allLocalItems.isNotEmpty() || allYtItems.isNotEmpty(),
            lazyListState = lazylistState,
            icon = Icons.Rounded.Casino,
            onClick = {
                val local = when {
                    allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5
                    allLocalItems.isNotEmpty() -> true
                    else -> false
                }
                if (local) {
                    when (val luckyItem = allLocalItems.random()) {
                        is Song -> playerConnection.playQueue(
                            YouTubeQueue.radio(luckyItem.toMediaMetadata()),
                            isRadio = true
                        )

                        is Album -> {
                            scope.launch(Dispatchers.IO) {
                                val songs = database.albumSongs(luckyItem.id).first()
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = luckyItem.title,
                                        items = songs.map(Song::toMediaMetadata)
                                    )
                                )
                            }
                        }
                        // not possible, already filtered out
                        is Artist -> {}
                        is Playlist -> {}
                    }
                } else {
                    when (val luckyItem = allYtItems.random()) {
                        is SongItem -> playerConnection.playQueue(
                            YouTubeQueue.radio(luckyItem.toMediaMetadata()),
                            isRadio = true
                        )

                        is AlbumItem -> playerConnection.playQueue(
                            YouTubeAlbumRadio(luckyItem.playlistId),
                            isRadio = true
                        )

                        is ArtistItem -> luckyItem.radioEndpoint?.let {
                            playerConnection.playQueue(YouTubeQueue(it), isRadio = true)
                        }

                        is PlaylistItem -> luckyItem.playEndpoint?.let {
                            playerConnection.playQueue(YouTubeQueue(it), isRadio = true)
                        }
                    }
                }
            }
        )

        Indicator(
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}

/**
 * Remembers a [SnapLayoutInfoProvider] that snaps an item so that its center sits at
 * [itemWidthFactor] / 2 of the viewport width, excluding content padding.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberGridItemSnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    itemWidthFactor: Float,
): SnapLayoutInfoProvider = remember(lazyGridState, itemWidthFactor) {
    SnapLayoutInfoProvider(
        lazyGridState = lazyGridState,
        positionInLayout = { layoutSize, itemSize ->
            (layoutSize * itemWidthFactor / 2f - itemSize / 2f)
        }
    )
}

private const val RecentRows = 2
private val RecentEdgePadding = 12.dp
private val RecentTileSpacing = 4.dp

private data class RecentActivityLayout(
    val columns: Int,
    val tileSize: Dp,
    val startPadding: Dp,
    val endPadding: Dp,
)

/**
 * Recent activity layout with as many columns as fit at the Keep listening thumbnail size.
 * Tile sizes are adjusted to fill the available width.
 */
@Composable
private fun recentActivityLayout(maxWidth: Dp, listPadding: PaddingValues): RecentActivityLayout {
    val layoutDirection = LocalLayoutDirection.current
    val barPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val startPadding = RecentEdgePadding + barPadding.calculateStartPadding(layoutDirection)
    val endPadding = RecentEdgePadding + barPadding.calculateEndPadding(layoutDirection)
    // The list's content padding holds the navigation rail width when the rail is shown.
    val rowWidth = maxWidth -
            listPadding.calculateStartPadding(layoutDirection) -
            listPadding.calculateEndPadding(layoutDirection) -
            startPadding - endPadding
    val columns = ((rowWidth + RecentTileSpacing) / (GridThumbnailHeight + RecentTileSpacing))
        .toInt()
        .coerceAtLeast(1)
    val tileSize = (rowWidth - RecentTileSpacing * (columns - 1)) / columns
    return RecentActivityLayout(columns, tileSize, startPadding, endPadding)
}

private fun RecentActivityEntity.isQueued(queuePlaylistId: String?): Boolean =
    queuePlaylistId != null && when (type) {
        RecentActivityType.PLAYLIST -> queuePlaylistId == id
        RecentActivityType.ALBUM -> queuePlaylistId == playlistId
        RecentActivityType.ARTIST -> queuePlaylistId == radioPlaylistId ||
                queuePlaylistId == shufflePlaylistId ||
                queuePlaylistId == playlistId
    }

@Composable
private fun RecentActivityPager(
    pages: List<List<RecentActivityEntity>>,
    layout: RecentActivityLayout,
    pagerState: PagerState,
    queuePlaylistId: String?,
    isPlaying: Boolean,
    onItemClick: (RecentActivityEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fill,
            // Add space so less of the next or previous page shows at the edges.
            pageSpacing = RecentEdgePadding * 2,
            contentPadding = PaddingValues(start = layout.startPadding, end = layout.endPadding),
            key = { page -> pages[page].first().id },
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.tileSize * RecentRows + RecentTileSpacing * (RecentRows - 1))
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(RecentTileSpacing)) {
                pages[page].chunked(layout.columns).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(RecentTileSpacing)) {
                        rowItems.forEach { item ->
                            RecentActivityTile(
                                item = item,
                                size = layout.tileSize,
                                isActive = item.isQueued(queuePlaylistId),
                                isPlaying = isPlaying,
                                onClick = { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }

        if (pages.size > 1) {
            PagerIndicator(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun PagerIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = modifier
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (page == currentPage) 1f else 0.3f
                        )
                    )
            )
        }
    }
}
