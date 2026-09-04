/*
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils

import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.PlaybackException
import io.github.yuuichi_s.astertune.constants.AudioQuality
import io.github.yuuichi_s.astertune.utils.YTPlayerUtils.MAIN_CLIENT
import io.github.yuuichi_s.astertune.utils.YTPlayerUtils.STREAM_CLIENTS
import io.github.yuuichi_s.astertune.utils.YTPlayerUtils.validateStatus
import io.github.yuuichi_s.astertune.utils.cipher.SignatureCipherManager
import io.github.yuuichi_s.astertune.utils.potoken.PoTokenGenerator
import io.github.yuuichi_s.astertune.utils.potoken.PoTokenResult
import com.zionhuang.innertube.NewPipeUtils
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.zionhuang.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.zionhuang.innertube.models.YouTubeClient.Companion.IOS
import com.zionhuang.innertube.models.YouTubeClient.Companion.ORIGIN_YOUTUBE_MUSIC
import com.zionhuang.innertube.models.YouTubeClient.Companion.REFERER_YOUTUBE_MUSIC
import com.zionhuang.innertube.models.YouTubeClient.Companion.TVHTML5
import com.zionhuang.innertube.models.YouTubeClient.Companion.VISIONOS
import com.zionhuang.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.zionhuang.innertube.models.response.PlayerResponse
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

object YTPlayerUtils {

    private const val TAG = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    /**
     * Duration for skipping WEB_REMIX after a reported stream rejection for a video.
     *
     * The next resolution after this interval expires can try WEB_REMIX again.
     */
    private const val WEB_REMIX_FAILURE_TTL_MS = 5 * 60 * 1000L

    /** videoId -> when its WEB_REMIX stream was last rejected mid-playback. */
    private val webRemixFailures = ConcurrentHashMap<String, Long>()

    /**
     * Records that [videoId]'s WEB_REMIX stream url was refused while playing. Such a url passes
     * [validateStatus] and only fails later, so the resolver cannot tell it apart on its own; the
     * player has to report it back for the next resolution to move on to another client.
     */
    fun markWebRemixFailed(videoId: String) {
        webRemixFailures[videoId] = System.currentTimeMillis()
    }

    private fun hasRecentWebRemixFailure(videoId: String): Boolean {
        val failedAt = webRemixFailures[videoId] ?: return false
        if ((System.currentTimeMillis() - failedAt) !in 0 until WEB_REMIX_FAILURE_TTL_MS) {
            webRemixFailures.remove(videoId, failedAt)
            return false
        }
        return true
    }

    /**
     * Client for the initial player response and playback metadata.
     *
     * Uses WEB_REMIX, which includes the configured session cookie when available. Metadata
     * continues to come from this response even if another client supplies the stream.
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Clients tried in order when resolving the playable stream.
     *
     * Metadata comes from [MAIN_CLIENT]. Its initial response is reused when stream resolution
     * reaches that client, rather than issuing another player request.
     */
    private val STREAM_CLIENTS: Array<YouTubeClient> = arrayOf(
        VISIONOS,
        ANDROID_VR_1_65_10,
        ANDROID_VR_1_43_32,
        WEB_REMIX,
        TVHTML5,
        IOS,
    )


    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        /** Client that produced [streamUrl], so the player can report it back if the url is refused. */
        val streamClient: String,
        /**
         * Headers the media request has to carry. googlevideo issues a url on behalf of a specific
         * client and expects the fetch to look like it came from that client; a request with the
         * http library's own defaults is served briefly and then refused.
         */
        val streamHeaders: Map<String, String>,
    )

    /** Identifies a media request as coming from the client the stream url was issued for. */
    private fun YouTubeClient.streamHeaders(): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        put("Accept", "*/*")
        put("Accept-Language", "en-US,en;q=0.9")
        when (clientName) {
            "WEB_REMIX" -> {
                put("Referer", REFERER_YOUTUBE_MUSIC)
                put("Origin", ORIGIN_YOUTUBE_MUSIC)
            }

            "WEB_CREATOR" -> {
                put("Referer", "https://studio.youtube.com/")
                put("Origin", "https://studio.youtube.com")
            }

            else -> {
                put("Referer", "https://www.youtube.com/")
                put("Origin", "https://www.youtube.com")
            }
        }
    }

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        Log.d(TAG, "Playback info requested: $videoId")

        // Required for some clients to get working streams, but not forced for MAIN_CLIENT: its
        // response is needed even when its streams won't work, so this is allowed to be null.
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        val isLoggedIn = YouTube.cookie != null
        // The streaming (GVS) token is minted against this and the stream url is then fetched by a
        // session identifying itself with visitorData, so this has to be visitorData for both
        // signed-in and signed-out sessions. Binding it to dataSyncId while requests carry
        // visitorData is honoured for roughly the first minute of a track and refused after that.
        val sessionId = YouTube.visitorData

        Log.d(TAG, "[$videoId] signatureTimestamp: $signatureTimestamp, isLoggedIn: $isLoggedIn, " +
                "dataSyncId present: ${!YouTube.dataSyncId.isNullOrBlank()} (len=${YouTube.dataSyncId?.length ?: 0}), " +
                "visitorData present: ${!YouTube.visitorData.isNullOrBlank()}")

        val (webPlayerPot, webStreamingPot) = getWebClientPoTokenOrNull(videoId, sessionId)?.let {
            Pair(it.playerRequestPoToken, it.streamingDataPoToken)
        } ?: Pair(null, null).also {
            Log.w(TAG, "[$videoId] No po token")
        }

        val mainPlayerResponse =
            YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp, webPlayerPot)
                .getOrThrow()

        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking

        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamClient: String? = null
        var streamHeaders: Map<String, String> = emptyMap()

        var streamPlayerResponse: PlayerResponse? = null
        for ((clientIndex, client) in STREAM_CLIENTS.withIndex()) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null
            streamClient = null
            streamHeaders = emptyMap()

            Log.d(TAG, "Trying stream client ${clientIndex + 1}/${STREAM_CLIENTS.size}: ${client.clientName}")

            if (client.loginRequired && !isLoggedIn) {
                // skip client if it requires login but user is not logged in
                continue
            }
            if (client.clientName == "WEB_REMIX" && hasRecentWebRemixFailure(videoId)) {
                Log.d(TAG, "[$videoId] skipping WEB_REMIX after a rejected stream")
                continue
            }

            // decide which client to use for streams and load its player response
            if (client == MAIN_CLIENT) {
                // its response was already fetched for the metadata
                streamPlayerResponse = mainPlayerResponse
            } else {
                val playerResult =
                    YouTube.player(videoId, playlistId, client, signatureTimestamp, webPlayerPot)
                playerResult.exceptionOrNull()?.let {
                    Log.e(TAG, "[$videoId] [${client.clientName}] player request failed", it)
                }
                streamPlayerResponse = playerResult.getOrNull()
            }

            Log.d(TAG, "[$videoId] stream client: ${client.clientName}, " +
                    "playabilityStatus: ${streamPlayerResponse?.playabilityStatus?.let {
                        it.status + (it.reason?.let { " - $it" } ?: "")
                    }}")

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                format = findFormat(streamPlayerResponse, audioQuality, connectivityManager)
                if (format == null) {
                    Log.w(TAG, "[$videoId] [${client.clientName}] OK but no audio format found")
                    continue
                }
                streamUrl = findUrlOrNull(format, videoId)
                if (streamUrl == null) {
                    Log.w(TAG, "[$videoId] [${client.clientName}] OK but failed to build stream url (deobfuscation?)")
                    continue
                }
                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Log.w(TAG, "[$videoId] [${client.clientName}] OK but missing expiresInSeconds")
                    continue
                }

                if (client.useWebPoTokens && webStreamingPot != null) {
                    streamUrl += "&pot=$webStreamingPot";
                }

                streamClient = client.clientName
                streamHeaders = client.streamHeaders()

                if (clientIndex == STREAM_CLIENTS.lastIndex) {
                    // skip validateStatus for the last client
                    break
                }
                if (validateStatus(streamUrl, streamHeaders)) {
                    // working stream found
                    Log.i(TAG, "[$videoId] [${client.clientName}] found working stream")
                    break
                } else {
                    Log.w(TAG, "[$videoId] [${client.clientName}] got bad http status code")
                }
            }
        }

        if (streamPlayerResponse == null) {
            throw Exception("Bad stream player response")
        }
        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            throw PlaybackException(
                streamPlayerResponse.playabilityStatus.reason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }
        if (streamExpiresInSeconds == null) {
            throw Exception("Missing stream expire time")
        }
        if (format == null) {
            throw Exception("Could not find format")
        }
        if (streamUrl == null) {
            throw Exception("Could not find stream url")
        }

        Log.d(TAG, "[$videoId] stream url: $streamUrl")

        PlaybackData(
            audioConfig = audioConfig,
            videoDetails = videoDetails,
            playbackTracking = playbackTracking,
            format = format,
            streamUrl = streamUrl,
            streamExpiresInSeconds = streamExpiresInSeconds,
            streamClient = streamClient ?: MAIN_CLIENT.clientName,
            streamHeaders = streamHeaders,
        )
    }

    /**
     * Fetches a WEB_REMIX player response for non-streaming data, including
     * video metadata and playback tracking.
     *
     * Streaming URLs from this response are not guaranteed to be playable.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        // WEB_REMIX provides the playback tracking URL required for history registration.
        // Include the web player integrity fields because omitting the player PoToken may
        // cause the request to return UNPLAYABLE.
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        val webPlayerPot = getWebClientPoTokenOrNull(videoId, YouTube.visitorData)?.playerRequestPoToken
        return YouTube.player(videoId, playlistId, WEB_REMIX, signatureTimestamp, webPlayerPot)
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? =
        playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            }

    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(url: String, headers: Map<String, String>): Boolean {
        try {
            // googlevideo often rejects HEAD with 403 even when the stream plays; validate with a
            // tiny ranged GET instead, which is how the player actually fetches the media.
            val requestBuilder = okhttp3.Request.Builder()
                .header("Range", "bytes=0-0")
                .url(url)
            headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val ok = response.isSuccessful
            if (!ok) {
                // logged at warn so release builds still show why a client was dropped
                Log.w(TAG, "stream validation failed: HTTP ${response.code}")
            }
            response.close()
            return ok
        } catch (e: Exception) {
            reportException(e)
        }
        return false
    }

    // Reports exceptions; returns null on failure.
    private fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onFailure {
                reportException(it)
            }
            .getOrNull()
    }

    /**
     * Resolves an audio stream URL with NewPipe, then the WebView cipher fallback if needed.
     *
     * @param videoId ID of the video [format] belongs to
     * @return The resolved stream URL, or null if no URL could be resolved
     * @throws kotlinx.coroutines.CancellationException If coroutine cancellation propagates from
     *     a suspending resolution step
     */
    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        val npResult = NewPipeUtils.getStreamUrl(format, videoId)
        npResult.getOrNull()?.let { return it }

        // fallback for cipher formats: deobfuscate in a WebView (see SignatureCipherManager)
        val signatureCipher = format.signatureCipher
        if (signatureCipher != null) {
            val url = SignatureCipherManager.deobfuscateStreamUrl(signatureCipher, videoId)
            if (url != null) return url
        }

        npResult.exceptionOrNull()?.let {
            Log.e(TAG, "[$videoId] getStreamUrl failed (itag=${format.itag}, hasUrl=${format.url != null}, hasCipher=${format.signatureCipher != null})", it)
            reportException(it)
        }
        return null
    }

    // Reports exceptions; returns null on failure.
    private fun getWebClientPoTokenOrNull(videoId: String, visitorData: String?): PoTokenResult? {
        if (visitorData == null) {
            Log.d(TAG, "[$videoId] visitorData is null")
            return null
        }
        try {
            return poTokenGenerator.getWebClientPoToken(videoId, visitorData)
        } catch (e: Exception) {
            reportException(e)
        }
        return null
    }
}
