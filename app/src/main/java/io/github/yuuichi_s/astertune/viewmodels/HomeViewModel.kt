package io.github.yuuichi_s.astertune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yuuichi_s.astertune.constants.InnerTubeCookieKey
import io.github.yuuichi_s.astertune.constants.PlaylistFilter
import io.github.yuuichi_s.astertune.constants.PlaylistSortType
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.Album
import io.github.yuuichi_s.astertune.db.entities.LocalItem
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.models.SimilarRecommendation
import io.github.yuuichi_s.astertune.utils.SyncUtils
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.reportException
import io.github.yuuichi_s.astertune.utils.syncCoroutine
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.pages.ExplorePage
import com.zionhuang.innertube.pages.HomePage
import com.zionhuang.innertube.utils.completed
import com.zionhuang.innertube.utils.parseCookieString
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val playlists = database.playlists(PlaylistFilter.LIBRARY, PlaylistSortType.NAME, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val recentActivity = database.recentActivity()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    private suspend fun load(manual: Boolean) =
        try {
            isLoading.value = true

            quickPicks.value = database.quickPicks()
                .first().shuffled().take(20)

            forgottenFavorites.value = database.forgottenFavorites()
                .first().shuffled().take(20)

            val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
            val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                .first().shuffled().take(10)
            val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
                .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
            val keepListeningArtists = database.mostPlayedArtists(0, 1)
                .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
            keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

            allLocalItems.value =
                (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
                    .filter { it is Song || it is Album }

            // The three groups below do not depend on each other, so they run at the same time.
            coroutineScope {
                launch {
                    if (YouTube.cookie != null) {
                        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                        }.onFailure {
                            reportException(it)
                        }
                    }

                    syncUtils.syncRecentActivity(manual)
                }

                launch {
                    // Similar to artists
                    val artistRecommendations =
                        database.mostPlayedArtists(0, 1, limit = 10).first()
                            .filter { it.artist.isYouTubeArtist }
                            .shuffled().take(2)
                            .mapNotNull {
                                val items = mutableListOf<YTItem>()
                                YouTube.artist(it.id).onSuccess { page ->
                                    items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                                    items += page.sections.lastOrNull()?.items.orEmpty()
                                }
                                SimilarRecommendation(
                                    title = it,
                                    items = items
                                        .shuffled()
                                        .ifEmpty { return@mapNotNull null }
                                )
                            }
                    // Similar to songs
//                    val songCandidates = database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
//                        .filter { it.album != null }
//                        .shuffled().take(2)
//                    val songRecommendations =
//                        songCandidates
//                            .mapNotNull { song ->
//                                val endpoint = YouTube.next(WatchEndpoint(videoId = song.id))
//                                    .getOrNull()?.relatedEndpoint
//                                if (endpoint == null) return@mapNotNull null
//                                val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
//                                SimilarRecommendation(
//                                    title = song,
//                                    items = (page.songs.shuffled().take(8) +
//                                            page.albums.shuffled().take(4) +
//                                            page.artists.shuffled().take(4) +
//                                            page.playlists.shuffled().take(4))
//                                        .shuffled()
//                                        .ifEmpty { return@mapNotNull null }
//                                )
//                            }
//                    similarRecommendations.value =
//                        (artistRecommendations + songRecommendations).shuffled()
                    similarRecommendations.value = artistRecommendations.shuffled()
                }

                launch {
                    YouTube.home().onSuccess { page ->
                        homePage.value = page
                    }.onFailure {
                        reportException(it)
                    }

                    YouTube.explore().onSuccess { page ->
                        explorePage.value = page
                    }.onFailure {
                        reportException(it)
                    }
                }
            }

            allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                    homePage.value?.sections?.flatMap { it.items }.orEmpty()
        } finally {
            isLoading.value = false
        }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null) return
        val requestedHomePage = homePage.value?.takeIf { it.continuation == continuation } ?: return

        viewModelScope.launch(Dispatchers.IO) {
            if (!_isLoadingMore.compareAndSet(false, true)) return@launch

            try {
                if (homePage.value !== requestedHomePage) return@launch

                val nextSections = YouTube.home(continuation).getOrNull() ?: return@launch
                homePage.update { currentHomePage ->
                    if (currentHomePage !== requestedHomePage) {
                        currentHomePage
                    } else {
                        nextSections.copy(
                            chips = currentHomePage.chips,
                            sections = currentHomePage.sections + nextSections.sections
                        )
                    }
                }
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            // store the actual homepage for deselecting chips
            previousHomePage.value = homePage.value
        }
        viewModelScope.launch(Dispatchers.IO) {
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch
            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections,
                continuation = nextSections.continuation
            )
            selectedChip.value = chip
        }
    }

    /**
     * Refreshes the home content.
     *
     * When [manual] is `true`, recent activity synchronization ignores the
     * auto-sync setting and cooldown.
     */
    fun refresh(manual: Boolean = false) {
        if (isRefreshing.value) return
        viewModelScope.launch(syncCoroutine) {
            isRefreshing.value = true
            try {
                load(manual)
            } finally {
                isRefreshing.value = false
            }
        }
    }

    init {
        refresh()
        viewModelScope.launch(syncCoroutine) {
            syncUtils.tryAutoSync()
        }
        // Reload when the sign-in state changes so the home reflects login without a manual refresh.
        viewModelScope.launch {
            context.dataStore.data
                .map { "SAPISID" in parseCookieString(it[InnerTubeCookieKey] ?: "") }
                .distinctUntilChanged()
                .drop(1) // skip the current state already loaded by refresh() above
                .collect { refresh() }
        }
    }
}
