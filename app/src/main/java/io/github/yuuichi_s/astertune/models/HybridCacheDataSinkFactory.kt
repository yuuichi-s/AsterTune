/*
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.models

import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink

/**
 * Wrapper class for cache. This will conditionally write to the cache.
 */
class HybridCacheDataSinkFactory(
    private val cache: Cache,
    private val shouldCache: (DataSpec) -> Boolean
) : DataSink.Factory {

    override fun createDataSink(): DataSink {
        return object : DataSink {
            private var delegate: CacheDataSink? = null

            override fun open(dataSpec: DataSpec) {
                if (shouldCache(dataSpec)) {
                    delegate = CacheDataSink(cache, CacheDataSink.DEFAULT_FRAGMENT_SIZE)
                    delegate?.open(dataSpec)
                }
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                delegate?.write(buffer, offset, length)
            }

            override fun close() {
                delegate?.close()
            }
        }
    }
}