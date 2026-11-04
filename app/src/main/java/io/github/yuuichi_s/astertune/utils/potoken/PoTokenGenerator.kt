package io.github.yuuichi_s.astertune.utils.potoken

import android.util.Log
import android.webkit.CookieManager
import io.github.yuuichi_s.astertune.App
import io.github.yuuichi_s.astertune.constants.POTOKEN_DEBUG
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private class CachedWebPoToken(
        val visitorData: String,
        val webView: PoTokenWebView,
        val streamingToken: String,
    )

    private val webPoTokenGenLock = Mutex()
    private var cachedWebPoToken: CachedWebPoToken? = null

    /**
     * @param visitorData identifies the session the tokens are minted for. It must be the same
     * value the player request and the stream url end up carrying: the streaming (GVS) token is
     * bound to it, and a token bound to one identifier but presented by another session is honoured
     * only for the first stretch of a stream before every later read is refused.
     */
    suspend fun getWebClientPoToken(videoId: String, visitorData: String): PoTokenResult? {
        val attempt = withTimeoutOrNull(PO_TOKEN_TIMEOUT_MS) {
            // Keep ownership of the WebView on Main through initialization and cleanup.
            val token = withContext(Dispatchers.Main) {
                webPoTokenGenLock.withLock {
                    currentCoroutineContext().ensureActive()
                    if (!webViewSupported || webViewBadImpl) {
                        return@withLock null
                    }

                    try {
                        generateWebClientPoToken(videoId, visitorData)
                    } catch (e: BadWebViewException) {
                        Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
                        webViewBadImpl = true
                        null
                    }
                }
            }
            Result.success(token)
        }
        if (attempt == null) {
            Log.w(TAG, "[$videoId] PoToken request timed out after ${PO_TOKEN_TIMEOUT_MS}ms")
        }
        return attempt?.getOrThrow()
    }

    private suspend fun generateWebClientPoToken(videoId: String, visitorData: String): PoTokenResult {
        if (POTOKEN_DEBUG) Log.d(TAG, "Web poToken requested: $videoId, $visitorData")

        val cached = cachedWebPoToken?.takeIf { it.visitorData == visitorData && !it.webView.isExpired }
        val session = cached ?: createWebPoTokenSession(visitorData)

        val playerPot = try {
            session.webView.generatePoToken(videoId)
        } catch (error: Throwable) {
            clearWebPoTokenSession()
            if (error is CancellationException || cached == null) {
                throw error
            }

            // Retry a failed cached WebView once, under the same lock and deadline.
            Log.e(TAG, "Failed to obtain poToken, retrying", error)
            return generateWebClientPoToken(videoId, visitorData)
        }

        if (POTOKEN_DEBUG) {
            Log.d(TAG, "[$videoId] playerPot=$playerPot, streamingPot=${session.streamingToken}")
        }

        return PoTokenResult(visitorData, playerPot, session.streamingToken)
    }

    private suspend fun createWebPoTokenSession(visitorData: String): CachedWebPoToken {
        clearWebPoTokenSession()
        currentCoroutineContext().ensureActive()
        val webView = PoTokenWebView.getNewPoTokenGenerator(App.instance)
        val session = try {
            // The streaming token must be generated before any player tokens.
            val streamingToken = webView.generatePoToken(visitorData)
            CachedWebPoToken(visitorData, webView, streamingToken)
        } catch (error: Throwable) {
            webView.close()
            throw error
        }
        cachedWebPoToken = session
        return session
    }

    private fun clearWebPoTokenSession() {
        val cached = cachedWebPoToken
        cachedWebPoToken = null
        cached?.webView?.close()
    }

    private companion object {
        const val PO_TOKEN_TIMEOUT_MS = 10_000L
    }
}
