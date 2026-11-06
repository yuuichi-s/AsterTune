package com.zionhuang.innertube.models

import java.util.concurrent.ConcurrentHashMap

/**
 * Records the width and height of the thumbnail candidate chosen for each URL, so the image
 * loader can log them next to the requested size. Recording is off unless [enabled] is set.
 */
object ThumbnailSizeLog {
    @Volatile
    var enabled = false

    private const val MAX_ENTRIES = 5000
    private val sizes = ConcurrentHashMap<String, String>()

    fun record(thumbnail: Thumbnail?) {
        if (!enabled || thumbnail == null) return
        if (sizes.size >= MAX_ENTRIES) sizes.clear()
        sizes[thumbnail.url] = "${thumbnail.width ?: "?"}x${thumbnail.height ?: "?"}"
    }

    fun sizeOf(url: String): String? = if (enabled) sizes[url] else null
}
