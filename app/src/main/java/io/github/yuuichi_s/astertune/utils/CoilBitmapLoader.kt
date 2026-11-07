/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.session.MediaSession
import coil3.imageLoader
import coil3.intercept.Interceptor
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.Options
import coil3.request.allowHardware
import coil3.size.pxOrElse
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import io.github.yuuichi_s.astertune.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.math.min

class CoilBitmapLoader @Inject constructor(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(coilCoroutine),
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future {
            BitmapFactory.decodeByteArray(data, 0, data.size) ?: drawPlaceholder(context)
        }

    /**
     * Loads artwork for the media session and notification.
     *
     * Requests artwork at Media3's size limit to reduce decoding work.
     * Disables Coil's memory cache because Media3 caches the artwork.
     */
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future {
            try {
                val dimensionLimit = MediaSession.getBitmapDimensionLimit(context)
                // local images
                val data: Any = if (uri.toString().startsWith("/storage/")) {
                    LocalArtworkPath(uri.toString())
                } else {
                    uri
                }
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(data)
                        .size(dimensionLimit)
                        .allowHardware(false)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build()
                )
                if (result is ErrorResult) {
                    reportException(ExecutionException(result.throwable))
                    return@future drawPlaceholder(context)
                }

                result.image!!.toBitmap()
            } catch (e: Exception) {
                reportException(ExecutionException(e))
                return@future drawPlaceholder(context)
            }
        }

    companion object {
        // TODO: re eval dimens after a few months
        /**
         * Draw a centered square app icon with the maximum possible size while maintaining aspect ratio.
         *
         * @param context
         * @param x Desired final x dimension
         * @param y Desired final y dimension
         * @param size Percentage size of valid draw frame. Must be a value between 0.0 and 1.0. For example, 0.8
         *      means that inner frame should be 80% of the size of the final frame, and centered within that frame.
         */
        fun drawPlaceholder(context: Context, x: Int = 2000, y: Int = 2000, size: Float = 0.8f): Bitmap {
            val padding = size.coerceIn(0f, 1f)
            val innerRecWidth = x * padding
            val innerRecHeight = y * padding

            val squareLength = min(innerRecWidth, innerRecHeight).toInt()
            val squareLeft = ((x - squareLength) / 2)
            val squareTop = ((y - squareLength) / 2)

            val drawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.app_logo)
            val bitmap = createBitmap(x, y, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawable?.setBounds(squareLeft, squareTop, squareLeft + squareLength, squareTop + squareLength)
            drawable?.draw(canvas)
            return bitmap
        }
    }
}

/**
 * Maps Coil's resolved request dimensions to local artwork whose dimensions are unspecified.
 *
 * The mapped dimensions are used by [LocalArtworkPathKeyer] and [LocalArtworkFetcher].
 */
class LocalArtworkPathSizeMapper : Mapper<LocalArtworkPath, LocalArtworkPath> {
    override fun map(
        data: LocalArtworkPath,
        options: Options
    ): LocalArtworkPath? {
        if (data.x > 0 || data.y > 0) return null

        val x = options.size.width.pxOrElse { -1 }
        val y = options.size.height.pxOrElse { -1 }
        if (x <= 0 && y <= 0) return null

        return data.copy(x = x, y = y)
    }
}

/**
 * Creates memory cache keys for local artwork.
 *
 * Includes the audio file's last-modified time so later requests do not reuse outdated artwork.
 */
class LocalArtworkPathKeyer : Keyer<LocalArtworkPath> {
    override fun key(
        data: LocalArtworkPath,
        options: Options
    ): String {
        val lastModified = data.path?.let { File(it).lastModified() } ?: 0L
        val key = "${data.path};${data.x};${data.y};$lastModified"

        return if (data.x > 0 || data.y > 0) {
            "$key;${options.scale}"
        } else {
            key
        }
    }
}

/**
 * Runs the interceptor chain for local artwork on [Dispatchers.IO].
 *
 * Coil creates memory cache keys before switching to the fetcher dispatcher, so this keeps
 * [LocalArtworkPathKeyer]'s file metadata lookup off the main thread.
 */
class LocalArtworkPathInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        if (chain.request.data !is LocalArtworkPath) {
            return chain.proceed()
        }

        return withContext(Dispatchers.IO) { chain.proceed() }
    }
}

data class LocalArtworkPath(val path: String?, val x: Int = -1, val y: Int = -1)
