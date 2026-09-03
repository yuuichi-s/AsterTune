/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec

/**
 * Resolved stream data cached and retrieved as a single value.
 *
 * The URL, expiry time, issuing client, and request headers belong to the same resolution.
 */
internal data class CachedStreamUrl(
    val url: String,
    val expiresAtMillis: Long,
    val clientName: String,
    val requestHeaders: Map<String, String>,
)

/**
 * Thread-safe cache for resolved streams shared by data source callbacks.
 *
 * Cache bookkeeping is synchronized; callers perform network and player operations outside the
 * lock. Lookups remove expired entries so callers cannot reuse stale request data.
 */
internal class StreamUrlCache(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val entries = HashMap<String, CachedStreamUrl>()

    operator fun get(mediaId: String): CachedStreamUrl? = synchronized(lock) {
        val stream = entries[mediaId] ?: return@synchronized null
        if (stream.expiresAtMillis <= currentTimeMillis()) {
            entries.remove(mediaId)
            null
        } else {
            stream
        }
    }

    fun put(
        mediaId: String,
        url: String,
        requestHeaders: Map<String, String>,
        clientName: String,
        expiresInSeconds: Int,
    ): CachedStreamUrl = synchronized(lock) {
        CachedStreamUrl(
            url = url,
            expiresAtMillis = currentTimeMillis() + expiresInSeconds * 1000L,
            clientName = clientName,
            requestHeaders = requestHeaders.toMap(),
        ).also { entries[mediaId] = it }
    }

    /**
     * Removes and returns the cached value without checking expiry so a failed request can be
     * attributed to the client that issued it.
     */
    fun invalidate(mediaId: String): CachedStreamUrl? = synchronized(lock) {
        entries.remove(mediaId)
    }
}

internal fun resolvedRequestHeaders(
    existingHeaders: Map<String, String>,
    stream: CachedStreamUrl,
): Map<String, String> = existingHeaders + stream.requestHeaders

internal fun DataSpec.withResolvedStream(stream: CachedStreamUrl): DataSpec =
    withUri(stream.url.toUri())
        .withRequestHeaders(resolvedRequestHeaders(httpRequestHeaders, stream))
