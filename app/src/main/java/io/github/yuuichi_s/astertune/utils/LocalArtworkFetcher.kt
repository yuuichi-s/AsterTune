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
import android.media.MediaMetadataRetriever
import androidx.core.graphics.scale
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Scale
import kotlin.math.roundToInt

/**
 * Loads artwork embedded in local audio files, scaled to the requested size.
 */
class LocalArtworkFetcher(
    private val context: Context,
    private val data: LocalArtworkPath,
    private val targetScale: Scale,
    private val diskCache: DiskCache?,
) : Fetcher {

    private val imageDebug = LocalArtworkDebug.create(data, targetScale)

    override suspend fun fetch(): FetchResult? {
        val path = data.path
        if (path?.startsWith("/storage/") != true) return null

        return try {
            imageDebug?.onStart()
            val cache = diskCache
            val requestSide = maxOf(data.x, data.y)
            if (cache == null || requestSide <= 0) {
                loadFromArt(extractArt(path))
            } else {
                loadWithDiskCache(cache, path, requestSide)
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
     * Loads artwork from [cache], or extracts and caches it from the audio file.
     *
     * Saves artwork with its shorter side matching the smallest tier that is at least
     * [requestSide] to support both FIT and FILL. Artwork whose shorter side is no larger
     * than the tier is saved at its original size and reused across tiers.
     *
     * Requests larger than the largest tier reuse original-size entries.
     * New entries for those requests are saved only if the shorter side is no larger than that tier.
     *
     * Cache read failures fall back to the audio file without saving. Undecodable entries are removed.
     */
    private fun loadWithDiskCache(cache: DiskCache, path: String, requestSide: Int): ImageFetchResult {
        val keyBase = LocalArtworkDiskCache.keyBase(path)
        val tier = LocalArtworkDiskCache.tierFor(requestSide)
        val originalKey = LocalArtworkDiskCache.originalKey(keyBase)
        val tierKey = tier?.let { LocalArtworkDiskCache.tierKey(keyBase, it) }
        for (key in listOfNotNull(tierKey, originalKey)) {
            val cached = try {
                readCached(cache, key)
            } catch (e: Exception) {
                reportException(e)
                return loadFromArt(extractArt(path))
            } ?: continue
            imageDebug?.onCacheHit(key, cached.size)
            decodeForRequest(cached, sampled = key != originalKey)?.let { return it }
            runCatching { cache.remove(key) }
        }

        val art = extractArt(path) ?: return loadFromArt(null)
        val bounds = decodeBounds(art)
        val shortSide = minOf(bounds.outWidth, bounds.outHeight)
        val saveTier = tier ?: LocalArtworkDiskCache.TIERS.last()
        if (shortSide <= 0 || (tier == null && shortSide > saveTier)) {
            return loadFromArt(art)
        }

        val (bitmap, key) = if (shortSide > saveTier) {
            decodeTier(art, shortSide, saveTier) to LocalArtworkDiskCache.tierKey(keyBase, saveTier)
        } else {
            decode(art, 1) to originalKey
        }
        if (bitmap == null) return loadFromArt(art)

        save(cache, key, bitmap)
        return scaleToRequest(bitmap, bitmap.width < bounds.outWidth)
    }

    /**
     * Decodes [art] so that its shorter side, [shortSide], is reduced to [tier].
     */
    private fun decodeTier(art: ByteArray, shortSide: Int, tier: Int): Bitmap? {
        var sampleSize = 1
        while (shortSide / (sampleSize * 2) >= tier) {
            sampleSize *= 2
        }
        imageDebug?.onSampleSize(sampleSize)
        val decoded = decode(art, sampleSize) ?: return null
        val factor = tier.toFloat() / minOf(decoded.width, decoded.height)
        if (factor >= 1f) return decoded
        return decoded.scale(
            (decoded.width * factor).roundToInt().coerceAtLeast(1),
            (decoded.height * factor).roundToInt().coerceAtLeast(1)
        )
    }

    /**
     * Saves [bitmap] to [cache] under [key].
     *
     * Opaque artwork is saved as JPEG and artwork with transparency as PNG.
     * Skips saving if the entry cannot be edited. Write failures are reported without
     * interrupting artwork loading.
     */
    private fun save(cache: DiskCache, key: String, bitmap: Bitmap) {
        val editor = try {
            cache.openEditor(key)
        } catch (e: Exception) {
            reportException(e)
            null
        } ?: return
        imageDebug?.onSaveStart()
        try {
            val format = if (bitmap.hasTransparentPixel()) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            cache.fileSystem.write(editor.data) {
                check(bitmap.compress(format, LocalArtworkDiskCache.JPEG_QUALITY, outputStream())) {
                    "Could not encode artwork"
                }
            }
            editor.commit()
            imageDebug?.onSaved(key)
        } catch (e: Exception) {
            runCatching { editor.abort() }
            reportException(e)
        }
    }

    /**
     * Returns whether any pixel is not fully opaque.
     *
     * [Bitmap.hasAlpha] can be true even when every pixel is opaque, so checking individual
     * pixels is necessary to determine whether JPEG would lose transparency.
     */
    private fun Bitmap.hasTransparentPixel(): Boolean {
        if (!hasAlpha()) return false
        val row = IntArray(width)
        for (y in 0 until height) {
            getPixels(row, 0, width, 0, y, width, 1)
            if (row.any { it ushr 24 != 0xFF }) return true
        }
        return false
    }

    /**
     * Decodes [art] for the request, or draws a placeholder when it is null or cannot be decoded.
     */
    private fun loadFromArt(art: ByteArray?): ImageFetchResult =
        art?.let { decodeForRequest(it, sampled = false) }
            ?: scaleToRequest(framePlaceholder(), false)

    /**
     * Decodes [image] and scales it to the request, or returns null if it cannot be decoded.
     *
     * @param sampled Whether [image] is already smaller than the original artwork.
     */
    private fun decodeForRequest(image: ByteArray, sampled: Boolean): ImageFetchResult? {
        val sampleSize = sampleSize(image)
        imageDebug?.onSampleSize(sampleSize)
        val decoded = decode(image, sampleSize) ?: return null
        return scaleToRequest(decoded, sampled || sampleSize > 1)
    }

    private fun readCached(cache: DiskCache, key: String): ByteArray? =
        cache.openSnapshot(key)?.use { snapshot ->
            cache.fileSystem.read(snapshot.data) { readByteArray() }
        }

    private fun decode(art: ByteArray, sampleSize: Int): Bitmap? {
        val decoded = try {
            BitmapFactory.decodeByteArray(
                art, 0, art.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        } catch (_: Exception) {
            null
        }
        imageDebug?.onDecoded()
        return decoded
    }

    /**
     * Scales [image] down to the requested frame and wraps it as the fetch result.
     */
    private fun scaleToRequest(image: Bitmap, sampled: Boolean): ImageFetchResult {
        imageDebug?.onScaleStart()
        val factor = scaleFactor(image.width, image.height)
        val scaled = factor < 1f
        val result = if (scaled) {
            image.scale(
                (image.width * factor).roundToInt().coerceAtLeast(1),
                (image.height * factor).roundToInt().coerceAtLeast(1)
            )
        } else {
            image
        }
        imageDebug?.onComplete(result)

        return ImageFetchResult(
            image = result.asImage(),
            isSampled = sampled || scaled,
            dataSource = DataSource.DISK
        )
    }

    /**
     * Reads the artwork embedded in the audio file at [path], or returns null if it cannot be read.
     */
    private fun extractArt(path: String): ByteArray? {
        val art = try {
            embeddedPicture(path)
        } catch (_: Exception) {
            null
        }
        art?.let { imageDebug?.onExtracted(it.size) }
        return art
    }

    private fun decodeBounds(art: ByteArray): BitmapFactory.Options {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(art, 0, art.size, bounds)
        imageDebug?.onBounds(bounds.outWidth, bounds.outHeight)
        return bounds
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
     * Chooses the largest sample size that avoids making the decoded image too small
     * for the requested size and [targetScale].
     */
    private fun sampleSize(art: ByteArray): Int {
        val bounds = decodeBounds(art)

        val factor = scaleFactor(bounds.outWidth, bounds.outHeight)
        if (factor <= 0f || factor >= 1f) return 1

        var sampleSize = 1
        while (sampleSize * 2 <= 1f / factor) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * Calculates the scale factor for the requested frame.
     *
     * Uses [targetScale] to choose FIT or FILL while keeping the image's aspect ratio.
     * Limits the factor to 1 so the image is never enlarged.
     * If only one frame dimension is specified, uses that dimension.
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
        return if (side > 0) {
            CoilBitmapLoader.drawPlaceholder(context, side, side)
        } else {
            CoilBitmapLoader.drawPlaceholder(context)
        }
    }

    /**
     * Creates fetchers for local artwork.
     *
     * @param diskCache Cache for local artwork, or null to disable disk caching.
     *     Used independently of the request's disk cache policy.
     */
    class Factory(
        private val context: Context,
        private val diskCache: DiskCache?,
    ) : Fetcher.Factory<LocalArtworkPath> {
        override fun create(data: LocalArtworkPath, options: Options, imageLoader: ImageLoader): Fetcher {
            return LocalArtworkFetcher(context, data, options.scale, diskCache)
        }
    }
}
