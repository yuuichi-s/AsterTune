package io.github.yuuichi_s.astertune.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamUrlCacheTest {
    @Test
    fun getReturnsTheCachedStreamBeforeExpiry() {
        var now = 1_000L
        val cache = StreamUrlCache { now }

        val inserted = cache.put(
            mediaId = "song",
            url = "https://example.com/stream",
            requestHeaders = mapOf("User-Agent" to "client"),
            clientName = "VISIONOS",
            expiresInSeconds = 60,
        )
        now += 59_999L

        assertEquals(inserted, cache["song"])
    }

    @Test
    fun getRemovesTheCachedStreamAtTheExpiryBoundary() {
        var now = 1_000L
        val cache = StreamUrlCache { now }
        cache.put("song", "https://example.com/stream", emptyMap(), "VISIONOS", 60)

        now += 60_000L

        assertNull(cache["song"])
        assertNull(cache.invalidate("song"))
    }

    @Test
    fun invalidateReturnsAndRemovesTheCachedStream() {
        val cache = StreamUrlCache { 1_000L }
        val inserted = cache.put(
            mediaId = "song",
            url = "https://example.com/stream",
            requestHeaders = mapOf("User-Agent" to "client"),
            clientName = "VISIONOS",
            expiresInSeconds = 60,
        )

        assertEquals(inserted, cache.invalidate("song"))
        assertNull(cache["song"])
    }

    @Test
    fun putCopiesTheRequestHeaders() {
        val cache = StreamUrlCache { 1_000L }
        val headers = mutableMapOf("User-Agent" to "client")
        cache.put(
            mediaId = "song",
            url = "https://example.com/stream",
            requestHeaders = headers,
            clientName = "VISIONOS",
            expiresInSeconds = 60,
        )

        headers["User-Agent"] = "changed"

        assertEquals("client", cache["song"]?.requestHeaders?.get("User-Agent"))
    }

    @Test
    fun resolvedHeadersPreferTheStreamHeaders() {
        val stream = cachedStream(mapOf("User-Agent" to "stream", "Origin" to "stream-origin"))

        val headers = resolvedRequestHeaders(
            existingHeaders = mapOf("User-Agent" to "existing"),
            stream = stream,
        )

        assertEquals("stream", headers["User-Agent"])
        assertEquals("stream-origin", headers["Origin"])
    }

    @Test
    fun resolvedHeadersRetainExistingHeaders() {
        val headers = resolvedRequestHeaders(
            existingHeaders = mapOf("Range" to "bytes=0-100"),
            stream = cachedStream(mapOf("User-Agent" to "stream")),
        )

        assertEquals("bytes=0-100", headers["Range"])
        assertEquals("stream", headers["User-Agent"])
    }

    private fun cachedStream(headers: Map<String, String>) = CachedStreamUrl(
        url = "https://example.com/stream",
        expiresAtMillis = 61_000L,
        clientName = "VISIONOS",
        requestHeaders = headers,
    )
}
