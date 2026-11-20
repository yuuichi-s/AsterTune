/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.lyricsplus

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Client for the [Lyrics+ API](https://github.com/ibratabian17/LyricsPlus) TTML endpoint.
 *
 * Extracts Apple Music-style TTML from the JSON response and leaves parsing to the caller.
 */
object LyricsPlus {
    private const val ENDPOINT = "https://lyricsplus.prjktla.my.id/v1/ttml/get"
    private const val USER_AGENT = "AsterTune (+https://github.com/yuuichi-s/AsterTune)"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
        }
    }

    /**
     * Fetches TTML lyrics from Lyrics+.
     *
     * Returns the TTML text, or a successful `null` when the response indicates
     * that no lyrics were found. Returns a failure if the request fails or the
     * response is inconclusive.
     *
     * @param duration track duration in seconds. Pass 0 or less to omit it from the request.
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
    ): Result<String?> = runCatching {
        val response = client.get(ENDPOINT) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "application/json")
            parameter("title", title)
            parameter("artist", artist)
            if (duration > 0) parameter("duration", duration)
        }
        val body = response.bodyAsText()
        when (response.status) {
            HttpStatusCode.OK -> json.decodeFromString<TtmlResponse>(body).ttml?.trim()?.takeIf { it.isNotEmpty() }
            HttpStatusCode.NotFound -> {
                if (!isDefinitiveNotFound(body)) {
                    throw IOException("Lyrics+ lookup inconclusive: HTTP 404")
                }
                null
            }
            else -> throw IOException("Lyrics+ request failed: HTTP ${response.status.value}")
        }
    }

    /** Checks whether the source statuses in a 404 indicate that no lyrics were found. */
    internal fun isDefinitiveNotFound(body: String): Boolean {
        val statuses = runCatching { json.decodeFromString<NotFoundResponse>(body) }.getOrNull()
            ?.processingTime?.sourcesStatus?.values?.map { it.status }
            ?: return false
        return statuses.any { it == "BAD" } && statuses.all { it == "BAD" || it == "SKIP" }
    }

    @Serializable
    private data class TtmlResponse(val ttml: String? = null)

    @Serializable
    private data class NotFoundResponse(val processingTime: ProcessingTime? = null)

    @Serializable
    private data class ProcessingTime(val sourcesStatus: Map<String, SourceStatus>? = null)

    @Serializable
    private data class SourceStatus(val status: String? = null)
}
