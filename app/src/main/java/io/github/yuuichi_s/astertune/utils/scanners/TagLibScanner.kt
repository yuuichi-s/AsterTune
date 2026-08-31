/*
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.scanners

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.yuuichi_s.astertune.constants.DEBUG_SAVE_OUTPUT
import io.github.yuuichi_s.astertune.constants.EXTRACTOR_DEBUG
import io.github.yuuichi_s.astertune.constants.SCANNER_DEBUG
import io.github.yuuichi_s.astertune.db.entities.AlbumEntity
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.FormatEntity
import io.github.yuuichi_s.astertune.db.entities.GenreEntity
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import io.github.yuuichi_s.astertune.models.SongTempData
import io.github.yuuichi_s.astertune.ui.utils.ARTIST_SEPARATORS
import io.github.yuuichi_s.astertune.ui.utils.EXTRACTOR_TAG
import com.kyant.taglib.TagLib
import java.io.File
import java.lang.Integer.parseInt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Metadata scanner backed by TagLib (io.github.kyant0:taglib).
 *
 * TagLib reads tags directly from the file and does not depend on the system codecs or
 * MediaStore, so this works consistently across OS versions and ABIs. The upstream TagLib
 * AudioProperties does not expose a codec name, so the codec is read separately from
 * [MediaExtractor] (see [readCodec]) and falls back to the file extension when unavailable.
 */
class TagLibScanner : MetadataScanner {

    /**
     * Given a path to a file, extract necessary metadata.
     *
     * @param file Full file path
     */
    override suspend fun getAllMetadataFromFile(file: File): SongTempData {
        if (EXTRACTOR_DEBUG)
            Log.v(EXTRACTOR_TAG, "Starting Full Extractor session on: ${file.absolutePath}")

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val songId = SongEntity.generateSongId()
            var rawTitle: String? = null
            var albumName: String? = null
            var trackNumber: Int? = null
            var discNumber: Int? = null
            var year: Int? = null
            var date: LocalDateTime? = null
            var bitsPerSample: Int? = null

            var extraData = "" // extra data field
            var allData = "" // for debugging

            var artistList = ArrayList<ArtistEntity>()
            var genresList = ArrayList<GenreEntity>()

            // Read audio properties (length is in ms, bitrate in kbps)
            val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd())
                ?: throw RuntimeException("TagLib failed to read audio properties for: ${file.absolutePath}")
            val rawDuration = audioProperties.length
            val sampleRate = audioProperties.sampleRate
            val bitrate = audioProperties.bitrate * 1000

            val metadata = TagLib.getMetadata(fd = fd.dup().detachFd(), readPictures = false)
                ?: throw RuntimeException("TagLib failed to read metadata for: ${file.absolutePath}")

            metadata.propertyMap.forEach { (key, value) ->
                value.forEach {
                    if (EXTRACTOR_DEBUG && DEBUG_SAVE_OUTPUT) {
                        allData += "\n$key: $it"
                    }

                    when (key) {
                        "ARTISTS", "ARTIST", "artist" -> {
                            it.split(ARTIST_SEPARATORS).forEach { artistVal ->
                                artistList.add(ArtistEntity(ArtistEntity.generateArtistId(), artistVal.trim(), isLocal = true))
                            }
                        }

                        "BITS_PER_SAMPLE", "bits_per_sample", "BITSPERSAMPLE", "bitsPerSample", "BITS" -> {
                            val bp = it.trim().filter { ch -> ch.isDigit() }.toIntOrNull()
                            if (bp != null && bp > 0) bitsPerSample = bp
                        }

                        "ALBUM", "album" -> albumName = it
                        "TITLE", "title" -> rawTitle = it
                        "GENRE", "genre" -> {
                            it.split(ARTIST_SEPARATORS).forEach { genreVal ->
                                genresList.add(GenreEntity(GenreEntity.generateGenreId(), genreVal.trim(), isLocal = true))
                            }
                        }
                        // date can have multiple list elements, though let it parse via string regardless
                        "DATE", "date" -> {
                            try {
                                if (date == null) {
                                    date = LocalDate.parse(it.trim()).atStartOfDay()
                                }
                            } catch (e: Exception) {
                                if (SCANNER_DEBUG) e.printStackTrace()
                                try {
                                    if (year == null) {
                                        year = date?.year ?: parseInt(it.trim())
                                    }
                                } catch (e: Exception) {
                                    if (SCANNER_DEBUG) e.printStackTrace()
                                }
                            }
                        }

                        "TRACKNUMBER" -> {
                            try {
                                trackNumber = parseInt(it.substringBefore('/').trim())
                            } catch (e: Exception) {
                                if (SCANNER_DEBUG) e.printStackTrace()
                            }
                        }
                        "DISCNUMBER" -> {
                            try {
                                discNumber = parseInt(it.substringBefore('/').trim())
                            } catch (e: Exception) {
                                if (SCANNER_DEBUG) e.printStackTrace()
                            }
                        }
                        else -> {
                            extraData += "$key: $it\n"
                        }
                    }
                }
            }

            if (EXTRACTOR_DEBUG && DEBUG_SAVE_OUTPUT) {
                Log.v(EXTRACTOR_TAG, "Full output for: ${file.absolutePath} \n $allData")
            }

            val timeNow = LocalDateTime.now()

            val title: String = if (!rawTitle.isNullOrBlank()) { // songs with no title tag
                rawTitle.trim()
            } else {
                file.absolutePath.substringAfterLast('/').substringBeforeLast('.')
            }

            val duration: Long = (rawDuration / 1000).toLong()

            val dateModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneOffset.UTC)
            val albumId = if (albumName != null) AlbumEntity.generateAlbumId() else null

            // codec is not part of TagLib's audio properties; read it from MediaExtractor
            val codec = readCodec(file)

            val albumEntity = if (albumName != null && albumId != null) AlbumEntity(
                id = albumId,
                title = albumName,
                thumbnailUrl = file.absolutePath,
                songCount = 1,
                duration = duration.toInt(),
                isLocal = true
            ) else null

            // deduplicate
            artistList = artistList.filterNot { it.name.isBlank() }.distinctBy { it.name.lowercase() } as ArrayList<ArtistEntity>
            genresList = genresList.filterNot { it.title.isBlank() }.distinctBy { it.title.lowercase() } as ArrayList<GenreEntity>

            return SongTempData(
                Song(
                    song = SongEntity(
                        id = songId,
                        title = title,
                        duration = duration.toInt(), // we use seconds for duration
                        thumbnailUrl = file.absolutePath,
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                        albumId = albumId,
                        albumName = albumName,
                        year = year,
                        date = date,
                        dateModified = dateModified,
                        isLocal = true,
                        inLibrary = timeNow,
                        localPath = file.absolutePath
                    ),
                    artists = artistList,
                    album = albumEntity,
                    genre = genresList
                ),
                FormatEntity(
                    id = songId,
                    itag = -1,
                    mimeType = "audio/${file.extension}",
                    codecs = codec,
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    bitsPerSample = bitsPerSample,
                    contentLength = duration,
                    extraComment = if (extraData.isNotBlank()) extraData else null,
                )
            )
        }
    }

    /**
     * Read the audio codec from the first audio track via [MediaExtractor], mapping the track's
     * MIME type to a short, user-facing codec name. Falls back to the file extension when the
     * codec cannot be determined (e.g. formats the system cannot parse).
     */
    private fun readCodec(file: File): String {
        val fallback = file.extension.lowercase().ifBlank { "—" }
        val extractor = MediaExtractor()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                extractor.setDataSource(fd.fileDescriptor)
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                    if (!mime.startsWith("audio/")) continue
                    return mimeToCodec(mime) ?: fallback
                }
            }
        } catch (e: Exception) {
            if (SCANNER_DEBUG) e.printStackTrace()
        } finally {
            extractor.release()
        }
        return fallback
    }

    /**
     * Map a MediaFormat audio MIME type to a short codec label. Returns null for unknown MIME
     * types so the caller can fall back to the file extension.
     */
    private fun mimeToCodec(mime: String): String? = when (mime.lowercase()) {
        MediaFormat.MIMETYPE_AUDIO_MPEG -> "mp3"
        MediaFormat.MIMETYPE_AUDIO_AAC -> "aac"
        "audio/alac" -> "alac"
        MediaFormat.MIMETYPE_AUDIO_FLAC -> "flac"
        MediaFormat.MIMETYPE_AUDIO_VORBIS -> "vorbis"
        MediaFormat.MIMETYPE_AUDIO_OPUS -> "opus"
        MediaFormat.MIMETYPE_AUDIO_RAW -> "pcm"
        MediaFormat.MIMETYPE_AUDIO_AC3 -> "ac3"
        MediaFormat.MIMETYPE_AUDIO_EAC3 -> "eac3"
        MediaFormat.MIMETYPE_AUDIO_AMR_NB -> "amr-nb"
        MediaFormat.MIMETYPE_AUDIO_AMR_WB -> "amr-wb"
        "audio/x-ms-wma" -> "wma"
        else -> null
    }
}
