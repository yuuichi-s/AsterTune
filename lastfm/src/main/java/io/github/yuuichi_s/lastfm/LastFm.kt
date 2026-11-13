/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.lastfm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * Last.fm API client for web authentication and scrobbling.
 * See https://www.last.fm/api/webauth and https://www.last.fm/api/show/track.scrobble.
 */
class LastFm(
    private val apiKey: String,
    private val sharedSecret: String,
    private val client: HttpClient = HttpClient(OkHttp),
) {
    /**
     * URL of the page where the user grants access. Last.fm then opens [callbackUrl] with a `token`
     * parameter.
     */
    fun authUrl(callbackUrl: String): String =
        URLBuilder(AUTH_URL).apply {
            parameters.append("api_key", apiKey)
            parameters.append("cb", callbackUrl)
        }.buildString()

    /** Exchanges a token from the callback URL for a session. */
    suspend fun getSession(token: String): ApiResult<Session> =
        when (val result = post(mapOf("method" to "auth.getSession", "token" to token))) {
            is ApiResult.Success -> {
                val session = result.value["session"] as? JsonObject
                val key = session?.get("key")?.jsonPrimitive?.contentOrNull
                val name = session?.get("name")?.jsonPrimitive?.contentOrNull
                if (key.isNullOrEmpty() || name.isNullOrEmpty()) {
                    ApiResult.Unavailable("session missing from response")
                } else {
                    ApiResult.Success(Session(name = name, key = key))
                }
            }

            is ApiResult.Error -> result
            is ApiResult.Unavailable -> result
        }

    /**
     * Sends a batch of 1 to [MAX_SCROBBLES_PER_REQUEST] scrobbles.
     *
     * Success also covers scrobbles ignored by Last.fm.
     *
     * @throws IllegalArgumentException if the batch size is outside the supported range.
     */
    suspend fun scrobble(sessionKey: String, scrobbles: List<Scrobble>): ApiResult<Unit> {
        require(scrobbles.size in 1..MAX_SCROBBLES_PER_REQUEST)
        val params = buildMap {
            put("method", "track.scrobble")
            put("sk", sessionKey)
            scrobbles.forEachIndexed { i, scrobble ->
                put("artist[$i]", scrobble.artist)
                put("track[$i]", scrobble.track)
                put("timestamp[$i]", scrobble.timestamp.toString())
                scrobble.album?.let { put("album[$i]", it) }
                scrobble.durationSec?.let { put("duration[$i]", it.toString()) }
            }
        }
        return when (val result = post(params)) {
            is ApiResult.Success ->
                if (result.value["scrobbles"] != null) ApiResult.Success(Unit)
                else ApiResult.Unavailable("scrobbles missing from response")

            is ApiResult.Error -> result
            is ApiResult.Unavailable -> result
        }
    }

    private suspend fun post(params: Map<String, String>): ApiResult<JsonObject> {
        val signed = params + ("api_key" to apiKey)
        val signature = signature(signed, sharedSecret)
        return try {
            val response = client.submitForm(
                url = API_URL,
                formParameters = parameters {
                    signed.forEach { (name, value) -> append(name, value) }
                    append("api_sig", signature)
                    append("format", "json")
                },
            )
            val body = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
            // Last.fm returns API errors in the body, with either a 200 or an error status.
            val errorCode = body?.get("error")?.jsonPrimitive?.intOrNull
            when {
                errorCode != null -> ApiResult.Error(
                    code = errorCode,
                    message = body["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )

                response.status.isSuccess() && body != null -> ApiResult.Success(body)
                else -> ApiResult.Unavailable("HTTP ${response.status.value}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Unavailable("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Username and key of an authenticated Last.fm session. */
    data class Session(val name: String, val key: String)

    companion object {
        const val MAX_SCROBBLES_PER_REQUEST = 50
        private const val AUTH_URL = "https://www.last.fm/api/auth/"
        private const val API_URL = "https://ws.audioscrobbler.com/2.0/"
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Computes the Last.fm API method signature.
         *
         * Sorts parameters by name and concatenates each name and value, followed by the shared
         * secret, then hashes the UTF-8 bytes with MD5. `format` and `callback` are excluded.
         *
         * See https://www.last.fm/api/authspec.
         */
        internal fun signature(params: Map<String, String>, sharedSecret: String): String {
            val text = buildString {
                params.filterKeys { it != "format" && it != "callback" }
                    .toSortedMap()
                    .forEach { (name, value) -> append(name).append(value) }
                append(sharedSecret)
            }
            return MessageDigest.getInstance("MD5")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

/** Result of a Last.fm API request. */
sealed interface ApiResult<out T> {
    /** Successful API request. */
    data class Success<T>(val value: T) : ApiResult<T>

    /** Last.fm answered with an error code (https://www.last.fm/api/errorcodes). */
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>

    /** No usable result due to a request failure or an invalid or incomplete response. */
    data class Unavailable(val reason: String) : ApiResult<Nothing>
}
