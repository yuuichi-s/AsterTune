/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.scanners

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMapNotNull
import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.TreeDocumentFileOt
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.SongItem
import io.github.yuuichi_s.astertune.constants.SCANNER_DEBUG
import io.github.yuuichi_s.astertune.constants.SYNC_SCANNER
import io.github.yuuichi_s.astertune.constants.ScanPathsKey
import io.github.yuuichi_s.astertune.constants.ScannerM3uMatchCriteria
import io.github.yuuichi_s.astertune.constants.ScannerMatchCriteria
import io.github.yuuichi_s.astertune.constants.scannerWhitelistExts
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.AlbumEntity
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.Event
import io.github.yuuichi_s.astertune.db.entities.GenreEntity
import io.github.yuuichi_s.astertune.db.entities.LyricsEntity
import io.github.yuuichi_s.astertune.db.entities.PlayCountEntity
import io.github.yuuichi_s.astertune.db.entities.PlaylistSongMap
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongAlbumMap
import io.github.yuuichi_s.astertune.db.entities.SongArtistMap
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import io.github.yuuichi_s.astertune.db.entities.SongGenreMap
import io.github.yuuichi_s.astertune.models.CulmSongs
import io.github.yuuichi_s.astertune.models.DirectoryTree
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.models.SongTempData
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.utils.closestAlbumMatch
import io.github.yuuichi_s.astertune.utils.closestMatch
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import io.github.yuuichi_s.astertune.utils.lmScannerCoroutine
import io.github.yuuichi_s.astertune.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime

class LocalMediaScanner(context: Context) {
    // Hold the application context to avoid leaking an Activity/Service through the static scanner instance
    val context: Context = context.applicationContext
    private val TAG = LocalMediaScanner::class.simpleName.toString()
    private var advancedScannerImpl: MetadataScanner = TagLibScanner()

    init {
        Log.i(
            TAG,
            "Creating scanner instance with scannerImpl:  ${advancedScannerImpl.javaClass.name}"
        )
    }

    /**
     * Compiles a song with all it's necessary metadata. Unlike MediaStore,
     * this also supports multiple artists, multiple genres (TBD), and a few extra details (TBD).
     */
    suspend fun advancedScan(
        file: File,
    ): SongTempData {
        val path = file.absolutePath
        try {
            if (!file.exists()) throw IOException("File not found")

            return advancedScannerImpl.getAllMetadataFromFile(file)
        } catch (e: Exception) {
            when (e) {
                is IOException, is IllegalArgumentException, is IllegalStateException -> {
                    if (SCANNER_DEBUG) {
                        e.printStackTrace()
                    }
                    throw InvalidAudioFileException("Failed to access file or not in a playable format: ${e.message} for: $path")
                }

                else -> {
                    if (SCANNER_DEBUG) {
                        Log.w(TAG, "ERROR READING METADATA: ${e.message} for: $path")
                        e.printStackTrace()
                    }

                    // we still want the song to be playable even if metadata extractor fails
                    return SongTempData(
                        Song(
                            SongEntity(
                                SongEntity.generateSongId(),
                                path.substringAfterLast('/'),
                                thumbnailUrl = null,
                                isLocal = true,
                                inLibrary = LocalDateTime.now(),
                                localPath = path
                            ),
                            artists = ArrayList()
                        ),
                        null
                    )
                }
            }
        }

    }


    /**
     * Scan the given scan paths for songs given a list of paths to scan for.
     * This will replace all data in the database for a given song.
     *
     * @param scanPaths List of whitelist paths to scan under. This assumes
     * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
     * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun scanLocal(
        scanPaths: String,
        excludedScanPaths: String,
    ): List<Uri> {
        val songs = ArrayList<Uri>()
        Log.i(TAG, "------------ SCAN: Starting Full Scanner ------------")
        scannerState.value = 1
        scannerProgressProbe.value = 0
        scannerProgressTotal.value = 0
        scannerProgressCurrent.value = -1

        val scanPaths = uriListFromString(scanPaths)
        val excludedScanPaths = uriListFromString(excludedScanPaths)

        getScanFiles(scanPaths, excludedScanPaths, context).forEach { uri ->
            if (SCANNER_DEBUG)
                Log.v(TAG, "PATH: $uri")

            songs.add(uri)
        }

        scannerState.value = 0
        Log.i(TAG, "------------ SCAN: Finished Full Scanner ------------")
        return songs.toList()
    }

    /**
     * Synchronizes scanned local songs with [database].
     *
     * New songs are inserted with user data restored from matching [MissingSongArchive] entries.
     * Matched songs keep their database IDs, download timestamps, Like states, and existing
     * non-null library-added timestamps.
     *
     * When [refreshExisting] is false, matched songs are updated as needed to restore local
     * paths or library availability.
     *
     * @param newSongs scanned local songs to synchronize
     * @param foundPaths paths of every local song found by the current scan.
     *     Required unless [noDisable] is true.
     * @param matchStrength how closely a scanned song must match an existing song
     * @param strictFileNames whether file names participate in matching
     * @param strictFilePaths whether file paths must match
     * @param refreshExisting whether to refresh scanned metadata, format, and artist, album,
     *     and genre mappings for matched songs
     * @param noDisable whether to skip duplicate cleanup, missing-song removal through
     *     [removeMissingSongs], and local album metadata refresh
     */
    suspend fun syncDB(
        database: MusicDatabase,
        newSongs: java.util.ArrayList<SongTempData>,
        foundPaths: Set<String>?,
        matchStrength: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
        refreshExisting: Boolean = false,
        noDisable: Boolean = false
    ) {
        if (scannerState.value > 0) {
            Log.i(TAG, "------------ SYNC: Scanner in use. Aborting Local Library Sync ------------")
            return
        }
        Log.i(TAG, "------------ SYNC: Starting Local Library Sync ------------")
        scannerState.value = 3
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0
        // deduplicate
        val finalSongs = ArrayList<SongTempData>()
        if (strictFilePaths) {
            finalSongs.addAll(newSongs)
        } else {
            newSongs.forEach { song ->
                if (finalSongs.none { s -> compareSong(song.song, s.song, matchStrength, strictFileNames) }) {
                    finalSongs.add(song)
                }
            }
        }
        Log.d(TAG, "Entries to process: ${newSongs.size}. After dedup: ${finalSongs.size}")
        scannerProgressTotal.value = finalSongs.size
        val mod = if (newSongs.size < 200) {
            30
        } else if (newSongs.size < 800) {
            70
        } else {
            140
        }

        val allLocalSongs = database.allLocalDbSongs()
        val now = System.currentTimeMillis()
        val restoreCandidates = MissingSongArchive.load(context).filterNot { it.isExpired(now) }.toMutableList()
        // Restore matching songs together to preserve their relative playlist order.
        val songsToRestore = ArrayList<Pair<SongTempData, MissingSongArchive.Entry>>()
        // sync
        var runs = 0
        finalSongs.forEach { song ->
            Log.v(TAG, "s --> ${song.song.song.title}, ${song.song.song.localPath}")
            runs++
            if (SCANNER_DEBUG && runs % mod == 0) {
                Log.d(TAG, "------------ SYNC: Local Library Sync: $runs/${finalSongs.size} processed ------------")
            }
            if (runs % mod == 0) {
                scannerProgressCurrent.value = runs
            }

            if (scannerRequestCancel) {
                if (SCANNER_DEBUG)
                    Log.i(TAG, "WARNING: Requested to cancel Local Library Sync. Aborting.")
                throw ScannerAbortException("Scanner canceled during Local Library Sync")
            }

            // check if this song is known to the library
            val songMatch = allLocalSongs.filter {
                return@filter it.song.title.contains(song.song.title, true) &&
                        compareSong(it, song.song, matchStrength, strictFileNames, strictFilePaths)
            }

            if (SCANNER_DEBUG) {
                Log.v(TAG, "Found songs that match: ${songMatch.size}")
                if (songMatch.isNotEmpty()) {
                    Log.v(TAG, "FIRST Found songs ${songMatch.first().song.title}")
                }
            }


            if (songMatch.isNotEmpty()) { // known song, update the song info in the database
                if (SCANNER_DEBUG)
                    Log.v(TAG, "Found in database, updating song: ${song.song.title} rescan = $refreshExisting")

                val oldSong = songMatch.first().song
                val songToUpdate = song.song.song.copy(
                    id = oldSong.id,
                    localPath = song.song.song.localPath,
                    inLibrary = oldSong.inLibrary ?: song.song.song.inLibrary,
                    dateDownload = oldSong.dateDownload,
                    liked = oldSong.liked,
                    likedDate = oldSong.likedDate,
                )

                // don't run if we will update these values in rescan anyways
                // always ensure inLibrary and local path values are valid
                if (!refreshExisting && (oldSong.inLibrary == null || oldSong.localPath == null)) {
                    database.awaitTransaction {
                        update(songToUpdate)

                        // update format
                        if (song.format != null) {
                            upsert(song.format.copy(id = songToUpdate.id, loudnessDb = formatLoudnessDb(songToUpdate.id)))
                        }
                    }
                }


                if (!refreshExisting) {
                    if (oldSong.localPath != songToUpdate.localPath && oldSong.inLibrary != null) {
                        // always update the path and library path they change
                        database.updateLocalSongPath(songToUpdate.id, songToUpdate.inLibrary, songToUpdate.localPath)
                    }
                    return@forEach
                }
                // below is only for when rescan is enabled

                val artistsToDo = ArrayList<Pair<ArtistEntity?, ArtistEntity>>()
                val genreToDo = ArrayList<Pair<GenreEntity?, GenreEntity>>()
                var albumToDo: Pair<AlbumEntity?, AlbumEntity>? = null

                // update artists and genre
                database.awaitTransaction {
                    // get any existing matches
                    song.song.artists.forEachIndexed { index, it ->
                        val dbQuery = localArtistsByNameFuzzy(it.name).sortedBy { item -> item.name.length }
                        val dbArtist = closestMatch(it.name, dbQuery)
                        artistsToDo.add(Pair(dbArtist, it))
                    }
                    song.song.genre?.forEachIndexed { index, it ->
                        val dbGenre = localGenreByNameFuzzy(it.title).firstOrNull()
                        genreToDo.add(Pair(dbGenre, it))
                    }

                    song.song.album?.let {
                        val dbQuery = localAlbumsByNameFuzzy(it.title).sortedBy { item -> item.title.length }
                        albumToDo = Pair(closestAlbumMatch(it.title, dbQuery), it)
                    }

                    // update song
                    update(
                        songToUpdate.copy(
                            albumId = albumToDo?.first?.id ?: albumToDo?.second?.id,
                            albumName = albumToDo?.first?.title ?: albumToDo?.second?.title
                        )
                    )
                    if (song.format != null) {
                        upsert(song.format.copy(id = songToUpdate.id, loudnessDb = formatLoudnessDb(songToUpdate.id)))
                    }

                    // destroy existing artist links
                    unlinkSongArtists(songToUpdate.id)
                    unlinkSongAlbums(songToUpdate.id)
                    unlinkSongGenres(songToUpdate.id)

                    artistsToDo.forEachIndexed { index, item ->
                        if (item.first == null) {
                            // artist does not exist in db, add it then link it
                            insert(item.second)
                            insert(SongArtistMap(songToUpdate.id, item.second.id, index))
                        } else {
                            // artist does  exist in db, link to it
                            insert(SongArtistMap(songToUpdate.id, item.first!!.id, index))
                        }
                    }

                    genreToDo.forEachIndexed { index, item ->
                        if (item.first == null) {
                            // genre does not exist in db, add it then link it
                            insert(item.second)
                            insert(SongGenreMap(songToUpdate.id, item.second.id, index))
                        } else {
                            // genre does exist in db, link to it
                            insert(SongGenreMap(songToUpdate.id, item.first!!.id, index))
                        }
                    }

                    albumToDo?.let { album ->
                        if (album.first == null) {
                            // album does not exist in db, add it then link it
                            insert(album.second)
                            insert(SongAlbumMap(songToUpdate.id, album.second.id, 0))
                        } else {
                            // album does  exist in db, link to it
                            update(
                                album.first!!.copy(
                                    thumbnailUrl = album.second.thumbnailUrl,
                                    songCount = album.first!!.songCount + 1
                                )
                            )
                            insert(SongAlbumMap(songToUpdate.id, album.first!!.id, album.first!!.songCount))
                        }
                    }
                }
            } else { // new song
                if (SCANNER_DEBUG)
                    Log.v(TAG, "NOT found in database, adding song: ${song.song.title}")

                val archived = restoreCandidates.firstOrNull {
                    val match = it.toMatchSong()
                    match.song.title.contains(song.song.title, true) &&
                            compareSong(match, song.song, matchStrength, strictFileNames, strictFilePaths)
                }
                if (archived != null) {
                    restoreCandidates.remove(archived)
                    songsToRestore.add(song to archived)
                    return@forEach
                }

                database.awaitTransaction {
                    insertLocalSong(song.song.toMediaMetadata())
                    song.format?.let {
                        upsert(it.copy(id = song.song.id))
                    }
                }
            }
        }

        if (songsToRestore.isNotEmpty()) {
            restoreArchivedSongs(songsToRestore, database)
        }

        scannerProgressCurrent.value = scannerProgressTotal.value
        if (!noDisable) {
            reportUnmergedGroups(finalize(database))
            removeMissingSongs(requireNotNull(foundPaths), database, matchStrength, strictFileNames, strictFilePaths)
            database.awaitTransaction { refreshLocalAlbums() }
        }
        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished Local Library Sync ------------")
    }

    /**
     * Scans local files whose paths are not already in the library.
     *
     * [newSongs] must contain every song URI found by the current scan.
     * Once scan locations have been saved, local songs whose paths are absent from the input
     * are removed (see [removeMissingSongs]).
     * If a URI cannot be resolved to a file path, this function throws before updating the
     * database.
     *
     * After scanning, this function:
     *
     * - inserts new songs and restores matching archived user data
     * - removes duplicate song records and merges duplicate local artists, albums, and genres
     * - recalculates local album song counts, durations, artists, and artwork
     *
     * Cleanup still runs when no new valid song is found. No remote artist lookup is performed.
     *
     * The cached directory tree is not refreshed, so callers displaying it should rebuild it after
     * the scan.
     *
     * @param newSongs complete set of local-song URIs found by the current scan
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun quickSync(
        database: MusicDatabase,
        newSongs: List<Uri>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
    ) {
        Log.i(TAG, "------------ SYNC: Starting Quick (additive delta) Library Sync ------------")
        Log.d(TAG, "Entries to process: ${newSongs.size}")
        scannerState.value = 2
        scannerProgressTotal.value = newSongs.size
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0

        Log.d(TAG, "Scanning for files...")
        // get list of all songs in db, then get songs unknown to the database
        // TODO: duplicate songs with different paths will cycle through paths, causing it to be synced instead of ignored...
        val allSongs = database.allLocalSongs().fastMapNotNull { it.song.localPath }.toSet()
        val converted = newSongs.map { uri ->
            fileFromUri(context, uri)?.absolutePath
                ?: throw IOException("Could not resolve local file path: $uri")
        }
        val delta = converted.minus(allSongs)
        Log.d(TAG, "Songs found: ${delta.size}")
        val mod = if (newSongs.size < 20) {
            2
        } else if (newSongs.size < 50) {
            8
        } else {
            20
        }

        val finalSongs = ArrayList<SongTempData>()
        val scannerJobs = ArrayList<Deferred<SongTempData?>>()

        // Get song basic metadata
        delta.forEach { s ->
            if (scannerRequestCancel) {
                Log.i(TAG, "WARNING: Requested to cancel. Aborting.")
                throw ScannerAbortException("Scanner canceled during Quick (additive delta) Library Sync")
            }

            if (SCANNER_DEBUG) {
                Log.v(TAG, "PATH: $s")
            }

            if (!SYNC_SCANNER) {
                // use async scanner
                runBlocking {
                    scannerJobs.add(
                        async(lmScannerCoroutine) {
                            var ret: SongTempData?
                            if (scannerRequestCancel) {
                                Log.i(TAG, "WARNING: Canceling advanced scanner job.")
                                throw ScannerAbortException("")
                            }
                            try {
                                ret = advancedScan(File(s))
                                scannerProgressProbe.value++
                                if (SCANNER_DEBUG && scannerProgressProbe.value % mod == 0) {
                                    Log.d(
                                        TAG,
                                        "------------ SCAN: Full Scanner: ${scannerProgressProbe.value} discovered ------------"
                                    )
                                }
                                if (scannerProgressProbe.value % mod == 0) {
                                    scannerProgressCurrent.value = scannerProgressProbe.value
                                }
                            } catch (e: InvalidAudioFileException) {
                                ret = null
                            }
                            ret
                        }
                    )
                }
            } else {
                if (scannerRequestCancel) {
                    Log.i(TAG, "WARNING: Requested to cancel. Aborting.")
                    throw ScannerAbortException("Scanner canceled during Quick (additive delta) Library Sync")
                }
                // force synchronous scanning of songs. Do not catch errors
                finalSongs.add(advancedScan(File(s)))
                scannerProgressProbe.value++
                if (SCANNER_DEBUG && scannerProgressProbe.value % 5 == 0) {
                    Log.d(
                        TAG,
                        "------------ SCAN: Full Scanner: ${scannerProgressProbe.value} discovered ------------"
                    )
                }
                if (scannerProgressProbe.value % 5 == 0) {
                    scannerProgressCurrent.value = scannerProgressProbe.value
                }
            }
        }

        if (!SYNC_SCANNER) {
            // use async scanner
            scannerJobs.awaitAll()
        }

        // add to finished list
        scannerJobs.forEach {
            val song = it.getCompleted()
            song?.song?.let { finalSongs.add(song) }
        }

        if (finalSongs.isNotEmpty()) {
            scannerState.value = 0
            syncDB(database, finalSongs, null, matchCriteria, strictFileNames, strictFilePaths, noDisable = true)
            scannerState.value = 2
        } else {
            Log.i(TAG, "Not syncing, no valid songs found!")
        }

        scannerProgressCurrent.value = scannerProgressProbe.value
        // syncDB skips this step, so missing songs are removed here
        scannerState.value = 3
        removeMissingSongs(converted.toSet(), database, matchCriteria, strictFileNames, strictFilePaths)
        reportUnmergedGroups(finalize(database))
        database.awaitTransaction { refreshLocalAlbums() }

        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished Quick (additive delta) Library Sync ------------")
    }


    /**
     * Rescans all local songs found by the current scan and synchronizes them with [database].
     *
     * [newSongs] must contain every song URI found by the current scan.
     * Once scan locations have been saved, local songs whose paths are absent from the input
     * are removed. Cleanup also runs for empty input.
     * Existing songs whose files are found are kept even if their metadata cannot be read.
     * If a URI cannot be resolved to a file path, this function throws before updating the
     * database.
     *
     * For matched songs, the scan refreshes metadata, format, and artist, album, and genre
     * relationships while preserving:
     *
     * - database IDs
     * - download timestamps
     * - Like states
     * - existing non-null library-added timestamps
     *
     * New songs are inserted and matching archived user data is restored.
     * Duplicate local rows are consolidated. Local album song counts, durations, artists,
     * and artwork are recalculated.
     *
     * No remote artist lookup is performed. The cached directory tree is not refreshed, so
     * callers displaying it should rebuild it after the scan.
     *
     * @param newSongs complete set of local-song URIs found by the current scan
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fullSync(
        database: MusicDatabase,
        newSongs: List<Uri>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
    ) {
        Log.i(TAG, "------------ SYNC: Starting FULL Library Sync ------------")
        Log.d(TAG, "Entries to process: ${newSongs.size}")
        scannerState.value = 2
        scannerProgressTotal.value = newSongs.size
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0
        val files = newSongs.map { uri ->
            fileFromUri(context, uri) ?: throw IOException("Could not resolve local file path: $uri")
        }
        val foundPaths = files.mapTo(HashSet()) { it.absolutePath }
        val mod = if (newSongs.size < 20) {
            2
        } else if (newSongs.size < 50) {
            8
        } else {
            20
        }

        val finalSongs = ArrayList<SongTempData>()
        val scannerJobs = ArrayList<Deferred<SongTempData?>>()

        // Get song basic metadata
        files.forEach { file ->
            if (scannerRequestCancel) {
                Log.i(TAG, "WARNING: Requested to cancel. Aborting.")
                throw ScannerAbortException("Scanner canceled during FULL Library Sync")
            }

            if (SCANNER_DEBUG)
                Log.d(TAG, "PATH: $file")

            if (!SYNC_SCANNER) {
                // use async scanner
                runBlocking {
                    scannerJobs.add(
                        async(lmScannerCoroutine) {
                            if (scannerRequestCancel) {
                                Log.i(TAG, "WARNING: Canceling advanced scanner job.")
                                throw ScannerAbortException("")
                            }
                            try {
                                val ret = advancedScan(file)
                                scannerProgressProbe.value++
                                if (SCANNER_DEBUG && scannerProgressProbe.value % mod == 0) {
                                    Log.d(
                                        TAG,
                                        "------------ SCAN: Full Scanner: ${scannerProgressProbe.value} discovered ------------"
                                    )
                                }
                                if (scannerProgressProbe.value % mod == 0) {
                                    scannerProgressCurrent.value = scannerProgressProbe.value
                                }
                                ret
                            } catch (e: InvalidAudioFileException) {
                                null
                            }
                        }
                    )
                }
            } else {
                // force synchronous scanning of songs. Do not catch errors
                finalSongs.add(advancedScan(file))
            }
        }

        if (!SYNC_SCANNER) {
            // use async scanner
            scannerJobs.awaitAll()
        }

        // add to finished list
        scannerJobs.forEach {
            val song = it.getCompleted()
            song?.song?.let { finalSongs.add(song) }
        }

        scannerProgressCurrent.value = scannerProgressProbe.value
        /**
         * TODO: Delete all local format entity before scan
         */
        scannerState.value = 0
        syncDB(database, finalSongs, foundPaths, matchCriteria, strictFileNames, strictFilePaths, refreshExisting = true)
        scannerState.value = 2

        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished FULL Library Sync ------------")
    }

    /**
     * Deletes local songs whose paths are absent from [foundPaths], including songs with no path.
     * Does nothing until the scan location setting has been saved at least once.
     *
     * Archives a song's user data in [MissingSongArchive] if it has at least
     * [ARCHIVE_MIN_PLAY_COUNT] plays, is liked, or belongs to a playlist.
     * Existing matching archive entries are replaced.
     *
     * Reads user data and deletes songs in one database transaction. The archive is saved first.
     * A failed archive write prevents deletion. The archive file is not rolled back with the
     * database. Entries left after a rollback are ignored while their songs remain in the database.
     *
     * Removes unbookmarked local artists, albums, and genres that have no songs.
     * Discards archive entries that have reached [MissingSongArchive.RETENTION_MILLIS].
     *
     * @param foundPaths file paths of every local song found by the current scan
     * @throws ScannerAbortException if the scan was canceled before anything is deleted
     */
    private suspend fun removeMissingSongs(
        foundPaths: Set<String>,
        database: MusicDatabase,
        matchStrength: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
    ) {
        if (context.dataStore[ScanPathsKey] == null) {
            Log.i(TAG, "Scan locations were never saved. Skipping removeMissingSongs")
            return
        }
        Log.i(TAG, "Start finalize (removeMissingSongs) job")
        val now = System.currentTimeMillis()
        MissingSongArchive.lock.withLock {
            database.awaitTransaction {
                // Checked here so a cancel made while waiting for the transaction is still seen.
                if (scannerRequestCancel) {
                    throw ScannerAbortException("Scanner canceled before removing missing songs")
                }
                val missingSongs = allLocalDbSongs().filter { song ->
                    song.song.localPath.let { it == null || it !in foundPaths }
                }
                Log.i(TAG, "Missing songs: ${missingSongs.size}")

                val stored = MissingSongArchive.load(context)
                val retained = stored.filterNot { it.isExpired(now) }.toMutableList()
                var archiveChanged = retained.size != stored.size

                // Capture original playlist positions before deletions shift them.
                val playlistMaps = missingSongs.associate { it.song.id to songMapsToPlaylist(it.song.id) }
                for (song in missingSongs) {
                    val id = song.song.id
                    val playCounts = playCountsBySong(id)
                    val maps = playlistMaps.getValue(id)
                    val keep = playCounts.sumOf { it.count } >= ARCHIVE_MIN_PLAY_COUNT ||
                            song.song.liked || maps.isNotEmpty()
                    if (!keep) {
                        continue
                    }

                    val entry = MissingSongArchive.Entry(
                        title = song.song.title,
                        artists = song.artists.map { it.name },
                        album = song.album?.title,
                        localPath = song.song.localPath,
                        archivedAt = now,
                        liked = song.song.liked,
                        likedDate = song.song.likedDate,
                        lyrics = lyricsById(id)?.let {
                            MissingSongArchive.Lyrics(it.lyrics, it.provider, it.lastCheckedAt, it.providerSignature)
                        },
                        playCounts = playCounts.map { MissingSongArchive.PlayCount(it.year, it.month, it.count) },
                        events = eventsBySong(id).map { MissingSongArchive.PlayEvent(it.timestamp, it.playTime) },
                        playlists = maps.map {
                            MissingSongArchive.PlaylistEntry(it.playlistId, it.position, it.setVideoId)
                        },
                    )
                    retained.removeAll {
                        compareSong(it.toMatchSong(), song, matchStrength, strictFileNames, strictFilePaths)
                    }
                    retained.add(entry)
                    archiveChanged = true
                }
                if (archiveChanged) {
                    MissingSongArchive.save(context, retained)
                }

                for (song in missingSongs) {
                    val id = song.song.id
                    if (SCANNER_DEBUG)
                        Log.v(TAG, "Removing song $id (${song.song.localPath})")

                    // Remove playlist entries one at a time so the positions after each stay contiguous.
                    while (true) {
                        val map = songMapsToPlaylist(id).firstOrNull() ?: break
                        move(map.playlistId, map.position, Int.MAX_VALUE)
                        delete(map.copy(position = Int.MAX_VALUE))
                    }
                    // These tables have no foreign key to the song, so delete their rows explicitly.
                    deletePlayCounts(id)
                    deleteLyricById(id)
                    deleteFormatById(id)
                    delete(song.song)
                }
                if (missingSongs.isNotEmpty()) {
                    deleteUnbookmarkedLocalArtistsWithoutSongs()
                    deleteUnbookmarkedLocalAlbumsWithoutSongs()
                    deleteUnbookmarkedLocalGenresWithoutSongs()
                }
            }
        }
        Log.i(TAG, "Finished (removeMissingSongs) job")
    }

    /**
     * Inserts the scanned songs in [songs] and restores their archived user data in one
     * transaction.
     *
     * Rechecks matched entries under [MissingSongArchive.lock] so intervening changes, such as
     * clearing the listen history, are preserved. Songs whose entries no longer exist are inserted
     * without archived data.
     *
     * Restores playlist entries in archived position order, shifting later entries. Positions
     * beyond the current playlist length are appended, and deleted playlists are skipped.
     *
     * Removes restored archive entries after the transaction commits. If that update fails,
     * the entries remain unused while the restored songs are still in the database.
     */
    private suspend fun restoreArchivedSongs(
        songs: List<Pair<SongTempData, MissingSongArchive.Entry>>,
        database: MusicDatabase,
    ) {
        MissingSongArchive.lock.withLock {
            val stored = MissingSongArchive.load(context)
            val restored = songs.mapNotNull { (song, entry) ->
                stored.firstOrNull { it.isSameEntryAs(entry) }?.let { song to it }
            }
            database.awaitTransaction {
                songs.forEach { (song, _) ->
                    insertLocalSong(song.song.toMediaMetadata())
                    song.format?.let {
                        upsert(it.copy(id = song.song.id))
                    }
                }
                restored.forEach { (song, entry) ->
                    val songId = song.song.id
                    if (SCANNER_DEBUG)
                        Log.v(TAG, "Restoring archived data for ${entry.title} to $songId")

                    if (entry.liked) {
                        setLike(songId, true, entry.likedDate)
                    }
                    entry.lyrics?.let {
                        upsert(LyricsEntity(songId, it.lyrics, it.provider, it.lastCheckedAt, it.providerSignature))
                    }
                    entry.playCounts.forEach {
                        insert(PlayCountEntity(songId, it.year, it.month, it.count))
                    }
                    entry.events.forEach {
                        insert(Event(songId = songId, timestamp = it.timestamp, playTime = it.playTime))
                    }
                }
                restored.flatMap { (song, entry) -> entry.playlists.map { song.song.id to it } }
                    .groupBy { it.second.playlistId }
                    .forEach { (playlistId, items) ->
                        if (!playlistExists(playlistId)) return@forEach

                        items.sortedBy { it.second.position }.forEach { (songId, item) ->
                            val position = minOf(item.position, playlistSongCount(playlistId))
                            shiftPlaylistPositions(playlistId, position)
                            insert(
                                PlaylistSongMap(
                                    playlistId = playlistId,
                                    songId = songId,
                                    position = position,
                                    setVideoId = item.setVideoId
                                )
                            )
                        }
                    }
            }

            if (restored.isEmpty()) return@withLock

            val restoredEntries = restored.map { it.second }
            try {
                MissingSongArchive.save(context, stored.filterNot { it in restoredEntries })
            } catch (e: IOException) {
                Log.e(TAG, "Could not remove restored entries from the missing song archive", e)
            }
        }
    }

    /**
     * Remove duplicate songs from the library and merge local artists, albums and genres that
     * share a name.
     *
     * @return the number of duplicate groups that could not be merged
     */
    private suspend fun finalize(database: MusicDatabase): Int {
        Log.i(TAG, "Start finalize (database cleanup job)")

        // remove duplicates
        val dupes = database.duplicatedLocalSongs().toMutableList()
        var index = 0

        Log.d(TAG, "Start finalize (duplicate removal) job. Number of candidates: ${dupes.size}")
        while (index < dupes.size) {
            // collect all the duplicates
            val contenders = ArrayList<Pair<SongEntity, Int>>()
            val localPath = dupes[index].localPath
            while (index < dupes.size && dupes[index].localPath == localPath) {
                contenders.add(Pair(dupes[index], database.getLifetimePlayCount(dupes[index].id)))
                index++
            }
            // yeet the lower play count songs
            contenders.remove(contenders.maxByOrNull { it.second })
            contenders.forEach {
                if (SCANNER_DEBUG)
                    Log.v(TAG, "Deleting song ${it.first.id} (${it.first.title})")
                database.delete(it.first)
            }
        }

        var failedGroups = 0

        failedGroups += mergeDuplicates(
            kind = "artists",
            rows = database.allLocalArtists(),
            id = { it.id },
            name = { it.name },
            bookmarkedAt = { it.bookmarkedAt }
        ) { survivor, duplicates ->
            database.awaitTransaction {
                val target = artistById(survivor.id) ?: return@awaitTransaction false
                val merged = duplicates.mapNotNull { artistById(it.id) }
                merged.forEach { old ->
                    updateSongArtistMap(old.id, target.id)
                    updateAlbumArtistMap(old.id, target.id)
                    deleteSongArtistMap(old.id)
                    deleteAlbumArtistMap(old.id)
                    safeDeleteArtist(old.id)
                }
                mergedArtistUpdate(target, merged)?.let { update(it) }
                true
            }
        }

        failedGroups += mergeDuplicates(
            kind = "albums",
            rows = database.allLocalAlbums(),
            id = { it.id },
            name = { it.title },
            bookmarkedAt = { it.bookmarkedAt }
        ) { survivor, duplicates ->
            database.awaitTransaction {
                val target = albumById(survivor.id) ?: return@awaitTransaction false
                val merged = duplicates.mapNotNull { albumById(it.id) }
                merged.forEach { old ->
                    updateSongAlbumMap(old.id, target.id)
                    updateSongAlbumId(old.id, target.id)
                    deleteSongAlbumMap(old.id)
                    safeDeleteAlbum(old.id)
                }
                val bookmarkedAt = (merged + target).mapNotNull { it.bookmarkedAt }.minOrNull()
                if (bookmarkedAt != target.bookmarkedAt) {
                    update(target.copy(bookmarkedAt = bookmarkedAt))
                }
                true
            }
        }

        failedGroups += mergeDuplicates(
            kind = "genres",
            rows = database.allLocalGenres(),
            id = { it.id },
            name = { it.title },
            bookmarkedAt = { it.bookmarkedAt }
        ) { survivor, duplicates ->
            database.awaitTransaction {
                val target = genreById(survivor.id) ?: return@awaitTransaction false
                val merged = duplicates.mapNotNull { genreById(it.id) }
                merged.forEach { old ->
                    updateSongGenreMap(old.id, target.id)
                    deleteSongGenreMap(old.id)
                    safeDeleteGenre(old.id)
                }
                val bookmarkedAt = (merged + target).mapNotNull { it.bookmarkedAt }.minOrNull()
                if (bookmarkedAt != target.bookmarkedAt) {
                    updateGenreBookmark(target.id, bookmarkedAt)
                }
                true
            }
        }

        database.awaitTransaction { fixLocalSongAlbumIdMismatches() }

        Log.i(TAG, "Finished finalize (duplicate removal) job")
        return failedGroups
    }

    private fun reportUnmergedGroups(count: Int) {
        if (count > 0) {
            Log.w(TAG, "$count group(s) of duplicates were left in the library")
        }
    }

    /**
     * Merges rows whose names match when case is ignored.
     *
     * In each duplicate group, the row with the earliest bookmark timestamp survives. If none is
     * bookmarked, the first row in the group survives. [merge] receives the surviving row and the
     * remaining rows so it can update their mappings and remove the duplicates.
     *
     * Each group is passed to [merge] separately. A false result or a non-cancellation exception is
     * counted as one failed group. Cancellation is rethrown; other exceptions are logged, and
     * processing continues with the next group.
     *
     * @param rows candidate rows in insertion order
     * @return number of groups that were not merged
     */
    private suspend fun <T> mergeDuplicates(
        kind: String,
        rows: List<T>,
        id: (T) -> String,
        name: (T) -> String,
        bookmarkedAt: (T) -> LocalDateTime?,
        merge: suspend (survivor: T, duplicates: List<T>) -> Boolean,
    ): Int {
        val groups = rows.groupBy { name(it).lowercase() }.values.filter { it.size > 1 }
        Log.d(TAG, "Start finalize (duplicate $kind) job. Number of groups: ${groups.size}")

        var merged = 0
        var failed = 0
        groups.forEach { group ->
            val survivor = group.filter { bookmarkedAt(it) != null }.minByOrNull { bookmarkedAt(it)!! }
                ?: group.first()
            val duplicates = group.filterNot { it === survivor }
            val groupName = name(survivor).lineSequence().joinToString(" ")
            try {
                if (merge(survivor, duplicates)) {
                    merged++
                } else {
                    failed++
                    Log.w(
                        TAG,
                        "Duplicate merge skipped: kind=$kind, name=$groupName, rows=${group.size}, " +
                                "survivorId=${id(survivor)}, duplicateIds=${duplicates.joinToString { id(it) }}, " +
                                "reason=survivor missing"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                val exception = "${e.javaClass.simpleName}: ${e.message}".lineSequence().joinToString(" ")
                Log.e(
                    TAG,
                    "Duplicate merge failed: kind=$kind, name=$groupName, rows=${group.size}, " +
                            "survivorId=${id(survivor)}, duplicateIds=${duplicates.joinToString { id(it) }}, " +
                            "exception=$exception"
                )
                reportException(e)
            }
        }
        Log.i(TAG, "Duplicate merge result: kind=$kind, groups=${groups.size}, merged=$merged, failed=$failed")
        return failed
    }


    companion object {
        // do not put any thing that should adhere to the scanner lock in here
        const val TAG = "LocalMediaScanner"

        /** Minimum lifetime play count for a missing song to be archived without a like or playlist. */
        const val ARCHIVE_MIN_PLAY_COUNT = 10

        private var ownerId = -1
        private var localScanner: LocalMediaScanner? = null


        var scannerRequestCancel = false

        /**
         * -1: Inactive
         * 0: Idle
         * 1: Discovering (Crawling files)
         * 2: Scanning (Extract metadata and checking playability
         * 3: Syncing (Update database)
         * 4: Scan finished
         * 5: Ytm artist linking
         */
        var scannerState = MutableStateFlow(-1)
        var scannerProgressTotal = MutableStateFlow(-1)
        var scannerProgressCurrent = MutableStateFlow(-1)
        var scannerProgressProbe = MutableStateFlow(-1)


        /**
         * ==========================
         * Scanner management
         * ==========================
         */

        /**
         * Trust me bro, it should never be null
         */
        fun getScanner(context: Context, owner: Int): LocalMediaScanner {

            if (localScanner == null) {
                localScanner = LocalMediaScanner(context)
                scannerProgressTotal.value = 0
                scannerProgressCurrent.value = -1
                scannerProgressProbe.value = 0
            }

            ownerId = owner
            return localScanner!!
        }

        suspend fun destroyScanner(owner: Int) {
            if (owner != ownerId && ownerId != -1) {
                Log.w(TAG, "Scanner instance can only be destroyed by the owner. Aborting. Check your ownerId.")
                return
            }
            ownerId = -1
            localScanner = null
            scannerState.value = -1
            scannerRequestCancel = false
            scannerProgressTotal.value = -1
            scannerProgressCurrent.value = -1
            scannerProgressProbe.value = -1

            Log.i(TAG, "Scanner instance destroyed")
        }


        /**
         * ==========================
         * Scanner extra scan utils
         * ==========================
         */


        /**
         * Lists files to scan, applying exclusions to the selected folders and their descendants.
         * Exclusions take precedence over inclusions.
         *
         * Input URIs are expected to have non-null paths.
         */
        fun getScanFiles(scanPaths: List<Uri>, excludedScanPaths: List<Uri>, context: Context): List<Uri> {
            val allSongs = ArrayList<Uri>()
            val resultingPaths =
                scanPaths.filterNot { incl ->
                    excludedScanPaths.any { excl -> isSameOrInsideFolder(incl.path, excl.path.toString()) }
                }

            resultingPaths.forEach { path ->
                try {
                    val file = documentFileFromUri(context, path)
                    if (file != null) {
                        val songsHere = ArrayList<DocumentFile>()
                        scanDfRecursive(file, songsHere, failOnListingError = true) {
                            // Allow: audio mime, or certain audio exts
                            // Disallow: x-mpegurl (m3u)
                            val mime = it.type ?: return@scanDfRecursive false
                            if (!mime.startsWith("audio")) {
                                if (it.name?.substringAfterLast('.') !in scannerWhitelistExts) {
                                    return@scanDfRecursive false
                                }
                            }
                            if (mime == "audio/x-mpegurl") {
                                return@scanDfRecursive false
                            }

                            return@scanDfRecursive true
                        }

                        allSongs.addAll(songsHere.fastFilter { incl ->
                            !excludedScanPaths.any { isSameOrInsideFolder(incl.uri.path, it.path.toString()) }
                        }.map { it.uri })
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    throw Exception("oh well idk man this should never happen")
                }
            }

            return allSongs.fastDistinctBy { it.toString() }
        }

        /**
         * Tests whether the tree URI path [path] is [folder] or lies within it.
         *
         * With [folder] set to `/tree/primary:Music`:
         *
         * - `/tree/primary:Music/Live` matches.
         * - `/tree/primary:Music2` does not match.
         *
         * A trailing `:` in [folder] indicates a storage volume root.
         */
        private fun isSameOrInsideFolder(path: String?, folder: String): Boolean {
            if (path == null) return false
            if (path == folder) return true

            val prefix = if (folder.endsWith(':') || folder.endsWith('/')) folder else "$folder/"
            return path.startsWith(prefix)
        }

        fun scanDfRecursive(
            dir: DocumentFile,
            result: ArrayList<DocumentFile>,
            scanHidden: Boolean = false,
            failOnListingError: Boolean = false,
            validator: ((DocumentFile) -> Boolean)? = null
        ): DocumentFile? {
            val files = listFiles(dir, failOnListingError)
            for (file in files) {
                if (!scanHidden && file.name?.startsWith(".") == true) continue
                if (
                    isDirectory(file) &&
                    (scanHidden || !listFiles(file, failOnListingError).any { it.name == ".nomedia" })
                ) {
                    // look into subdirs
                    scanDfRecursive(file, result, scanHidden, failOnListingError, validator)
                } else {
                    // add if file matches
                    if (validator == null || validator(file)) {
                        result.add(file)
                        scannerProgressProbe.value++
                        if (scannerProgressProbe.value % 20 == 0) {
                            scannerProgressTotal.value = scannerProgressProbe.value
                        }
                    }
                }
            }
            return null
        }

        /**
         * Whether [file] is a directory. For a [TreeDocumentFileOt] from a directory listing, the
         * MIME type read with the listing is used, because querying it again can fail and would
         * then report a directory as a file, leaving its songs unscanned.
         */
        private fun isDirectory(file: DocumentFile): Boolean {
            // TreeDocumentFileOt.getType() preserves the cached directory MIME type.
            // Without a cached type, it queries the provider and returns null for directories.
            val listedType = if (file is TreeDocumentFileOt) file.type else null

            return if (listedType != null) {
                listedType == DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                file.isDirectory
            }
        }

        private fun listFiles(dir: DocumentFile, failOnListingError: Boolean): Array<DocumentFile> {
            return if (failOnListingError) {
                (dir as TreeDocumentFileOt).listFilesOrThrow()
            } else {
                dir.listFiles()
            }
        }

        /**
         * Rebuilds a skeleton directory tree from local song records in the database.
         *
         * Does not inspect files or refresh metadata. A separate scan is required to reflect file
         * moves or metadata changes in the database before rebuilding the tree.
         *
         * @param filter Raw directory path used to select database records and root the tree
         */
        suspend fun refreshLocal(
            database: MusicDatabase,
            filter: String
        ): DirectoryTree {
            val newDirectoryStructure = DirectoryTree(filter.trimEnd { it == '/' }, CulmSongs(0))

            // get songs from db
            val existingSongs: List<Song> = database.localSongsInDirShallow(filter)

            Log.i(TAG, "------------ SCAN: Starting Quick Directory Rebuild ------------")

            // Build directory tree with existing files
            existingSongs.forEach { s ->
                val path = s.song.localPath ?: return@forEach
                val filterPath =
                    (if (path.startsWith(filter)) path.substringAfter(filter) else path).trimStart { it == '/' }
                newDirectoryStructure.insert(filterPath, s)
            }

            Log.i(TAG, "------------ SCAN: Finished Quick Directory Rebuild ------------")
            return newDirectoryStructure.androidStorageWorkaround()
        }


        /**
         * ==========================
         * Scanner helpers
         * ==========================
         */


        /**
         * Check if artists are the same
         *
         *  Both null == same artists
         *  Either null == different artists
         */
        fun compareArtist(a: List<ArtistEntity>, b: List<ArtistEntity>): Boolean {
            if (a.isEmpty() && b.isEmpty()) {
                return true
            } else if (a.isEmpty() || b.isEmpty()) {
                return false
            }

            // compare entries
            if (a.size != b.size) {
                return false
            }
            val matchingArtists = a.filter { artist ->
                b.any { it.name.equals(artist.name, false) }
            }

            return matchingArtists.size == a.size
        }

        /**
         * Check if albums are the same
         *
         *  Both null == same albums
         *  Either null == different albums
         */
        fun compareAlbum(a: AlbumEntity?, b: AlbumEntity?): Boolean {
            if (a == null && b == null) return true
            if (a == null || b == null) return false

            return a.title.equals(b.title, false)
        }

        /**
         * Check the similarity of a song
         *
         * @param a
         * @param b
         * @param matchStrength How lax should the scanner be
         */
        fun compareM3uSong(
            a: Song,
            b: Song,
            matchStrength: ScannerM3uMatchCriteria = ScannerM3uMatchCriteria.LEVEL_1,
        ): Boolean {
            if (matchStrength != ScannerM3uMatchCriteria.LEVEL_2) {
                return compareSong(a, b, ScannerMatchCriteria.LEVEL_1)
            }
            val path = a.song.localPath
            if (!path.isNullOrBlank() && path == b.song.localPath) {
                return true
            }
            return a.song.title == b.song.title && compareArtist(a.artists, b.artists)
        }

        /**
         * Tests whether [a] and [b] match under the local scanner criteria.
         *
         * @param a first song to compare
         * @param b second song to compare
         * @param matchStrength metadata matching level. Levels 2 and 3 also accept equal nonblank paths.
         * @param strictFileNames whether to reject differing file names. Ignored when [strictFilePaths] is true.
         * @param strictFilePaths whether to match only equal nonblank paths, bypassing other criteria
         */
        fun compareSong(
            a: Song,
            b: Song,
            matchStrength: ScannerMatchCriteria = ScannerMatchCriteria.LEVEL_2,
            strictFileNames: Boolean = false,
            strictFilePaths: Boolean = false,
        ): Boolean {
            fun closeEnough(): Boolean {
                return !a.song.localPath.isNullOrBlank() && a.song.localPath == b.song.localPath
            }
            if (strictFilePaths) {
                return closeEnough()
            }
            // if match file names
            if (strictFileNames &&
                (a.song.localPath?.substringAfterLast('/') !=
                        b.song.localPath?.substringAfterLast('/'))
            ) {
                return false
            }

            // compare songs based on scanner strength
            return when (matchStrength) {
                ScannerMatchCriteria.LEVEL_1 -> a.song.title == b.song.title
                ScannerMatchCriteria.LEVEL_2 -> closeEnough() || (a.song.title == b.song.title &&
                        compareArtist(a.artists, b.artists))

                ScannerMatchCriteria.LEVEL_3 -> closeEnough() || (a.song.title == b.song.title &&
                        compareArtist(a.artists, b.artists) && compareAlbum(a.album, b.album))
            }
        }

        /**
         * Looks up [videoId] on YouTube Music and falls back to a song search with [query].
         */
        suspend fun youtubeSongLookup(query: String, videoId: String?): List<MediaMetadata> {
            val ytmResult = ArrayList<MediaMetadata>()

            val exactSong = videoId?.let { id ->
                YouTube.queue(listOf(id)).getOrNull()?.firstOrNull { it.id == id }
            }

            // prefer song from url
            if (exactSong != null) {
                ytmResult.add(exactSong.toMediaMetadata())
                if (SCANNER_DEBUG)
                    Log.v(TAG, "Found exact song: ${exactSong.title} [${exactSong.id}]")
                return ytmResult
            }
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { result ->

                val foundSong = result.items.filter {
                    // TODO: might want to implement proper matching to remove outlandish results
                    it is SongItem
                }
                ytmResult.addAll(foundSong.map { (it as SongItem).toMediaMetadata() })

                if (SCANNER_DEBUG)
                    Log.v(TAG, "Remote song: ${foundSong.firstOrNull()?.title} [${foundSong.firstOrNull()?.id}]")
            }.onFailure {
                throw Exception("Failed to search on YouTube Music: ${it.message}")
            }

            return ytmResult
        }

        /**
         * Search for an artist on YouTube Music.
         *
         * If no artist is found, create one locally
         */
        suspend fun youtubeArtistLookup(query: String): ArtistEntity? {
            var ytmResult: ArtistEntity? = null

            // hit up YouTube for artist
            YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).onSuccess { result ->

                val foundArtist = result.items.filter { it is ArtistItem }.firstOrNull {
                    // TODO: might want to implement smarter matching
                    it.title.equals(query, true)
                } as ArtistItem? ?: throw Exception("Failed to search: Artist not found on YouTube Music")
                ytmResult = ArtistEntity(
                    foundArtist.id,
                    foundArtist.title,
                    foundArtist.thumbnail,
                    foundArtist.channelId
                )

                if (SCANNER_DEBUG)
                    Log.v(TAG, "Found remote artist:  ${foundArtist.title} [${foundArtist.id}]")
            }.onFailure {
                throw Exception("Failed to search on YouTube Music")
            }

            return ytmResult
        }
    }
}

/**
 * The survivor [target] after merging [merged] duplicates into it, or null when it does not change.
 * Keeps the earliest bookmark time, and takes an artist image from the duplicates when [target] has none.
 */
internal fun mergedArtistUpdate(target: ArtistEntity, merged: List<ArtistEntity>): ArtistEntity? {
    val bookmarkedAt = (merged + target).mapNotNull { it.bookmarkedAt }.minOrNull()
    val thumbnailUrl = target.thumbnailUrl ?: merged.firstNotNullOfOrNull { it.thumbnailUrl }
    return if (bookmarkedAt != target.bookmarkedAt || thumbnailUrl != target.thumbnailUrl) {
        target.copy(bookmarkedAt = bookmarkedAt, thumbnailUrl = thumbnailUrl)
    } else {
        null
    }
}

class InvalidAudioFileException(message: String) : Throwable(message)
class ScannerAbortException(message: String) : Throwable(message)
class ScannerCriticalFailureException(message: String) : Throwable(message)
