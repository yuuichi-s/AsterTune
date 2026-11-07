package io.github.yuuichi_s.astertune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yuuichi_s.astertune.constants.ArtistSongSortDescendingKey
import io.github.yuuichi_s.astertune.constants.ArtistSongSortType
import io.github.yuuichi_s.astertune.constants.ArtistSongSortTypeKey
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.Album
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.extensions.toEnum
import io.github.yuuichi_s.astertune.utils.ArtistHeaderDebug
import io.github.yuuichi_s.astertune.utils.dataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * An album of a local artist, with the year taken from its songs' tags when the album has none.
 */
data class LocalArtistAlbum(
    val album: Album,
    val year: Int?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalArtistViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    private val imageDebug = ArtistHeaderDebug.create(this, database, artistId)

    private val artistRow = database.artist(artistId)
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val artist = artistRow
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs = context.dataStore.data
        .map {
            it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.artistSongs(artistId, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val genres = songs
        .map(::localArtistGenres)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val latestYear = songs
        .map(::localArtistLatestYear)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val totalSeconds = songs
        .map(::localArtistTotalSeconds)
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val albums = database.artistSongAlbums(artistId)
        .map { albums ->
            albums.map { LocalArtistAlbum(Album(it.album, it.downloadCount, it.artists), it.sortYear) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Artwork that can still be shown for each album, keyed by album ID. */
    val albumThumbnails = database.artistAlbumThumbnails(artistId)
        .map { thumbnails -> thumbnails.associate { it.albumId to it.thumbnailUrl } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /** Artwork of the newest album, used when the artist has no usable artwork of its own. */
    val newestAlbumThumbnail = combine(albums, albumThumbnails) { albums, thumbnails ->
        albums.firstOrNull()?.let { thumbnails[it.album.id] ?: it.album.album.thumbnailUrl }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** The artwork chosen for the artist while it can still be shown, otherwise null. */
    private val usableArtistThumbnail = artistRow
        .map { it?.artist?.thumbnailUrl }
        .distinctUntilChanged()
        .flatMapLatest { url ->
            when {
                url == null -> flowOf(null)
                !url.startsWith("/") -> flowOf(url)
                else -> database.isLibrarySongPath(url).map { if (it) url else null }
            }
        }

    val headerThumbnail = combine(usableArtistThumbnail, newestAlbumThumbnail) { chosen, newest ->
        chosen ?: newest
    }.onEach { header ->
        imageDebug?.onHeader(header, songs.value.size, albums.value.size, albumThumbnails.value.size)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)
}

/** Genres of [songs], most songs first. */
internal fun localArtistGenres(songs: List<Song>): List<String> =
    songs.flatMap { song -> song.genre.orEmpty().map { it.title }.distinct() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }

/** Newest tag year among [songs]. */
internal fun localArtistLatestYear(songs: List<Song>): Int? =
    songs.mapNotNull { it.song.year }.maxOrNull()

/** Total length of [songs] in seconds; songs with an unknown length count as zero. */
internal fun localArtistTotalSeconds(songs: List<Song>): Int =
    songs.sumOf { it.song.duration.coerceAtLeast(0) }
