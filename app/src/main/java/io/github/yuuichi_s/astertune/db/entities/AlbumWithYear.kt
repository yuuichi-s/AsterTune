/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.db.entities

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * An album with the year used for sorting.
 *
 * Uses the album's own year, otherwise the newest year among the queried artist's linked
 * library songs. The year is null when neither provides one.
 */
data class AlbumWithYear(
    @Embedded
    val album: AlbumEntity,
    val downloadCount: Int,
    val sortYear: Int?,
    @Relation(
        entity = ArtistEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy = Junction(
            value = AlbumArtistMap::class,
            parentColumn = "albumId",
            entityColumn = "artistId"
        )
    )
    val artists: List<ArtistEntity>,
)
