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
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.media3.common.util.BitmapLoader
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.imageLoader
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.size.pxOrElse
import coil3.toBitmap
import io.github.yuuichi_s.astertune.R
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

class CoilBitmapLoader @Inject constructor(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(coilCoroutine),
    private val data: LocalArtworkPath = LocalArtworkPath(null),
    private val targetScale: Scale = Scale.FIT,
) : Fetcher, BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future {
            BitmapFactory.decodeByteArray(data, 0, data.size) ?: drawPlaceholder(context)
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future {
            try {
                // local images
                val result = if (uri.toString().startsWith("/storage/")) {
                    context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(LocalArtworkPath(uri.toString()))
                            .allowHardware(false)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build()
                    )
                } else {
                    context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(uri)
                            .allowHardware(false)
                            .build()
                    )
                }
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

    override suspend fun fetch(): FetchResult? {
        return try {
            if (data.path?.startsWith("/storage/") == true) {
                var subsampled = false
                var image: Bitmap = try {
                    val art = embeddedPicture(data.path)!!
                    val sampleSize = sampleSize(art)
                    val decoded = BitmapFactory.decodeByteArray(
                        art, 0, art.size,
                        BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    )
                    subsampled = decoded != null && sampleSize > 1
                    decoded
                } catch (e: Exception) {
                    framePlaceholder()
                } ?: framePlaceholder()

                val factor = scaleFactor(image.width, image.height)
                val scaled = factor < 1f
                if (scaled) {
                    image = image.scale(
                        (image.width * factor).roundToInt().coerceAtLeast(1),
                        (image.height * factor).roundToInt().coerceAtLeast(1)
                    )
                }

                ImageFetchResult(
                    image = image.asImage(),
                    isSampled = subsampled || scaled,
                    dataSource = DataSource.DISK
                )
            } else {
                null
            }
        } catch (e: Exception) {
            reportException(e)
            ImageFetchResult(
                image = framePlaceholder().asImage(),
                isSampled = false,
                dataSource = DataSource.MEMORY
            )
        }
    }

    /**
     * Reads the artwork embedded in the audio file at [path].
     *
     * [MediaMetadataRetriever] does not implement `AutoCloseable` below API 29,
     * so it is released in a `finally` block instead of with `use`.
     */
    private fun embeddedPicture(path: String?): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        }
    }

    /**
     * Calculates the power-of-two sample size for decoding [art].
     *
     * Returns the largest sample size that keeps the decoded bitmap large enough for
     * [targetScale], allowing [fetch] to apply only a final downscale when needed.
     */
    private fun sampleSize(art: ByteArray): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(art, 0, art.size, bounds)

        val factor = scaleFactor(bounds.outWidth, bounds.outHeight)
        if (factor <= 0f || factor >= 1f) return 1

        var sampleSize = 1
        while (sampleSize * 2 <= 1f / factor) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Calculate the magnification factor to be applied to the original image dimensions
     *
     * The image will fit within the specified frame while maintaining its aspect ratio,
     * or it will be displayed to fill the entire frame.
     */
    private fun scaleFactor(width: Int, height: Int): Float {
        if (width <= 0 || height <= 0) return 1f

        val fitX = if (data.x > 0) data.x.toFloat() / width else null
        val fitY = if (data.y > 0) data.y.toFloat() / height else null
        val factor = when {
            fitX != null && fitY != null ->
                if (targetScale == Scale.FILL) maxOf(fitX, fitY) else minOf(fitX, fitY)

            else -> fitX ?: fitY ?: return 1f
        }

        return factor.coerceAtMost(1f)
    }

    private fun framePlaceholder(): Bitmap {
        val side = maxOf(data.x, data.y)
        return if (side > 0) drawPlaceholder(context, side, side) else drawPlaceholder(context)
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

            val drawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.placeholder_icon)
            val bitmap = Bitmap.createBitmap(x, y, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawable?.setBounds(squareLeft, squareTop, squareLeft + squareLength, squareTop + squareLength)
            drawable?.draw(canvas)
            return bitmap
        }
    }

    class Factory(
        private val context: Context,
    ) : Fetcher.Factory<LocalArtworkPath> {
        override fun create(data: LocalArtworkPath, options: Options, imageLoader: ImageLoader): Fetcher? {
            return CoilBitmapLoader(context, data = data, targetScale = options.scale)
        }
    }
}

/**
 * Maps Coil's resolved request dimensions to local artwork whose dimensions are unspecified.
 *
 * The mapped dimensions are used by [LocalArtworkPathKeyer] and [CoilBitmapLoader].
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

class LocalArtworkPathKeyer : Keyer<LocalArtworkPath> {
    override fun key(
        data: LocalArtworkPath,
        options: Options
    ): String? {
        val key = data.path + ";" + data.x + ";" + data.y
        return if (data.x > 0 || data.y > 0) key + ";" + options.scale else key
    }

}

data class LocalArtworkPath(val path: String?, val x: Int = -1, val y: Int = -1)
