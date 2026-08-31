package io.github.yuuichi_s.astertune.db.entities

/**
 * Representative artwork path for a local artist, derived from the artist's local albums or songs.
 */
data class LocalArtistThumbnail(
    val artistId: String,
    val thumbnailUrl: String?,
)
