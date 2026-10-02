package com.dd3boh.lrclib

import com.dd3boh.lrclib.models.Track
import com.dd3boh.lrclib.models.bestMatchingFor
import com.dd3boh.lrclib.models.selectCandidates
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Source: https://github.com/Malopieds/InnerTune
 */
object LrcLib {
    internal val jsonFormat = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(jsonFormat)
            }

            defaultRequest {
                url("https://lrclib.net")
            }

            expectSuccess = true
        }
    }

    private suspend fun queryLyrics(
        artist: String,
        title: String,
        album: String? = null,
    ) = client
        .get("/api/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
            if (album != null) parameter("album_name", album)
        }.body<List<Track>>()

    /**
     * Look up synced lyrics. Returns success with the raw LRC text when a match is found, success with
     * null when the search succeeded but carried no synced match (a definitive absence), and a failure
     * when the request itself failed. Non-2xx responses throw because [expectSuccess] is set.
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String?> = runCatching {
        val syncedTracks = queryLyrics(artist, title, album).filter { it.syncedLyrics != null }
        syncedTracks.bestMatchingFor(duration)?.syncedLyrics?.let(LrcLib::Lyrics)?.text
    }

    /**
     * Delivers manual-search lyrics candidates through [callback].
     * Rows without a duration are excluded, even when [duration] is unknown (-1).
     * Request and response-decoding failures are propagated.
     */
    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        queryLyrics(artist, title, album).selectCandidates(duration).forEach(callback)
    }

    /**
     * Queries LRCLIB tracks without candidate filtering.
     * Request and response-decoding failures are returned in [Result].
     */
    suspend fun lyrics(
        artist: String,
        title: String,
    ) = runCatching {
        queryLyrics(artist = artist, title = title, album = null)
    }

    @JvmInline
    value class Lyrics(
        val text: String,
    ) {
        val sentences
            get() =
                runCatching {
                    buildMap {
                        put(0L, "")
                        text.trim().lines().filter { it.length >= 10 }.forEach {
                            put(
                                it[8].digitToInt() * 10L +
                                        it[7].digitToInt() * 100 +
                                        it[5].digitToInt() * 1000 +
                                        it[4].digitToInt() * 10000 +
                                        it[2].digitToInt() * 60 * 1000 +
                                        it[1].digitToInt() * 600 * 1000,
                                it.substring(10),
                            )
                        }
                    }
                }.getOrNull()
    }
}
