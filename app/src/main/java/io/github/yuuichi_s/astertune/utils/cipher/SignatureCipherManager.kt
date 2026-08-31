/*
 * Copyright (C) 2026 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.cipher

import android.net.Uri
import android.util.Log
import io.github.yuuichi_s.astertune.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Deobfuscates a `signatureCipher` stream URL by running the player's own signature function in a
 * WebView loaded with the full player.js (see [CipherWebView]). Used as a fallback when NewPipe
 * cannot deobfuscate the signature for the current player.
 *
 * Both the signature and the `n` throttling parameter are handled.
 */
object SignatureCipherManager {

    private const val TAG = "SignatureCipherManager"
    private val nParamRegex = Regex("[?&]n=([^&]+)")

    private val mutex = Mutex()
    private var webView: CipherWebView? = null
    private var webViewHash: String? = null

    /**
     * Parses [signatureCipher] (its s / sp / url fields) and deobfuscates the signature for the
     * current player.
     *
     * @param videoId the video the cipher belongs to
     * @return the full stream URL with the signature appended, or null on failure
     */
    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? = mutex.withLock {
        val params = parseQuery(signatureCipher)
        val obfuscatedSig = params["s"]
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"]
        if (obfuscatedSig == null || baseUrl == null) {
            Log.e(TAG, "[$videoId] could not parse signatureCipher " +
                    "(s=${obfuscatedSig != null}, url=${baseUrl != null})")
            return@withLock null
        }

        // Try the cached player.js first (attempt 0). A cached player.js may be stale (the player
        // rotated while running), so on a cache hit with no config, or on a deobfuscation failure,
        // drop it and refetch once (attempt 1) before giving up.
        for (attempt in 0..1) {
            val forceRefresh = attempt > 0
            val (playerHash, playerJs, fromCache) = PlayerJsFetcher.getPlayerJs(videoId, forceRefresh) ?: run {
                Log.e(TAG, "[$videoId] could not fetch player.js")
                return@withLock null
            }

            var config = PlayerCipherConfigStore.get(playerHash)
            if (config == null) {
                if (attempt == 0 && fromCache) {
                    // The cached player.js may be stale; refetch once in case the current player is
                    // supported. A freshly fetched player.js is already current, so no retry there.
                    Log.w(TAG, "[$videoId] no config for cached player $playerHash; refetching once")
                    PlayerJsFetcher.invalidate()
                    continue
                }
                // player.js is current but unknown to us, so our config set is behind the rotation:
                // pull the upstream set (rate limited internally) before giving up.
                Log.w(TAG, "[$videoId] no cipher config for player $playerHash " +
                        "(${PlayerCipherConfigStore.knownHashes().size} known players); refreshing configs")
                if (PlayerCipherConfigStore.refreshFromRemote()) {
                    config = PlayerCipherConfigStore.get(playerHash)
                }
                if (config == null) {
                    Log.w(TAG, "[$videoId] player $playerHash still unsupported after config refresh")
                    return@withLock null
                }
            }

            val finalUrl = try {
                val cipher = getOrCreateWebView(playerHash, playerJs, config)
                val deobfuscatedSig = cipher.deobfuscateSignature(obfuscatedSig)
                val urlWithN = transformNParam(baseUrl, cipher, videoId)
                val separator = if ("?" in urlWithN) "&" else "?"
                "$urlWithN$separator$sigParam=${Uri.encode(deobfuscatedSig)}"
            } catch (e: Exception) {
                Log.e(TAG, "[$videoId] cipher deobfuscation failed (forceRefresh=$forceRefresh)", e)
                null
            }

            if (finalUrl != null) {
                Log.i(TAG, "[$videoId] stream url built via WebView (player $playerHash)")
                return@withLock finalUrl
            }
            if (attempt > 0) break

            // Deobfuscation failed; drop the cached code and the WebView so attempt 1 retries with a
            // fresh player.js and a rebuilt WebView.
            Log.w(TAG, "[$videoId] retrying with a refreshed player.js")
            PlayerJsFetcher.invalidate()
            closeWebView()
        }
        null
    }

    /**
     * Transforms the `n` throttling parameter in [url] using the player's n-transform.
     *
     * @return [url] with the transformed n, or the original [url] if there is no n or the transform
     *   fails
     */
    private suspend fun transformNParam(url: String, cipher: CipherWebView, videoId: String): String {
        val match = nParamRegex.find(url) ?: return url
        val encoded = match.groupValues[1]
        val original = Uri.decode(encoded)
        return try {
            val transformed = cipher.transformNParam(original)
            if (transformed.isEmpty() || transformed == original) {
                Log.w(TAG, "[$videoId] n parameter unchanged after transform")
                url
            } else {
                val leadChar = url[match.range.first]
                val prefix = url.substring(0, match.range.first)
                val suffix = url.substring(match.range.last + 1)
                Log.i(TAG, "[$videoId] n parameter transformed")
                "$prefix${leadChar}n=${Uri.encode(transformed)}$suffix"
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$videoId] n transform failed", e)
            url
        }
    }

    private suspend fun getOrCreateWebView(
        playerHash: String,
        playerJs: String,
        config: PlayerCipherConfig,
    ): CipherWebView {
        webView?.let { existing ->
            if (webViewHash == playerHash) return existing
            closeWebView()
        }
        val created = CipherWebView.create(App.instance, playerJs, config)
        webView = created
        webViewHash = playerHash
        return created
    }

    private suspend fun closeWebView() {
        webView?.let { existing -> withContext(Dispatchers.Main) { existing.close() } }
        webView = null
        webViewHash = null
    }

    private fun parseQuery(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = Uri.decode(pair.substring(0, idx))
                result[key] = Uri.decode(pair.substring(idx + 1))
            }
        }
        return result
    }
}
