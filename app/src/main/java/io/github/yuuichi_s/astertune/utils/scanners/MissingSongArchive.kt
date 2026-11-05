/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.scanners

import android.content.Context
import android.util.Log
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.AlbumEntity
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Keeps user data of local songs whose files were not found, to restore it when the song is
 * scanned again.
 *
 * Code that changes both the archive and the database, or reads both for a backup, holds [lock]
 * so the two stay consistent.
 * Functions that read or write the file throw [IOException] when the file cannot be accessed.
 */
object MissingSongArchive {
    private const val TAG = "MissingSongArchive"
    const val FILENAME = "missing_song_archive.json"
    const val RETENTION_MILLIS = 365L * 24 * 60 * 60 * 1000
    private const val VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_SONGS = "songs"
    private const val TEMP_SUFFIX = ".tmp"

    val lock = Mutex()

    data class PlayCount(val year: Int, val month: Int, val count: Int)

    data class PlayEvent(val timestamp: LocalDateTime, val playTime: Long)

    data class PlaylistEntry(val playlistId: String, val position: Int, val setVideoId: String?)

    data class Lyrics(
        val lyrics: String,
        val provider: String?,
        val lastCheckedAt: Long?,
        val providerSignature: String?,
    )

    data class Entry(
        val title: String,
        val artists: List<String>,
        val album: String?,
        val localPath: String?,
        val archivedAt: Long,
        val liked: Boolean,
        val likedDate: LocalDateTime?,
        val lyrics: Lyrics?,
        val playCounts: List<PlayCount>,
        val events: List<PlayEvent>,
        val playlists: List<PlaylistEntry>,
    ) {
        /**
         * Builds a [Song] carrying only the fields used by [LocalMediaScanner.compareSong].
         */
        fun toMatchSong(): Song = Song(
            song = SongEntity(id = "", title = title, localPath = localPath, isLocal = true),
            artists = artists.map { ArtistEntity(id = "", name = it, isLocal = true) },
            album = album?.let { AlbumEntity(id = "", title = it, songCount = 0, duration = 0, isLocal = true) },
        )

        fun isExpired(now: Long) = now - archivedAt >= RETENTION_MILLIS

        /**
         * Checks whether [other] is the same archived entry, ignoring changes to user data.
         */
        fun isSameEntryAs(other: Entry) =
            title == other.title &&
                artists == other.artists &&
                album == other.album &&
                localPath == other.localPath &&
                archivedAt == other.archivedAt
    }

    fun load(context: Context): List<Entry> {
        val bytes = readOrNull(archiveFile(context)) ?: return emptyList()
        try {
            return parse(bytes)
        } catch (e: JSONException) {
            Log.e(TAG, "Could not read the missing song archive", e)
        }
        return emptyList()
    }

    /** Hold [lock] while loading, modifying, and saving entries. */
    fun save(context: Context, entries: List<Entry>) {
        val file = archiveFile(context)
        if (entries.isEmpty()) {
            deleteFile(file)
            return
        }
        val root = JSONObject()
            .put(KEY_VERSION, VERSION)
            .put(KEY_SONGS, JSONArray(entries.map { entryToJson(it) }))
        writeFile(file, root.toString().toByteArray(Charsets.UTF_8))
    }

    /**
     * The archive is cleared first, so if writing it fails, the history in the database is not
     * cleared either.
     */
    suspend fun clearListenHistoryWithArchive(context: Context, database: MusicDatabase) {
        lock.withLock {
            val entries = load(context)
            if (entries.any { it.events.isNotEmpty() }) {
                save(context, entries.map { it.copy(events = emptyList()) })
            }
            database.awaitTransaction { clearListenHistory() }
        }
    }

    fun readBytes(context: Context): ByteArray? = readOrNull(archiveFile(context))

    /**
     * Deletes the current archive so it is not used with the restored database, and returns a
     * temporary copy of [bytes] for [finishRestore], or null if [bytes] are missing or invalid.
     * Hold [lock] from this call until [finishRestore] completes.
     */
    fun prepareRestore(context: Context, bytes: ByteArray?): File? {
        val file = archiveFile(context)
        val temp = if (bytes != null && isReadable(bytes)) writeTemp(file, bytes) else null
        deleteFile(file)
        return temp
    }

    fun finishRestore(context: Context, prepared: File?) {
        if (prepared != null) {
            rename(prepared, archiveFile(context))
        }
    }

    private fun archiveFile(context: Context) = File(context.filesDir, FILENAME)

    private fun readOrNull(file: File): ByteArray? {
        try {
            return file.readBytes()
        } catch (_: FileNotFoundException) {
            // There is no archive.
        }
        return null
    }

    private fun isReadable(bytes: ByteArray): Boolean {
        try {
            parse(bytes)
        } catch (e: JSONException) {
            Log.e(TAG, "The missing song archive in the backup is unreadable", e)
            return false
        }
        return true
    }

    /**
     * [KEY_VERSION] is not checked. Unknown keys are ignored, so a file written by a newer version
     * is read as far as its keys are known.
     */
    private fun parse(bytes: ByteArray): List<Entry> {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        return root.objectList(KEY_SONGS).map { entryFromJson(it) }
    }

    private fun entryToJson(entry: Entry): JSONObject = JSONObject()
        .put("title", entry.title)
        .put("artists", JSONArray(entry.artists))
        .putOpt("album", entry.album)
        .putOpt("localPath", entry.localPath)
        .put("archivedAt", entry.archivedAt)
        .put("liked", entry.liked)
        .putOpt("likedDate", entry.likedDate?.let { toEpochMilli(it) })
        .putOpt("lyrics", entry.lyrics?.let { lyricsToJson(it) })
        .put("playCounts", JSONArray(entry.playCounts.map { playCountToJson(it) }))
        .put("events", JSONArray(entry.events.map { playEventToJson(it) }))
        .put("playlists", JSONArray(entry.playlists.map { playlistEntryToJson(it) }))

    private fun entryFromJson(json: JSONObject): Entry {
        val artists = json.optJSONArray("artists") ?: JSONArray()
        return Entry(
            title = json.getString("title"),
            artists = (0 until artists.length()).map { artists.getString(it) },
            album = json.optStringOrNull("album"),
            localPath = json.optStringOrNull("localPath"),
            archivedAt = json.getLong("archivedAt"),
            liked = json.optBoolean("liked"),
            likedDate = json.optLongOrNull("likedDate")?.let { fromEpochMilli(it) },
            lyrics = json.optJSONObject("lyrics")?.let { lyricsFromJson(it) },
            playCounts = json.objectList("playCounts").map { playCountFromJson(it) },
            events = json.objectList("events").map { playEventFromJson(it) },
            playlists = json.objectList("playlists").map { playlistEntryFromJson(it) },
        )
    }

    private fun lyricsToJson(lyrics: Lyrics): JSONObject = JSONObject()
        .put("lyrics", lyrics.lyrics)
        .putOpt("provider", lyrics.provider)
        .putOpt("lastCheckedAt", lyrics.lastCheckedAt)
        .putOpt("providerSignature", lyrics.providerSignature)

    private fun lyricsFromJson(json: JSONObject) = Lyrics(
        lyrics = json.getString("lyrics"),
        provider = json.optStringOrNull("provider"),
        lastCheckedAt = json.optLongOrNull("lastCheckedAt"),
        providerSignature = json.optStringOrNull("providerSignature"),
    )

    private fun playCountToJson(playCount: PlayCount): JSONObject = JSONObject()
        .put("year", playCount.year)
        .put("month", playCount.month)
        .put("count", playCount.count)

    private fun playCountFromJson(json: JSONObject) = PlayCount(
        year = json.getInt("year"),
        month = json.getInt("month"),
        count = json.getInt("count"),
    )

    private fun playEventToJson(event: PlayEvent): JSONObject = JSONObject()
        .put("timestamp", toEpochMilli(event.timestamp))
        .put("playTime", event.playTime)

    private fun playEventFromJson(json: JSONObject) = PlayEvent(
        timestamp = fromEpochMilli(json.getLong("timestamp")),
        playTime = json.getLong("playTime"),
    )

    private fun playlistEntryToJson(entry: PlaylistEntry): JSONObject = JSONObject()
        .put("playlistId", entry.playlistId)
        .put("position", entry.position)
        .putOpt("setVideoId", entry.setVideoId)

    private fun playlistEntryFromJson(json: JSONObject) = PlaylistEntry(
        playlistId = json.getString("playlistId"),
        position = json.getInt("position"),
        setVideoId = json.optStringOrNull("setVideoId"),
    )

    private fun JSONObject.objectList(name: String): List<JSONObject> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) getLong(name) else null

    /** The target keeps its old contents if writing or renaming fails. */
    private fun writeFile(target: File, bytes: ByteArray) {
        rename(writeTemp(target, bytes), target)
    }

    private fun writeTemp(target: File, bytes: ByteArray): File {
        val temp = File(target.path + TEMP_SUFFIX)
        try {
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
        return temp
    }

    private fun rename(temp: File, target: File) {
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Could not rename $temp to $target")
        }
    }

    private fun deleteFile(file: File) {
        if (file.exists() && !file.delete()) {
            throw IOException("Could not delete $file")
        }
    }

    // Same conversion as the database type converters, so restored rows keep their values.
    private fun toEpochMilli(time: LocalDateTime) = time.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun fromEpochMilli(value: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneOffset.UTC)
}
