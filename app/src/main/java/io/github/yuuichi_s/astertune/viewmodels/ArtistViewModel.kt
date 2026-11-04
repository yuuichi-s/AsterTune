package io.github.yuuichi_s.astertune.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.BrowseEndpoint
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.pages.ArtistPage
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.yuuichi_s.astertune.constants.ArtistSongSortType
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.utils.reportException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = database.artistSongs(artistId, ArtistSongSortType.CREATE_DATE, descending = true)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = database.artistAlbumsPreview(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isLoading = MutableStateFlow(true)

    /** Whether the artist is a local-media artist; null until the database has been read. */
    val isLocalArtist = MutableStateFlow<Boolean?>(null)

    init {
        viewModelScope.launch {
            val isLocal = database.artist(artistId).first()?.artist?.isLocal == true
            isLocalArtist.value = isLocal
            if (!isLocal) fetchArtistsFromYTM()
        }
    }

    /**
     * Loads unique songs in page order from the artist section's linked page.
     * If a continuation fails, returns the songs loaded up to that point.
     *
     * @throws Exception if the first page fails to load
     */
    suspend fun loadAllSongs(endpoint: BrowseEndpoint): List<MediaMetadata> {
        val firstPage = YouTube.artistItems(endpoint).getOrThrow()
        val songs =
            firstPage.items.filterIsInstance<SongItem>() + fetchArtistSongsFrom(firstPage.continuation)
        return songs.distinctBy { it.id }.map { it.toMediaMetadata() }
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            isLoading.value = true
            YouTube.artist(artistId)
                .onSuccess {
                    artistPage = it
                }.onFailure {
                    reportException(it)
                }

            isLoading.value = false
        }
    }
}

/**
 * Fetches the songs from the continuation [continuation] to the end of an artist's items.
 * A continuation that fails to load ends the fetch, keeping the songs so far.
 */
suspend fun fetchArtistSongsFrom(continuation: String?): List<SongItem> {
    val songs = mutableListOf<SongItem>()
    var next = continuation
    while (next != null) {
        val page = YouTube.artistItemsContinuation(next).getOrNull() ?: break
        songs += page.items.filterIsInstance<SongItem>()
        next = page.continuation
    }
    return songs
}
