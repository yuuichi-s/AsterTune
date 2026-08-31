package io.github.yuuichi_s.astertune.utils.potoken

import android.util.Log
import android.webkit.CookieManager
import io.github.yuuichi_s.astertune.App
import io.github.yuuichi_s.astertune.constants.POTOKEN_DEBUG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenVisitorData: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    /**
     * @param visitorData identifies the session the tokens are minted for. It must be the same
     * value the player request and the stream url end up carrying: the streaming (GVS) token is
     * bound to it, and a token bound to one identifier but presented by another session is honoured
     * only for the first stretch of a stream before every later read is refused.
     */
    fun getWebClientPoToken(videoId: String, visitorData: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) {
            return null
        }

        return try {
            runBlocking { getWebClientPoToken(videoId, visitorData, forceRecreate = false) }
        } catch (e: Exception) {
            when (e) {
                is BadWebViewException -> {
                    Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
                    webViewBadImpl = true
                    null
                }
                else -> throw e // includes PoTokenException
            }
        }
    }

    /**
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator], to be used in
     * case the current [webPoTokenGenerator] threw an error last time
     * [PoTokenWebView.generatePoToken] was called
     */
    private suspend fun getWebClientPoToken(videoId: String, visitorData: String, forceRecreate: Boolean): PoTokenResult {
        if (POTOKEN_DEBUG) Log.d(TAG, "Web poToken requested: $videoId, $visitorData")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired || webPoTokenVisitorData != visitorData

                if (shouldRecreate) {
                    webPoTokenVisitorData = visitorData

                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                    }

                    // create a new webPoTokenGenerator
                    webPoTokenGenerator = PoTokenWebView.getNewPoTokenGenerator(App.instance)

                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    webPoTokenStreamingPot = webPoTokenGenerator!!.generatePoToken(webPoTokenVisitorData!!)
                }

                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
            }

        val playerPot = try {
            // Not using synchronized here, since poTokenGenerator would be able to generate
            // multiple poTokens in parallel if needed. The only important thing is for exactly one
            // streaming poToken (based on [visitorData]) to be generated before anything else.
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                // the poTokenGenerator has just been recreated (and possibly this is already the
                // second time we try), so there is likely nothing we can do
                throw throwable
            } else {
                // retry, this time recreating the [webPoTokenGenerator] from scratch;
                // this might happen for example if the app goes in the background and the WebView
                // content is lost
                Log.e(TAG, "Failed to obtain poToken, retrying", throwable)
                return getWebClientPoToken(videoId = videoId, visitorData = visitorData, forceRecreate = true)
            }
        }

        if (POTOKEN_DEBUG) Log.d(TAG, "[$videoId] playerPot=$playerPot, streamingPot=$streamingPot")

        return PoTokenResult(visitorData, playerPot, streamingPot)
    }
}