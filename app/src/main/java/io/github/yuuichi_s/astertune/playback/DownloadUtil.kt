/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.playback

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.util.NotificationUtil
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.zionhuang.innertube.YouTube
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.AudioQuality
import io.github.yuuichi_s.astertune.constants.AudioQualityKey
import io.github.yuuichi_s.astertune.constants.DOWNLOAD_DEBUG
import io.github.yuuichi_s.astertune.constants.DownloadExtraPathKey
import io.github.yuuichi_s.astertune.constants.DownloadOnWifiOnlyKey
import io.github.yuuichi_s.astertune.constants.DownloadPathKey
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.FormatEntity
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import io.github.yuuichi_s.astertune.di.AppModule.PlayerCache
import io.github.yuuichi_s.astertune.di.DownloadCache
import io.github.yuuichi_s.astertune.models.MediaMetadata
import io.github.yuuichi_s.astertune.playback.DownloadUtil.Companion.STATE_DOWNLOADING
import io.github.yuuichi_s.astertune.playback.DownloadUtil.Companion.STATE_INVALID
import io.github.yuuichi_s.astertune.playback.downloadManager.DownloadDirectoryManagerOt
import io.github.yuuichi_s.astertune.utils.YTPlayerUtils
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.dlCoroutine
import io.github.yuuichi_s.astertune.utils.enumPreference
import io.github.yuuichi_s.astertune.utils.get
import io.github.yuuichi_s.astertune.utils.reportException
import io.github.yuuichi_s.astertune.utils.scanners.InvalidAudioFileException
import io.github.yuuichi_s.astertune.utils.scanners.fileFromUri
import io.github.yuuichi_s.astertune.utils.scanners.uriListFromString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: SimpleCache,
    @PlayerCache val playerCache: SimpleCache,
) {
    val TAG = DownloadUtil::class.simpleName.toString()

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val songUrlCache = StreamUrlCache()
    private val dataSourceFactory = ResolvingDataSource.Factory(
        CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(
                OkHttpDataSource.Factory(
                    OkHttpClient.Builder()
                        .proxy(YouTube.proxy)
                        .build()
                )
            )
    ) { dataSpec ->
        val mediaId = dataSpec.key ?: error("No media id")
        val length = if (dataSpec.length >= 0) dataSpec.length else 1
        if (playerCache.isCached(mediaId, dataSpec.position, length)) {
            return@Factory dataSpec
        }

        songUrlCache[mediaId]?.let { cachedStream ->
            return@Factory dataSpec.withResolvedStream(cachedStream)
        }

        val playbackData = runBlocking(Dispatchers.IO) {
            YTPlayerUtils.playerResponseForPlayback(
                mediaId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
            )
        }.getOrThrow()
        val format = playbackData.format

        database.query {
            upsert(
                FormatEntity(
                    id = mediaId,
                    itag = format.itag,
                    mimeType = format.mimeType.split(";")[0],
                    codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                    bitrate = format.bitrate,
                    sampleRate = format.audioSampleRate,
                    contentLength = format.contentLength!!,
                    loudnessDb = playbackData.audioConfig?.loudnessDb,
                    playbackTrackingUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                )
            )
        }

        val streamUrl = playbackData.streamUrl.let {
            // Specify range to avoid YouTube's throttling
            "${it}&range=0-${format.contentLength ?: 10000000}"
        }

        val stream = songUrlCache.put(
            mediaId = mediaId,
            url = streamUrl,
            requestHeaders = playbackData.streamHeaders,
            clientName = playbackData.streamClient,
            expiresInSeconds = playbackData.streamExpiresInSeconds,
        )
        dataSpec.withResolvedStream(stream)
    }
    val downloadNotificationHelper = DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)
    val downloadManager: DownloadManager =
        DownloadManager(context, databaseProvider, downloadCache, dataSourceFactory, Executor(Runnable::run)).apply {
            maxParallelDownloads = 3
            requirements = downloadRequirements(context.dataStore.get(DownloadOnWifiOnlyKey, true))
            addListener(
                ExoDownloadService.TerminalStateNotificationHelper(
                    context = context,
                    notificationHelper = downloadNotificationHelper,
                    nextNotificationId = ExoDownloadService.NOTIFICATION_ID + 1
                )
            )
        }
    val downloads = MutableStateFlow<Map<String, LocalDateTime>>(emptyMap())

    var localMgr = DownloadDirectoryManagerOt(
        context,
        context.dataStore.get(DownloadPathKey, "").toUri(),
        uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
    )
    var isProcessingDownloads = MutableStateFlow(false)

    // Serializes download moves, deletions, scans, and directory updates.
    private val storageMutex = Mutex()

    // Keeps storage operations running after the requesting screen is closed.
    private val storageScope = CoroutineScope(SupervisorJob() + dlCoroutine)

    // Songs saved in a download directory whose internal copies are awaiting removal.
    private val movedSongIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun getDownload(songId: String): Flow<LocalDateTime?> = downloads.map { it[songId] }

    fun download(songs: List<MediaMetadata>) {
        if (songs.any { downloads.value[it.id] == null }) notifyIfWaitingForWifi()
        songs.forEach { song -> downloadSong(song.id, song.title) }
    }

    fun download(song: MediaMetadata) {
        if (downloads.value[song.id] == null) notifyIfWaitingForWifi()
        downloadSong(song.id, song.title)
    }

    fun download(song: SongEntity) {
        if (downloads.value[song.id] == null) notifyIfWaitingForWifi()
        downloadSong(song.id, song.title)
    }

    /**
     * Update the network requirement for downloads. Takes effect immediately, including for
     * downloads that are already queued or in progress.
     */
    fun setDownloadRequirements(wifiOnly: Boolean) {
        DownloadService.sendSetRequirements(
            context,
            ExoDownloadService::class.java,
            downloadRequirements(wifiOnly),
            false
        )
    }

    /**
     * Show a hint when a download is requested on a metered network while Wi-Fi only is enabled,
     * since the download is queued silently and only starts once Wi-Fi is available.
     */
    private fun notifyIfWaitingForWifi() {
        if (context.dataStore.get(DownloadOnWifiOnlyKey, true) && connectivityManager.isActiveNetworkMetered) {
            Toast.makeText(context, R.string.download_waiting_for_wifi, LENGTH_SHORT).show()
        }
    }

    private fun downloadSong(id: String, title: String) {
        if (downloads.value[id] != null) return
        val downloadRequest = DownloadRequest.Builder(id, id.toUri())
            .setCustomCacheKey(id)
            .setData(title.toByteArray())
            .build()
        DownloadService.sendAddDownload(
            context,
            ExoDownloadService::class.java,
            downloadRequest,
            false
        )
    }

    fun resumeDownloadsOnStart() {
        DownloadService.sendResumeDownloads(
            context,
            ExoDownloadService::class.java,
            false
        )
    }


    fun removeDownload(songId: String) {
        storageScope.launch {
            storageMutex.withLock {
                // A concurrent move may add this ID after the internal download is removed.
                movedSongIds.remove(songId)
                if (localMgr.deleteFile(songId)) {
                    downloads.update { it - songId }
                    database.removeDownloadSong(songId)
                }
            }
        }
        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, songId, false)
    }

    /**
     * Opens a download's cached files as a single stream.
     *
     * Returns null if the cache is empty or has gaps. If the download size is known,
     * the cached data must also match that size.
     */
    private fun openInternalData(download: Download): InputStream? {
        val spans: Set<CacheSpan> = downloadCache.getCachedSpans(download.request.id)
        val files = ArrayList<File>()
        var position = 0L
        for (span in spans) {
            val file = span.file
            if (!span.isCached || file == null || span.position != position) {
                return null
            }

            files.add(file)
            position += span.length
        }

        if (files.isEmpty() || (download.contentLength > 0 && position != download.contentLength)) {
            return null
        }

        val iterator = files.iterator()
        return SequenceInputStream(object : Enumeration<InputStream> {
            override fun hasMoreElements() = iterator.hasNext()
            override fun nextElement(): InputStream = FileInputStream(iterator.next())
        })
    }

    suspend fun moveToFolder(mediaId: String) {
        try {
            storageMutex.withLock {
                if (moveToFolderCatching(mediaId)) {
                    notifyMoveFailed()
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }
    }

    /**
     * Moves completed internal downloads to the main download directory, if set.
     *
     * Posts one notification if any downloads remain after a failed move.
     *
     * @param refreshFiles Whether to scan the download directories before moving songs.
     *     Set to false if they were just scanned.
     */
    suspend fun migrateDownloads(refreshFiles: Boolean = true) =
        withContext(NonCancellable) { migrateDownloadsUncancellable(refreshFiles) }

    private suspend fun migrateDownloadsUncancellable(refreshFiles: Boolean) {
        if (context.dataStore.get(DownloadPathKey, "").isEmpty()) {
            return
        }

        storageMutex.withLock {
            var anyKept = false
            isProcessingDownloads.value = true
            try {
                val ids = ArrayList<String>()
                downloadManager.downloadIndex.getDownloads(Download.STATE_COMPLETED).use { cursor ->
                    while (cursor.moveToNext()) {
                        ids.add(cursor.download.request.id)
                    }
                }

                if (ids.isEmpty()) {
                    return
                }

                localMgr.deleteTempFiles()
                if (refreshFiles) {
                    localMgr.getAvailableFiles(false)
                }

                ids.forEachIndexed { index, id ->
                    if (DOWNLOAD_DEBUG) Log.d(TAG, "Migrating download: ${index + 1}/${ids.size}")
                    if ((index + 1) % 20 == 0) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "${index + 1}/${ids.size}", LENGTH_SHORT).show()
                        }
                    }
                    if (moveToFolderCatching(id)) {
                        anyKept = true
                    }
                }
            } catch (e: Exception) {
                reportException(e)
            } finally {
                isProcessingDownloads.value = false
            }

            if (anyKept) {
                notifyMoveFailed()
            }
        }
    }

    /**
     * Moves an internal download to the main download directory.
     *
     * Reuses an existing file in any download directory instead of saving another copy.
     * Removes the internal copy only after the file is registered.
     * The caller must hold [storageMutex].
     *
     * @return true if the move fails and the internal download is still marked as completed.
     */
    private suspend fun moveToFolderLocked(mediaId: String): Boolean {
        val downloadPath = context.dataStore.get(DownloadPathKey, "")
        // A changed path is applied by setDownloadPath(), which moves the remaining songs afterward.
        if (downloadPath.isEmpty() || downloadPath.toUri() != localMgr.dir) {
            return false
        }

        val download = downloadManager.downloadIndex.getDownload(mediaId)
        val song = database.song(mediaId).first()
        val title = song?.title
            ?: download?.request?.data?.let { String(it) }
            ?: mediaId

        var saved = localMgr.findFile(mediaId)
        if (saved == null) {
            if (download?.state != Download.STATE_COMPLETED) return false

            if (!hasInternalData(download)) {
                // Remove the record so a song with missing data is not shown as downloaded.
                withContext(Dispatchers.Main) { downloadManager.removeDownload(mediaId) }
                return false
            }

            val dir = localMgr.mainDir
            if (dir == null || fileFromUri(context, dir.uri) == null) {
                return isKept(mediaId)
            }

            saved = openInternalData(download)?.use { localMgr.saveFile(mediaId, it, title) }
        }

        val path = saved?.let { fileFromUri(context, it.uri) }?.absolutePath
            ?: return isKept(mediaId)

        val dateDownload = song?.song?.dateDownload
            ?.takeIf { it != STATE_DOWNLOADING && it != STATE_INVALID }
            ?: download?.let {
                Instant.ofEpochMilli(it.updateTimeMs).atZone(ZoneOffset.UTC).toLocalDateTime()
            }?: LocalDateTime.now()

        database.registerDownloadSong(mediaId, dateDownload, path)
        downloads.update { it + (mediaId to dateDownload) }

        if (download != null) {
            movedSongIds.add(mediaId)
            withContext(Dispatchers.Main) { downloadManager.removeDownload(mediaId) }
        }

        return false
    }

    /**
     * Moves an internal download and reports errors.
     *
     * The caller must hold [storageMutex].
     *
     * @return true if the move fails and the internal download is still marked as completed.
     */
    private suspend fun moveToFolderCatching(mediaId: String): Boolean = try {
        moveToFolderLocked(mediaId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reportException(e)
        runCatching { isKept(mediaId) }.getOrDefault(false)
    }

    private fun isKept(mediaId: String): Boolean =
        downloadManager.downloadIndex.getDownload(mediaId)?.state == Download.STATE_COMPLETED

    /**
     * Posts a move-failure notification with the count of remaining completed internal downloads.
     *
     * Callers must hold [storageMutex].
     */
    private fun notifyMoveFailed() {
        var count = 0
        downloadManager.downloadIndex.getDownloads(Download.STATE_COMPLETED).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.download.request.id !in movedSongIds && hasInternalData(cursor.download)) {
                    count++
                }
            }
        }

        if (count == 0) return

        val text = context.getString(R.string.dl_move_failed_count_text, count)
        val notification = NotificationCompat.Builder(context, ExoDownloadService.CHANNEL_ID)
            .setSmallIcon(R.drawable.error)
            .setContentTitle(context.getString(R.string.dl_move_failed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationUtil.setNotification(context, ExoDownloadService.MOVE_FAILED_NOTIFICATION_ID, notification)
    }

    /**
     * Changes the main download directory and moves completed internal downloads there.
     *
     * Registers existing files in the download directories and preserves existing download dates.
     *
     * @param scanFirst Whether to register previously unregistered files before moving
     *     internal downloads. Otherwise, registers them afterward.
     */
    fun setDownloadPath(path: String, scanFirst: Boolean) {
        storageScope.launch {
            context.dataStore.edit { it[DownloadPathKey] = path }
            storageMutex.withLock { cd() }
            if (scanFirst) {
                scanDownloads()
            } else {
                rescanDownloads()
            }
            migrateDownloads(refreshFiles = false)
            if (!scanFirst) {
                registerNewFiles()
            }
        }
    }

    /**
     * Marks newly found files as downloaded.
     *
     * Uses the current time as their download date. Skips songs that are not in the database.
     */
    private suspend fun registerNewFiles() = withContext(NonCancellable) {
        storageMutex.withLock {
            val newFiles = localMgr.getAvailableFiles(true).filterKeys { it !in downloads.value }
            if (newFiles.isEmpty()) return@withLock

            val timeNow = LocalDateTime.now()
            database.awaitTransaction {
                newFiles.forEach { (id, uri) ->
                    val file = fileFromUri(context, uri) ?: return@forEach
                    registerDownloadSong(id, timeNow, file.absolutePath)
                }
            }
            val registered = database.downloadedOrQueuedSongs().first().filter { it.id in newFiles }
            downloads.update { map -> map + registered.associate { it.id to it.song.dateDownload!! } }
        }
    }

    fun setExtraDownloadPaths(paths: String) {
        storageScope.launch {
            context.dataStore.edit { it[DownloadExtraPathKey] = paths }
            storageMutex.withLock { cd() }
            scanDownloads()
        }
    }

    fun startScanDownloads() {
        storageScope.launch { scanDownloads() }
    }

    fun startMigrateDownloads() {
        storageScope.launch { migrateDownloads() }
    }

    fun cd() {
        localMgr.doInit(
            context,
            context.dataStore.get(DownloadPathKey, "").toUri(),
            uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
        )
    }

    /**
     * Refreshes download status and file paths for registered songs.
     */
    suspend fun rescanDownloads() = withContext(NonCancellable) { storageMutex.withLock { rescanDownloadsLocked() } }

    /**
     * Checks whether a download record counts as an internal download.
     *
     * Ignores downloads being removed. For completed downloads, checks for all cached data
     * only when the total length is known.
     */
    private fun hasInternalData(download: Download): Boolean = when (download.state) {
        Download.STATE_REMOVING -> false
        Download.STATE_COMPLETED -> download.contentLength <= 0 ||
            downloadCache.isCached(download.request.id, 0, download.contentLength)
        else -> true
    }

    private suspend fun rescanDownloadsLocked() {
        isProcessingDownloads.value = true
        try {
            rescanDownloadsUnflagged()
        } finally {
            isProcessingDownloads.value = false
        }
    }

    private suspend fun rescanDownloadsUnflagged() {
        if (DOWNLOAD_DEBUG) Log.i(TAG, "+rescanDownloads()")
        val dbDownloads = database.downloadedOrQueuedSongs().first()
        val result = mutableMapOf<String, LocalDateTime>()

        // Clear download status for songs missing from both the directories and internal downloads.
        val missingFiles =
            localMgr.getMissingFiles(dbDownloads.filterNot { it.song.dateDownload == null }).toMutableList()
        if (DOWNLOAD_DEBUG) {
            Log.d(TAG, "Found ${missingFiles.size}/${dbDownloads.size} songs not in custom download directories")
        }

        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                if (!hasInternalData(cursor.download)) {
                    continue
                }

                missingFiles.removeIf { it.id == cursor.download.request.id }
            }
        }
        if (DOWNLOAD_DEBUG) Log.d(
            TAG,
            "Found ${missingFiles.size}/${dbDownloads.size} song not in custom download directories + internal cache. Removing these files now"
        )

        val availableDownloads = dbDownloads.minus(missingFiles)
        val files = localMgr.getAvailableFiles(true)
        val changedPaths = availableDownloads.mapNotNull { s ->
            val path = files[s.id]?.let {
                fileFromUri(context, it)
            }?.absolutePath ?: return@mapNotNull null

            if (path == s.song.localPath) {
                null
            } else {
                Triple(s.id, s.song.dateDownload!!, path)
            }
        }

        database.awaitTransaction {
            missingFiles.forEach {
                if (DOWNLOAD_DEBUG) Log.v(TAG, "Shedding: [${it.id}] ${it.song.title}")
                removeDownloadSong(it.song.id)
            }
            changedPaths.forEach { (id, dateDownload, path) -> registerDownloadSong(id, dateDownload, path) }
        }

        availableDownloads.forEach { s ->
            result[s.song.id] = s.song.dateDownload!! // The query excludes null download dates.
        }

        downloads.value = result
        if (DOWNLOAD_DEBUG) Log.i(TAG, "-rescanDownloads()")
    }


    /**
     * Rebuilds download records from the download directories and internal downloads.
     *
     * Only updates songs already in the database.
     */
    suspend fun scanDownloads() = withContext(NonCancellable) { scanDownloadsUncancellable() }

    private suspend fun scanDownloadsUncancellable() = storageMutex.withLock {
        if (DOWNLOAD_DEBUG) Log.i(TAG, "+scanDownloads()")
        isProcessingDownloads.value = true

        try {
            val registeredDates = database.downloadedOrQueuedSongs().first().mapNotNull { s ->
                s.song.dateDownload?.takeIf { it != STATE_DOWNLOADING && it != STATE_INVALID }?.let { s.id to it }
            }.toMap()
            database.removeAllDownloadedSongs()
            val timeNow = LocalDateTime.now()

            // add custom downloads
            val availableFiles = localMgr.getAvailableFiles(false)
            database.awaitTransaction {
                availableFiles.forEach { f ->
                    try {
                        val file = fileFromUri(context, f.value)
                        if (file == null) throw (InvalidAudioFileException("Hello darkness my old friend"))

                        registerDownloadSong(f.key, registeredDates[f.key] ?: timeNow, file.absolutePath)

                    } catch (e: InvalidAudioFileException) {
                        reportException(e)
                    }
                }
            }

            if (DOWNLOAD_DEBUG) Log.d(TAG, "Registered ${availableFiles.size} files from custom downloads")

            // add internal downloads
            val internalDownloads = ArrayList<Download>()
            downloadManager.downloadIndex.getDownloads().use { cursor ->
                while (cursor.moveToNext()) {
                    if (hasInternalData(cursor.download)) {
                        internalDownloads.add(cursor.download)
                    }
                }
            }
            var count = 0
            database.awaitTransaction {
                internalDownloads.forEach {
                    updateDownloadStatus(it.request.id, stateToLocalDateTime(it))
                    count ++
                }
            }
            if (DOWNLOAD_DEBUG) {
                Log.d(TAG, "Registered $count files from internal downloads")
                Log.d(TAG, "Database registration complete, triggering map registry rebuild")
            }

            rescanDownloadsUnflagged()
        } finally {
            isProcessingDownloads.value = false
        }

        if (DOWNLOAD_DEBUG) Log.i(TAG, "-scanDownloads()")
    }

    companion object {
        val STATE_DOWNLOADING: LocalDateTime = Instant.ofEpochMilli(1).atZone(ZoneOffset.UTC).toLocalDateTime()
        val STATE_INVALID: LocalDateTime = Instant.ofEpochMilli(0).atZone(ZoneOffset.UTC).toLocalDateTime()

        fun downloadRequirements(wifiOnly: Boolean): Requirements =
            Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
    }


    init {
        if (DOWNLOAD_DEBUG) Log.i(TAG, "DownloadUtil init")
        // TODO: make sure db is update when download is queued
        CoroutineScope(dlCoroutine).launch {
            // A move interrupted by process death leaves its temporary file in the download directory.
            try {
                storageMutex.withLock { localMgr.deleteTempFiles() }
            } catch (e: Exception) {
                reportException(e)
            }
            rescanDownloads()
        }

        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    // Keep the song marked as downloaded while its internal copy is removed.
                    if (download.state == Download.STATE_REMOVING && download.request.id in movedSongIds) {
                        return
                    }

                    downloads.update { map ->
                        map.toMutableMap().apply {
                            val state = stateToLocalDateTime(download)
                            if (state == STATE_INVALID) {
                                Log.w(TAG, "Invalid download state for ${download.request.id}. Removing download")
                                remove(download.request.id)
                            } else {
                                set(download.request.id, state)
                            }
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        if (download.state == Download.STATE_COMPLETED) {
                            val updateTime =
                                Instant.ofEpochMilli(download.updateTimeMs).atZone(ZoneOffset.UTC).toLocalDateTime()
                            database.updateDownloadStatus(download.request.id, updateTime)
                        } else {
                            database.updateDownloadStatus(download.request.id, null)
                        }
                    }

                    if (download.state == Download.STATE_COMPLETED) {
                        CoroutineScope(dlCoroutine).launch { moveToFolder(download.request.id) }
                    }
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    movedSongIds.remove(download.request.id)
                }
            }
        )
    }
}

fun stateToLocalDateTime(download: Download): LocalDateTime {
    return when (download.state) {
        Download.STATE_COMPLETED -> {
            Instant.ofEpochMilli(download.updateTimeMs).atZone(ZoneOffset.UTC).toLocalDateTime()
        }

        Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> STATE_DOWNLOADING
        else -> STATE_INVALID
    }
}
