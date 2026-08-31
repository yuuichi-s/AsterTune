/*
 * Copyright (C) 2026 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.cipher

import android.util.Log
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches YouTube's base player.js and its 8-hex hash.
 *
 * The hash is read from an embed page, and the base.js URL is built as the `player_ias` / `en_US`
 * variant that the bundled cipher configs match. Other variants (such as the embed page's own
 * `player_embed` jsUrl) do not work with those configs, so the path is fixed rather than read from
 * `jsUrl`. The player.js is the same across videos for a given rollout, so the last fetched (hash,
 * code) pair is cached and reused; call [invalidate] to drop the cache when the player may have
 * rotated.
 */
object PlayerJsFetcher {

    private const val TAG = "PlayerJsFetcher"

    private val client = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()

    private val mutex = Mutex()

    @Volatile
    private var cached: Pair<String, String>? = null // hash to playerJs

    private val hashRegex = Regex("""/s/player/([0-9a-fA-F]{8})/""")

    internal data class PlayerJs(val hash: String, val code: String, val fromCache: Boolean)

    /**
     * Returns the player.js for the current web player, fetching it if not cached.
     *
     * @param videoId only used to load an embed page the current player hash is read from
     * @param forceRefresh fetch again even if a value is cached
     * @return the player.js (and whether it came from the cache), or null if it could not be fetched
     */
    internal suspend fun getPlayerJs(videoId: String, forceRefresh: Boolean = false): PlayerJs? =
        mutex.withLock {
            if (!forceRefresh) {
                cached?.let { return PlayerJs(it.first, it.second, fromCache = true) }
            }
            val result = runCatching { fetch(videoId) }.getOrElse {
                Log.e(TAG, "Failed to fetch player.js", it)
                null
            }
            if (result != null) {
                cached = result
            }
            result?.let { PlayerJs(it.first, it.second, fromCache = false) }
        }

    fun invalidate() {
        cached = null
    }

    private suspend fun fetch(videoId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val hash = fetchPlayerHash(videoId) ?: run {
            Log.e(TAG, "Could not resolve player hash for $videoId")
            return@withContext null
        }
        val url = "https://www.youtube.com/s/player/$hash/player_ias.vflset/en_US/base.js"
        Log.d(TAG, "Fetching player.js: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "player.js fetch failed: HTTP ${response.code}")
                return@withContext null
            }
            val code = response.body?.string() ?: return@withContext null
            Log.d(TAG, "player.js fetched: hash=$hash, ${code.length} chars")
            hash to code
        }
    }

    private fun fetchPlayerHash(videoId: String): String? {
        val request = Request.Builder()
            .url("https://www.youtube.com/embed/$videoId")
            .header("User-Agent", YouTubeClient.USER_AGENT_WEB)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "embed page fetch failed: HTTP ${response.code}")
                return null
            }
            val body = response.body?.string() ?: return null
            return hashRegex.find(body)?.groupValues?.get(1)
        }
    }
}
