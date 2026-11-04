package io.github.yuuichi_s.astertune.utils.potoken

import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.zionhuang.innertube.YouTube
import io.github.yuuichi_s.astertune.BuildConfig
import io.github.yuuichi_s.astertune.constants.POTOKEN_DEBUG
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PoTokenWebView private constructor(context: Context) {
    private val webView = WebView(context)
    private val scope = MainScope()
    private var initialization: CancellableContinuation<Unit>? = null
    private var pendingPoToken: Pair<String, CancellableContinuation<String>>? = null
    private var botGuardCall: Call? = null
    private var closed = false
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        scope.launch { close(error) }
    }
    private lateinit var expirationInstant: Instant

    //region Initialization
    private fun configureWebView() {
        val webViewSettings = webView.settings
        //noinspection SetJavaScriptEnabled we want to use JavaScript!
        webViewSettings.javaScriptEnabled = true
//        webViewSettings.safeBrowsingEnabled = false // sdk24 support
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webViewSettings, false)
        }
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true // the WebView does not need internet access

        // so that we can run async functions and get back the result
        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.message().contains("Uncaught")) {
                    // There should not be any uncaught errors while executing the code, because
                    // everything that can fail is guarded by try-catch. Therefore, this likely
                    // indicates that there was a syntax error in the code, i.e. the WebView only
                    // supports a really old version of JS.

                    val fmt = "\"${m.message()}\", source: ${m.sourceId()} (${m.lineNumber()})"
                    val exception = BadWebViewException(fmt)
                    Log.e(TAG, "This WebView implementation is broken: $fmt")

                    close(exception)
                    return true
                }
                return super.onConsoleMessage(m)
            }
        }
    }

    /**
     * Must be called right after instantiating [PoTokenWebView] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an `integrityToken`.
     */
    private fun loadHtmlAndObtainBotguard() {
        if (POTOKEN_DEBUG) Log.d(TAG, "loadHtmlAndObtainBotguard() called")

        scope.launch(exceptionHandler) {
            val html = withContext(Dispatchers.IO) {
                webView.context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            }

            // calls downloadAndRunBotguard() when the page has finished loading
            val data = html.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>")
            webView.loadDataWithBaseURL("https://www.youtube.com", data, "text/html", "utf-8", null)
        }
    }

    /**
     * Called during initialization by the JavaScript snippet appended to the HTML page content in
     * [loadHtmlAndObtainBotguard] after the WebView content has been loaded.
     */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        if (POTOKEN_DEBUG) Log.d(TAG, "downloadAndRunBotguard() called")

        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/Create",
            "[ \"$REQUEST_KEY\" ]",
        ) { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            webView.evaluateJavascript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    /**
     * Called during initialization by the JavaScript snippets from either
     * [downloadAndRunBotguard] or [onRunBotguardResult].
     */
    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Initialization error from JavaScript: $error")
        }
        scope.launch { close(buildExceptionForJsError(error)) }
    }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard] after
     * obtaining the BotGuard execution output [botguardResponse].
     */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        if (POTOKEN_DEBUG) Log.d(TAG, "botguardResponse: $botguardResponse")
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { responseBody ->
            if (POTOKEN_DEBUG) Log.d(TAG, "GenerateIT response: $responseBody")
            val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)

            // leave 10 minutes of margin just to be sure
            expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds).minus(10, ChronoUnit.MINUTES)

            webView.evaluateJavascript("this.integrityToken = $integrityToken") {
                val continuation = initialization ?: return@evaluateJavascript
                initialization = null
                if (POTOKEN_DEBUG) Log.d(TAG, "initialization finished, expiration=${expirationTimeInSeconds}s")
                continuation.resume(Unit)
            }
        }
    }
    //endregion

    //region Obtaining poTokens
    @MainThread
    suspend fun generatePoToken(identifier: String): String {
        check(!closed) { "PoToken WebView is closed" }
        check(pendingPoToken == null) { "A PoToken request is already pending" }

        return suspendCancellableCoroutine { cont ->
            if (POTOKEN_DEBUG) Log.d(TAG, "generatePoToken() called with identifier $identifier")
            pendingPoToken = identifier to cont
            webView.evaluateJavascript(
                """try {
                        identifier = "$identifier"
                        u8Identifier = ${stringToU8(identifier)}
                        poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, u8Identifier)
                        poTokenU8String = poTokenU8.join(",")
                        $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                    }""",
                null
            )
        }
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] when an error occurs in calling the
     * JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "obtainPoToken error from JavaScript: $error")
        }
        scope.launch {
            takePoTokenRequest(identifier)?.resumeWithException(buildExceptionForJsError(error))
        }
    }

    /**
     * Called by the JavaScript snippet from [generatePoToken] with the original identifier and the
     * result of the JavaScript `obtainPoToken()` function.
     */
    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        if (POTOKEN_DEBUG) Log.d(TAG, "Generated poToken (before decoding): identifier=$identifier poTokenU8=$poTokenU8")
        val result = runCatching { u8ToBase64(poTokenU8) }
        scope.launch {
            takePoTokenRequest(identifier)?.resumeWith(result)
        }
    }

    val isExpired: Boolean
        get() = closed || Instant.now().isAfter(expirationInstant)

    private fun takePoTokenRequest(identifier: String): CancellableContinuation<String>? {
        val request = pendingPoToken ?: return null
        if (request.first != identifier) return null

        pendingPoToken = null
        return request.second
    }
    //endregion

    //region Utils
    /**
     * Makes a BotGuard request during initialization and delivers the response body on Main.
     * The exception handler closes the WebView if the request fails.
     */
    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit,
    ) {
        scope.launch(exceptionHandler) {
            val request = okhttp3.Request.Builder()
                .post(data.toRequestBody())
                .headers(mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "application/json",
                    "Content-Type" to "application/json+protobuf",
                    "x-goog-api-key" to GOOGLE_API_KEY,
                    "x-user-agent" to "grpc-web-javascript/0.1",
                ).toHeaders())
                .url(url)
                .build()
            val call = httpClient.newCall(request)
            botGuardCall = call
            val body = try {
                withContext(Dispatchers.IO) {
                    call.execute().use { response ->
                        if (response.code != 200) {
                            throw PoTokenException("Invalid response code: ${response.code}")
                        }
                        response.body!!.string()
                    }
                }
            } finally {
                botGuardCall = null
            }
            handleResponseBody(body)
        }
    }

    /**
     * Releases the WebView and fails any pending request.
     */
    @MainThread
    fun close(error: Throwable = PoTokenException("PoToken WebView was closed")) {
        if (closed) return

        closed = true
        val initializing = initialization
        val generating = pendingPoToken?.second
        initialization = null
        pendingPoToken = null
        scope.cancel()
        botGuardCall?.cancel()
        botGuardCall = null

        try {
            webView.destroy()
        } finally {
            initializing?.resumeWithException(error)
            generating?.resumeWithException(error)
        }
    }
    //endregion

    companion object {
        private const val TAG = "PoTokenWebView"
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        private val httpClient = OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .build()

        @MainThread
        suspend fun getNewPoTokenGenerator(context: Context): PoTokenWebView {
            val webView = PoTokenWebView(context)
            try {
                webView.configureWebView()
                suspendCancellableCoroutine { continuation ->
                    webView.initialization = continuation
                    webView.loadHtmlAndObtainBotguard()
                }
                return webView
            } catch (error: Throwable) {
                webView.close()
                throw error
            }
        }
    }
}
