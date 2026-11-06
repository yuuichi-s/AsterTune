package io.github.yuuichi_s.astertune.utils

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import coil3.EventListener
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.size.pxOrElse
import com.zionhuang.innertube.models.ThumbnailSizeLog
import io.github.yuuichi_s.astertune.constants.IMAGE_DEBUG
import io.github.yuuichi_s.astertune.ui.utils.resize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

const val IMAGE_TAG = "ImageDebug"

internal fun initializeImageLoadDebug(context: Context, imageLoader: () -> ImageLoader) {
    if (!IMAGE_DEBUG) return
    ThumbnailSizeLog.enabled = true
    ImageLoadDebug.logMemoryClass(context, imageLoader())
}

internal fun ImageLoader.Builder.debugEventListener(imageLoader: () -> ImageLoader) = apply {
    if (IMAGE_DEBUG) eventListenerFactory(ImageLoadDebug.factory(imageLoader))
}

@Composable
internal fun ObserveImageLoadNavigation(navController: NavController) {
    if (!IMAGE_DEBUG) return
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            ImageLoadDebug.onScreen(destination.route)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}

/**
 * Debug logging for image loading, enabled by [IMAGE_DEBUG].
 *
 * Each finished request is logged as one line with the screen it was made on, where the image
 * came from, and the time spent in each stage measured from `onStart`.
 */
object ImageLoadDebug {
    @Volatile
    private var screen: String = "-"

    @Volatile
    private var screenSince: Long = SystemClock.elapsedRealtime()

    /** Records the navigation destination that later requests are attributed to. */
    fun onScreen(route: String?) {
        if (!IMAGE_DEBUG) return
        screen = route ?: "-"
        screenSince = SystemClock.elapsedRealtime()
        Log.d(IMAGE_TAG, "screen route=$screen")
        frameUntil = screenSince + FRAME_LOG_MS
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private const val FRAME_LOG_MS = 1000L

    @Volatile
    private var frameUntil = 0L

    /** Logs the start of each frame for a while after a screen change. */
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val now = SystemClock.elapsedRealtime()
            Log.d(IMAGE_TAG, "frame screen=$screen sinceScreen=${now - screenSince}ms")
            if (now < frameUntil) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private const val MAX_PROBES = 15
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val probed = HashSet<String>()

    /**
     * Downloads [url] once at the requested size and once as is, and logs the time and bytes of each.
     */
    private fun probe(url: String, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        synchronized(probed) {
            if (probed.size >= MAX_PROBES || !probed.add(url)) return
        }
        val small = url.resize(width, height)
        if (small == url) return
        probeScope.launch {
            fun download(target: String): String = try {
                val start = SystemClock.elapsedRealtime()
                val connection = URL(target).openConnection() as HttpURLConnection
                val bytes = connection.inputStream.use { it.readBytes().size }
                connection.disconnect()
                "${SystemClock.elapsedRealtime() - start}ms/${bytes}B"
            } catch (e: Exception) {
                "error=${e.javaClass.simpleName}"
            }
            val smallResult = download(small)
            val largeResult = download(url)
            Log.d(IMAGE_TAG, "probe req=${width}x$height small=$smallResult large=$largeResult url=$url")
        }
    }

    fun logMemoryClass(context: Context, imageLoader: ImageLoader) {
        if (!IMAGE_DEBUG) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val maxBytes = imageLoader.memoryCache?.maxSize ?: 0L
        Log.d(
            IMAGE_TAG,
            "memory memoryClass=${am.memoryClass}MB largeMemoryClass=${am.largeMemoryClass}MB " +
                "isLowRam=${am.isLowRamDevice} cacheMax=${maxBytes / 1024 / 1024}MB"
        )
    }

    fun factory(imageLoader: () -> ImageLoader) = EventListener.Factory { request ->
        Listener(imageLoader, screen, SystemClock.elapsedRealtime() - screenSince)
    }

    private class Listener(
        private val imageLoader: () -> ImageLoader,
        private val screen: String,
        private val sinceScreen: Long,
    ) : EventListener() {
        private var start = 0L
        private var sizeEnd = -1L
        private var keyStart = -1L
        private var keyEnd = -1L
        private var fetchEnd = -1L
        private var decodeEnd = -1L
        private var size: Size? = null
        private var mapped: Any? = null

        private fun now() = System.nanoTime()
        private fun ms(t: Long) = if (t < 0) "-" else "%.1f".format((t - start) / 1_000_000.0)

        override fun onStart(request: ImageRequest) {
            start = now()
        }

        override fun resolveSizeEnd(request: ImageRequest, size: Size) {
            sizeEnd = now()
            this.size = size
        }

        override fun mapEnd(request: ImageRequest, output: Any) {
            mapped = output
        }

        override fun keyStart(request: ImageRequest, input: Any) {
            keyStart = now()
        }

        override fun keyEnd(request: ImageRequest, output: String?) {
            keyEnd = now()
        }

        override fun fetchEnd(
            request: ImageRequest,
            fetcher: Fetcher,
            options: Options,
            result: FetchResult?
        ) {
            fetchEnd = now()
        }

        override fun decodeEnd(
            request: ImageRequest,
            decoder: Decoder,
            options: Options,
            result: DecodeResult?
        ) {
            decodeEnd = now()
        }

        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
            log(request, "ok src=${result.dataSource} img=${result.image.width}x${result.image.height}")
            val url = request.data as? String
            if (url != null && url.contains("=w1080-h1080")) {
                val s = size
                if (s != null) probe(url, s.width.pxOrElse { -1 }, s.height.pxOrElse { -1 })
            }
        }

        override fun onError(request: ImageRequest, result: ErrorResult) {
            log(request, "error=${result.throwable.javaClass.simpleName}")
        }

        override fun onCancel(request: ImageRequest) {
            log(request, "cancel")
        }

        private fun log(request: ImageRequest, outcome: String) {
            val end = now()
            val data = request.data
            val (kind, desc) = when (data) {
                is LocalArtworkPath -> {
                    val m = mapped as? LocalArtworkPath ?: data
                    "local" to "req=${m.x}x${m.y} path=${data.path}"
                }

                else -> {
                    val url = data.toString()
                    val kind = if (url.startsWith("/")) "local-str" else "online"
                    val cand = ThumbnailSizeLog.sizeOf(url)
                    kind to "cand=${cand ?: "-"} url=$url"
                }
            }
            val cache = imageLoader().memoryCache
            Log.d(
                IMAGE_TAG,
                "load kind=$kind screen=$screen sinceScreen=${sinceScreen}ms target=${request.target != null} " +
                    "size=$size $outcome total=${ms(end)} sizeEnd=${ms(sizeEnd)} keyStart=${ms(keyStart)} " +
                    "keyEnd=${ms(keyEnd)} fetchEnd=${ms(fetchEnd)} decodeEnd=${ms(decodeEnd)} " +
                    "mem=${(cache?.size ?: 0) / 1024}/${(cache?.maxSize ?: 0) / 1024}KB $desc"
            )
        }
    }
}
