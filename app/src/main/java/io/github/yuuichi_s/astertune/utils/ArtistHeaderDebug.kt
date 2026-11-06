package io.github.yuuichi_s.astertune.utils

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yuuichi_s.astertune.constants.IMAGE_DEBUG
import io.github.yuuichi_s.astertune.db.MusicDatabase
import kotlinx.coroutines.launch

internal const val IMAGE_DEBUG_NEWEST_ARTIST_ALBUM_QUERY = """
    SELECT t.albumId AS albumId, COALESCE(t.thumb, t.rawThumb) AS thumbnailUrl
    FROM (
        SELECT album.id AS albumId,
               album.thumbnailUrl AS rawThumb,
               COALESCE(
                   album.year,
                   (SELECT MAX(s.year)
                    FROM song_artist_map sam
                        INNER JOIN song s ON sam.songId = s.id
                    WHERE sam.artistId = :artistId
                        AND s.inLibrary IS NOT NULL
                        AND (s.albumId = album.id
                            OR s.id IN (SELECT m.songId FROM song_album_map m WHERE m.albumId = album.id)))
               ) AS newestYear,
               COALESCE(
                   CASE WHEN album.thumbnailUrl NOT LIKE '/%' THEN album.thumbnailUrl END,
                   (SELECT s.localPath
                    FROM song s
                    WHERE s.localPath = album.thumbnailUrl
                        AND s.inLibrary IS NOT NULL
                    LIMIT 1),
                   (SELECT MIN(s.localPath)
                    FROM song_album_map m
                    INNER JOIN song s ON s.id = m.songId
                    WHERE m.albumId = album.id
                        AND s.inLibrary IS NOT NULL
                        AND s.localPath IS NOT NULL)
               ) AS thumb
        FROM album
        WHERE album.id IN (
            SELECT song_album_map.albumId
            FROM song_artist_map
                INNER JOIN song ON song_artist_map.songId = song.id
                INNER JOIN song_album_map ON song_album_map.songId = song.id
            WHERE song_artist_map.artistId = :artistId
                AND song.inLibrary IS NOT NULL
        )
    ) t
    ORDER BY t.newestYear IS NULL, t.newestYear DESC
    LIMIT 1
"""

internal class ArtistHeaderDebug private constructor(
    private val artistId: String,
) {
    private val createdAt = SystemClock.elapsedRealtime()
    private var headerChanges = 0
    private var lastHeader: String? = null

    private suspend fun logNewestAlbumQuery(database: MusicDatabase) {
        val start = SystemClock.elapsedRealtime()
        val result = database.debugNewestArtistAlbumThumbnail(artistId)
        val end = SystemClock.elapsedRealtime()
        Log.d(
            IMAGE_TAG,
            "artistHeaderQuery artistId=$artistId sinceOpen=${end - createdAt}ms " +
                "query=${end - start}ms album=${result?.albumId} header=${result?.thumbnailUrl}"
        )
    }

    fun onHeader(header: String?, songCount: Int, albumCount: Int, albumThumbnailCount: Int) {
        if (header != lastHeader) {
            headerChanges++
            lastHeader = header
        }
        Log.d(
            IMAGE_TAG,
            "artistHeader artistId=$artistId sinceOpen=${SystemClock.elapsedRealtime() - createdAt}ms " +
                "changes=$headerChanges songs=$songCount albums=$albumCount " +
                "albumThumbs=$albumThumbnailCount header=$header"
        )
    }

    companion object {
        fun create(viewModel: ViewModel, database: MusicDatabase, artistId: String): ArtistHeaderDebug? {
            if (!IMAGE_DEBUG) return null
            return ArtistHeaderDebug(artistId).apply {
                viewModel.viewModelScope.launch { logNewestAlbumQuery(database) }
            }
        }
    }
}
