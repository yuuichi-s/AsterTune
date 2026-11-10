package io.github.yuuichi_s.astertune.utils

import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.Artist
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal fun resolveArtistThumbnailUrl(
    artist: ArtistEntity,
    representativeThumbnail: String?,
    savedThumbnailUsable: Boolean,
): String? = if (!artist.isLocal || savedThumbnailUsable) {
    artist.thumbnailUrl
} else {
    representativeThumbnail
}

internal data class ArtistThumbnailData(
    val representativeByArtist: Map<String, String?>,
    val usableSavedArtistIds: Set<String>,
) {
    fun thumbnailUrl(artist: ArtistEntity): String? = resolveArtistThumbnailUrl(
        artist = artist,
        representativeThumbnail = representativeByArtist[artist.id],
        savedThumbnailUsable = artist.id in usableSavedArtistIds,
    )

    fun withThumbnail(artist: Artist): Artist {
        val thumbnailUrl = thumbnailUrl(artist.artist)
        return if (thumbnailUrl == artist.artist.thumbnailUrl) {
            artist
        } else {
            artist.copy(artist = artist.artist.copy(thumbnailUrl = thumbnailUrl))
        }
    }
}

internal fun MusicDatabase.artistThumbnailData(): Flow<ArtistThumbnailData> = combine(
    localArtistThumbnails(),
    localArtistsWithUsableThumbnail(),
) { thumbnails, usableIds ->
    ArtistThumbnailData(
        representativeByArtist = thumbnails.associate { it.artistId to it.thumbnailUrl },
        usableSavedArtistIds = usableIds.toSet(),
    )
}

internal fun Flow<List<Artist>>.withArtistThumbnails(database: MusicDatabase): Flow<List<Artist>> =
    combine(this, database.artistThumbnailData()) { artists, thumbnails ->
        artists.map(thumbnails::withThumbnail)
    }
