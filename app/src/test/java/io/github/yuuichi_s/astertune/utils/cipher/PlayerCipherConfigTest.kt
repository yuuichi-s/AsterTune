/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils.cipher

import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests validation of externally supplied cipher config fields and bounded response-body reads.
 *
 * The JVM tests exercise Android-independent helpers directly because `JSONObject` is an Android
 * stub in local unit tests. The counting source verifies that an oversized response is rejected
 * without consuming the complete body.
 */
class PlayerCipherConfigTest {

    @Test
    fun `accepts a well formed entry`() {
        val config = PlayerCipherConfigStore.parseConfig("Tl(48,5831,INPUT)", "W_")
        assertEquals(PlayerCipherConfig("Tl", listOf(48, 5831), "W_"), config)
    }

    @Test
    fun `rejects a sig function name carrying a separator`() {
        assertNull(PlayerCipherConfigStore.parseConfig("evil;Tl(48,5831,INPUT)", "W_"))
    }

    @Test
    fun `rejects a sig function name carrying a newline`() {
        assertNull(PlayerCipherConfigStore.parseConfig("Tl\nevil(48,5831,INPUT)", "W_"))
    }

    @Test
    fun `rejects an nClass carrying a separator`() {
        assertNull(PlayerCipherConfigStore.parseConfig("Tl(48,5831,INPUT)", "Yx('x',true); g.Yx"))
    }

    @Test
    fun `rejects an nClass carrying a newline`() {
        assertNull(PlayerCipherConfigStore.parseConfig("Tl(48,5831,INPUT)", "W_\nevil"))
    }

    @Test
    fun `rejects an empty nClass`() {
        assertNull(PlayerCipherConfigStore.parseConfig("Tl(48,5831,INPUT)", ""))
    }

    @Test
    fun `rejects a non integer constant`() {
        assertNull(PlayerCipherConfigStore.parseConfig("Tl(a,5831,INPUT)", "W_"))
    }

    @Test
    fun `rejects a call not ending in INPUT`() {
        assertNull(PlayerCipherConfigStore.parseConfig("Tl(48,5831)", "W_"))
    }

    @Test
    fun `rejects a call without constants`() {
        assertNull(PlayerCipherConfigStore.parseConfig("Tl(INPUT)", "W_"))
    }

    @Test
    fun `accepts a body below the limit`() {
        val source = Buffer().write(ByteArray(LIMIT.toInt() - 1))
        assertFalse(PlayerCipherConfigStore.exceedsLimit(source, LIMIT))
    }

    @Test
    fun `accepts a body of exactly the limit`() {
        val source = Buffer().write(ByteArray(LIMIT.toInt()))
        assertFalse(PlayerCipherConfigStore.exceedsLimit(source, LIMIT))
    }

    @Test
    fun `rejects a body one byte over the limit`() {
        val source = Buffer().write(ByteArray(LIMIT.toInt() + 1))
        // Allow buffered read-ahead while still failing if the complete oversized body is consumed.
        assertTrue(PlayerCipherConfigStore.exceedsLimit(source, LIMIT))
    }

    @Test
    fun `stops reading shortly after the limit`() {
        val source = CountingSource(LIMIT + 128L * 1024)
        assertTrue(PlayerCipherConfigStore.exceedsLimit(source.buffer(), LIMIT))
        assertTrue(
            "read ${source.bytesRead} bytes",
            source.bytesRead < LIMIT + 64 * 1024,
        )
    }

    /** Supplies [available] zero bytes and records how many of them were read. */
    private class CountingSource(private val available: Long) : Source {
        var bytesRead = 0L
            private set

        override fun read(sink: Buffer, byteCount: Long): Long {
            val remaining = available - bytesRead
            if (remaining <= 0L) return -1L
            val count = minOf(byteCount, remaining)
            sink.write(ByteArray(count.toInt()))
            bytesRead += count
            return count
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }

    private companion object {
        const val LIMIT = 4L * 1024
    }
}
