package io.github.yuuichi_s.astertune.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.pages.AlbumPage
import io.github.yuuichi_s.astertune.db.daos.AlbumsDao
import io.github.yuuichi_s.astertune.db.daos.ArtistsDao
import io.github.yuuichi_s.astertune.db.daos.PlaylistsDao
import io.github.yuuichi_s.astertune.db.daos.QueueDao
import io.github.yuuichi_s.astertune.db.daos.SongsDao
import io.github.yuuichi_s.astertune.db.entities.AlbumArtistMap
import io.github.yuuichi_s.astertune.db.entities.AlbumEntity
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.Event
import io.github.yuuichi_s.astertune.db.entities.EventWithSong
import io.github.yuuichi_s.astertune.db.entities.FormatEntity
import io.github.yuuichi_s.astertune.db.entities.GenreEntity
import io.github.yuuichi_s.astertune.db.entities.LyricsEntity
import io.github.yuuichi_s.astertune.db.entities.QueueEntity
import io.github.yuuichi_s.astertune.db.entities.QueueSongMap
import io.github.yuuichi_s.astertune.db.entities.RecentActivityEntity
import io.github.yuuichi_s.astertune.db.entities.RecentActivityType
import io.github.yuuichi_s.astertune.db.entities.RelatedSongMap
import io.github.yuuichi_s.astertune.db.entities.SearchHistory
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongAlbumMap
import io.github.yuuichi_s.astertune.db.entities.SongArtistMap
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import io.github.yuuichi_s.astertune.db.entities.SongGenreMap
import io.github.yuuichi_s.astertune.extensions.toSQLiteQuery
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface DatabaseDao : SongsDao, AlbumsDao, ArtistsDao, PlaylistsDao, QueueDao {

    // GROUP BY may take songId from any row in the group.
    // Use EXISTS to check all source songs.
    @Transaction
    @Query("""
        SELECT song.*
        FROM (SELECT *, COUNT(DISTINCT songId) AS referredCount
              FROM related_song_map
              GROUP BY relatedSongId) map
                 JOIN song ON song.id = map.relatedSongId
        WHERE EXISTS (SELECT 1
                      FROM related_song_map eligible
                      WHERE eligible.relatedSongId = map.relatedSongId
                        AND eligible.songId IN (SELECT songId
                                                FROM (SELECT songId
                                                      FROM event
                                                      ORDER BY ROWID DESC
                                                      LIMIT 5)
                                                UNION
                                                SELECT songId
                                                FROM (SELECT songId
                                                      FROM event
                                                      WHERE timestamp > :now - 86400000 * 7
                                                      GROUP BY songId
                                                      ORDER BY SUM(playTime) DESC
                                                      LIMIT 5)
                                                UNION
                                                SELECT id
                                                FROM (SELECT id
                                                      FROM song
                                                      LIMIT 10)))
        ORDER BY referredCount DESC
        LIMIT 100
    """)
    fun quickPicks(now: Long = System.currentTimeMillis()): Flow<List<Song>>

    @Query("SELECT * FROM format WHERE id = :id")
    fun format(id: String?): Flow<FormatEntity?>

    @Query("SELECT loudnessDb FROM format WHERE id = :id")
    fun formatLoudnessDb(id: String): Double?

    @Query("UPDATE format SET loudnessDb = :loudnessDb WHERE id = :id")
    fun updateLoudnessDb(id: String, loudnessDb: Double)

    @Query("UPDATE format SET loudnessDb = :loudnessDb WHERE id = :id AND loudnessDb IS NULL")
    fun updateLoudnessDbIfAbsent(id: String, loudnessDb: Double)

    @Query("SELECT * FROM lyrics WHERE id = :id")
    fun lyrics(id: String?): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics WHERE id = :id")
    fun lyricsById(id: String): LyricsEntity?

    @Query("SELECT * FROM event WHERE songId = :songId ORDER BY timestamp")
    fun eventsBySong(songId: String): List<Event>

    @Transaction
    @Query("SELECT * FROM event ORDER BY timestamp DESC, rowId DESC")
    fun events(): Flow<List<EventWithSong>>

    @Query("DELETE FROM event")
    fun clearListenHistory()

    @Query("SELECT * FROM search_history WHERE `query` LIKE :query || '%' ORDER BY id DESC")
    fun searchHistory(query: String = ""): Flow<List<SearchHistory>>

    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

    @Query("SELECT EXISTS(SELECT 1 FROM related_song_map WHERE songId = :songId)")
    fun hasRelatedSongs(songId: String): Boolean

    @Transaction
    @Query(
        """
        SELECT song.*
        FROM (SELECT DISTINCT relatedSongId
              FROM related_song_map
              WHERE songId = :songId) map
                 JOIN
             song
             ON song.id = map.relatedSongId
        """
    )
    fun relatedSongs(songId: String): List<Song>

    /**
     * All local genres in insertion order, so callers that merge duplicates can pick the oldest
     * row of a group without another sort.
     */
    @Query("SELECT * FROM genre WHERE genre.isLocal = 1 ORDER BY genre.rowId ASC")
    fun allLocalGenres(): List<GenreEntity>

    @Query("SELECT * FROM genre WHERE id = :id")
    fun genreById(id: String): GenreEntity?

    @Query("UPDATE genre SET bookmarkedAt = :bookmarkedAt WHERE id = :genreId")
    fun updateGenreBookmark(genreId: String, bookmarkedAt: LocalDateTime?)

    @Query("SELECT * FROM genre WHERE title = :name")
    fun genreByName(name: String): GenreEntity?

    @Query("SELECT * FROM genre WHERE isLocal = 1 AND title LIKE '%' || :query || '%' LIMIT :previewSize")
    fun localGenreByNameFuzzy(query: String, previewSize: Int = Int.MAX_VALUE): List<GenreEntity>

    /**
     * Relink songs of [oldId] to [newId]. Rows whose song is already linked to [newId] would
     * violate the (songId, genreId) primary key, so they are left behind for [deleteSongGenreMap]
     * to remove.
     */
    @Transaction
    @Query("UPDATE OR IGNORE song_genre_map SET genreId = :newId WHERE genreId = :oldId")
    fun updateSongGenreMap(oldId: String, newId: String)

    @Query("DELETE FROM song_genre_map WHERE genreId = :genreId")
    fun deleteSongGenreMap(genreId: String)

    @Query(
        """
        DELETE FROM genre
        WHERE NOT EXISTS (
            SELECT 1
            FROM song_genre_map
            WHERE song_genre_map.genreId = :genreId
        )
        AND id = :genreId
    """
    )
    fun safeDeleteGenre(genreId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(genre: GenreEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongGenreMap)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(searchHistory: SearchHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: RelatedSongMap)

    /**
     * Inserts the song in [mediaMetadata] with its artists, genres, and album unless it already
     * exists.
     *
     * Local songs are added only by the local media scanner through [insertLocalSong].
     * This method skips local songs so removed songs are not recreated from metadata held by
     * a queue or the player.
     */
    @Transaction
    fun insert(mediaMetadata: MediaMetadata, block: (SongEntity) -> SongEntity = { it }) {
        if (mediaMetadata.isLocal) return
        insertSongWithRelations(mediaMetadata, block)
    }

    /**
     * Inserts a local song found by the local media scanner, as [insert] does for other songs.
     */
    @Transaction
    fun insertLocalSong(mediaMetadata: MediaMetadata) {
        insertSongWithRelations(mediaMetadata) { it }
    }

    /**
     * Inserts a song with its artists, genres, and album unless the song ID already exists.
     *
     * Requires a caller-provided database transaction and does not filter local songs.
     * Use [insert] or [insertLocalSong] for normal insertion.
     */
    fun insertSongWithRelations(mediaMetadata: MediaMetadata, block: (SongEntity) -> SongEntity) {
        val album = mediaMetadata.album?.let { albumsByName(it.title, it.isLocal) }
        val albumId = mediaMetadata.album?.let { album?.id ?: GenreEntity.generateGenreId() }

        val songEntity = mediaMetadata.toSongEntity().let(block).let {
            // Use the resolved database album ID for local songs.
            // Preserve YouTube album IDs for navigation.
            if (mediaMetadata.isLocal && albumId != null) it.copy(albumId = albumId) else it
        }
        if (insert(songEntity) == -1L) return
        mediaMetadata.artists.forEachIndexed { index, artist ->
            // Local scans generate per-file artist IDs, so match local artists by name first.
            val artistId = if (artist.isLocal) {
                localArtistByName(artist.name)?.id ?: artist.id ?: ArtistEntity.generateArtistId()
            } else {
                artist.id ?: remoteArtistByName(artist.name)?.id ?: ArtistEntity.generateArtistId()
            }
            insert( // TODO: use upsert???
                ArtistEntity(
                    id = artistId,
                    name = artist.name,
                    isLocal = artist.isLocal
                )
            )
            insert(
                SongArtistMap(
                    songId = mediaMetadata.id,
                    artistId = artistId,
                    position = index
                )
            )
        }
        mediaMetadata.genre?.forEachIndexed { index, genre ->
            val genreId = genreByName(genre.title)?.id ?: GenreEntity.generateGenreId()
            insert( // TODO: use upsert???
                GenreEntity(
                    id = genreId,
                    title = genre.title,
                    isLocal = genre.isLocal
                )
            )
            insert(
                SongGenreMap(
                    songId = mediaMetadata.id,
                    genreId = genreId,
                    index = index
                )
            )
        }

        mediaMetadata.album?.let {
            upsert(
                if (album != null) {
                    // Preserve the other album fields, including bookmarks and the year.
                    album.copy(
                        songCount = album.songCount.coerceAtLeast(1),
                        duration = album.duration + mediaMetadata.duration,
                        thumbnailUrl = album.thumbnailUrl ?: mediaMetadata.thumbnailUrl
                    )
                } else {
                    AlbumEntity(
                        id = albumId!!,
                        title = it.title,
                        thumbnailUrl = mediaMetadata.thumbnailUrl,
                        songCount = 1,
                        duration = mediaMetadata.duration,
                        isLocal = it.isLocal
                    )
                }
            )
            insert(
                SongAlbumMap(
                    songId = mediaMetadata.id,
                    albumId = albumId!!,
                    index = album?.songCount ?: 0
                )
            )
        }
    }

    @Transaction
    fun insert(albumPage: AlbumPage) {
        if (insert(AlbumEntity(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { it.duration ?: 0 }
            )) == -1L
        ) return
        albumPage.songs.map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index
                )
            }
            .forEach(::upsert)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId(),
                    name = artist.name
                )
            }
            ?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index
                )
            }
            ?.forEach(::insert)
    }

    @Transaction
    fun update(album: AlbumEntity, albumPage: AlbumPage) {
        update(
            album.copy(
                id = albumPage.album.browseId,
                playlistId = albumPage.album.playlistId,
                title = albumPage.album.title,
                year = albumPage.album.year,
                thumbnailUrl = albumPage.album.thumbnail,
                songCount = albumPage.songs.size,
                duration = albumPage.songs.sumOf { it.duration ?: 0 }
            )
        )
        albumPage.songs.map(SongItem::toMediaMetadata)
            .onEach(::insert)
            .mapIndexed { index, song ->
                SongAlbumMap(
                    songId = song.id,
                    albumId = albumPage.album.browseId,
                    index = index
                )
            }
            .forEach(::upsert)
        albumPage.album.artists
            ?.map { artist ->
                ArtistEntity(
                    id = artist.id ?: artistByName(artist.name)?.id ?: ArtistEntity.generateArtistId(),
                    name = artist.name
                )
            }
            ?.onEach(::insert)
            ?.mapIndexed { index, artist ->
                AlbumArtistMap(
                    albumId = albumPage.album.browseId,
                    artistId = artist.id,
                    order = index
                )
            }
            ?.forEach(::insert)
    }

    @Upsert
    fun upsert(lyrics: LyricsEntity)

    @Upsert
    fun upsert(format: FormatEntity)

    @Delete
    fun delete(lyrics: LyricsEntity)

    @Query("DELETE FROM lyrics where id = :id")
    fun deleteLyricById(id: String)

    @Query("DELETE FROM format WHERE id = :id")
    fun deleteFormatById(id: String)

    @Query("""
        DELETE FROM genre
        WHERE isLocal = 1
            AND bookmarkedAt IS NULL
            AND NOT EXISTS (SELECT 1 FROM song_genre_map WHERE song_genre_map.genreId = genre.id)
    """)
    fun deleteUnbookmarkedLocalGenresWithoutSongs()

    @Delete
    fun delete(searchHistory: SearchHistory)

    @Delete
    fun delete(event: Event)


    /**
     * Replaces [queue] and its stored song mappings. Does nothing if [songs] is empty.
     *
     * Mappings are saved only for songs present in the database after inserting [songs].
     * Removed local songs are not recreated by [insert], even if they remain in the queue.
     *
     * Use [QueueDao.update] to update queue metadata without replacing its song mappings.
     */
    @Transaction
    fun saveQueue(queue: QueueEntity, songs: List<MediaMetadata>, songMaps: List<QueueSongMap>) {
        if (songs.isEmpty()) {
            return
        }

        insert(queue)
        deleteAllQueueSongs(queue.id)
        songs.forEach { insert(it) }
        val storedIds = storedSongIds(songMaps.map { it.songId })
        songMaps.forEach { if (it.songId in storedIds) insert(it) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(item: RecentActivityEntity)

    @Delete
    fun delete(item: RecentActivityEntity)

    @Query("DELETE FROM recent_activity")
    fun clearRecentActivity()

    /**
     * Replaces the whole recent activity table in a single transaction, keeping [items] in order.
     *
     * The dates are assigned here, one second apart, because the query orders by date descending.
     */
    @Transaction
    fun replaceRecentActivity(items: List<YTItem>) {
        clearRecentActivity()
        val date = LocalDateTime.now()
        items.forEachIndexed { index, item ->
            insertRecentActivityItem(item, date.minusSeconds(index.toLong()))
        }
    }

    @Transaction
    fun insertRecentActivityItem(item: YTItem, date: LocalDateTime) {
        when (item) {
            is AlbumItem -> {
                insert(
                    RecentActivityEntity(
                        id = item.browseId,
                        title = item.title,
                        thumbnail = item.thumbnail,
                        explicit = item.explicit,
                        shareLink = item.shareLink,
                        type = RecentActivityType.ALBUM,
                        playlistId = item.playlistId,
                        radioPlaylistId = null,
                        shufflePlaylistId = null,
                        date = date
                    )
                )
            }

            is PlaylistItem -> {
                insert(
                    RecentActivityEntity(
                        id = item.id,
                        title = item.title,
                        thumbnail = item.thumbnail,
                        explicit = item.explicit,
                        shareLink = item.shareLink,
                        type = RecentActivityType.PLAYLIST,
                        playlistId = item.id,
                        radioPlaylistId = item.radioEndpoint?.playlistId,
                        shufflePlaylistId = item.shuffleEndpoint?.playlistId,
                        date = date
                    )
                )
            }

            is ArtistItem -> {
                insert(
                    RecentActivityEntity(
                        id = item.id,
                        title = item.title,
                        thumbnail = item.thumbnail,
                        explicit = item.explicit,
                        shareLink = item.shareLink,
                        type = RecentActivityType.ARTIST,
                        playlistId = item.playEndpoint?.playlistId,
                        radioPlaylistId = item.radioEndpoint?.playlistId,
                        shufflePlaylistId = item.shuffleEndpoint?.playlistId,
                        date = date
                    )
                )
            }

            else -> {
                // do nothing
            }
        }
    }

    @Query("SELECT * FROM recent_activity ORDER BY date DESC")
    fun recentActivity(): Flow<List<RecentActivityEntity>>

    /**
     * Nukes
     */

    @Transaction
    @Query("DELETE FROM genre WHERE isLocal = 1")
    fun nukeLocalGenre()

    @Transaction
    @Query("""
DELETE FROM format 
WHERE format.id IS NOT NULL 
AND NOT EXISTS (
    SELECT 1 FROM song WHERE song.id = format.id
);
    """)
    fun nukeDanglingFormatEntities()

    @Transaction
    @Query("DELETE FROM lyrics WHERE lyrics.id IN (SELECT song.id FROM song WHERE song.isLocal)")
    fun nukeLocalLyrics()

    @Transaction
    @Query("DELETE FROM lyrics WHERE lyrics.id NOT IN (SELECT song.id FROM song)")
    fun nukeDanglingLyrics()

    @Transaction
    @Query("DELETE FROM playlist WHERE isLocal = 0")
    fun nukeRemotePlaylists()

    @Transaction
    fun nukeLocalData() {
        nukeLocalSongs()
        nukeLocalArtists()
        nukeLocalAlbums()
        nukeLocalGenre()
    }

    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw("PRAGMA wal_checkpoint(FULL)".toSQLiteQuery())
    }
}
