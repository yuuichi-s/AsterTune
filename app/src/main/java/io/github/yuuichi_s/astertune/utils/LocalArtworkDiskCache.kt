/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils

import android.content.Context
import coil3.disk.DiskCache
import coil3.disk.directory
import io.github.yuuichi_s.astertune.constants.MaxLocalImageCacheSizeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Holds the disk cache for downscaled artwork embedded in local audio files.
 *
 * The cache is separate from the online image cache. Its size limit is read once per process,
 * so a changed limit applies after the app restarts.
 */
object LocalArtworkDiskCache {
    const val DEFAULT_MAX_SIZE_MIB = 32

    /** Sizes, in pixels, that requests are rounded up to before artwork is saved. */
    val TIERS = intArrayOf(128, 256, 512, 1024)

    /** JPEG quality for saved artwork without transparency. */
    const val JPEG_QUALITY = 90

    private const val DIRECTORY = "local_artwork"

    @Volatile
    private var initialized = false

    @Volatile
    private var cache: DiskCache? = null

    /**
     * Returns the shared cache, or null if it was disabled on first access.
     *
     * When first accessed with caching disabled, schedules deletion of the cache directory.
     */
    fun get(context: Context): DiskCache? {
        if (initialized) return cache
        synchronized(this) {
            if (!initialized) {
                val sizeMib = context.dataStore[MaxLocalImageCacheSizeKey] ?: DEFAULT_MAX_SIZE_MIB
                cache = if (sizeMib > 0) {
                    DiskCache.Builder()
                        .directory(directory(context))
                        .maxSizeBytes(sizeMib * 1024 * 1024L)
                        .build()
                } else {
                    val directory = directory(context)
                    CoroutineScope(Dispatchers.IO).launch {
                        directory.deleteRecursively()
                    }
                    null
                }
                initialized = true
            }
            return cache
        }
    }

    private fun directory(context: Context): File = context.cacheDir.resolve(DIRECTORY)

    fun tierFor(requestSide: Int): Int? = TIERS.firstOrNull { it >= requestSide }

    /**
     * Returns the cache key base for the audio file at [path].
     *
     * Includes the file's last-modified time and length so edited files do not reuse old artwork.
     */
    fun keyBase(path: String): String {
        val file = File(path)
        return "$path;${file.lastModified()};${file.length()}"
    }

    fun tierKey(keyBase: String, tier: Int) = "$keyBase;$tier"

    /** Key for artwork saved at its original size because it is not larger than the tier. */
    fun originalKey(keyBase: String) = "$keyBase;original"
}
