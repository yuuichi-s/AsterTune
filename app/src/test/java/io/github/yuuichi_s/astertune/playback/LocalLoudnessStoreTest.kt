package io.github.yuuichi_s.astertune.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests when complete and partial measurements replace cached loudness. */
class LocalLoudnessStoreTest {
    @Test
    fun completeMeasurementIsUsedWithoutTheDatabase() {
        val store = LocalLoudnessStore()
        store.remember("song", null)

        store.onMeasured("song", 1.5, complete = true)

        assertTrue("song" in store)
        assertEquals(1.5, store["song"]!!, 0.0)
    }

    @Test
    fun completeMeasurementReplacesExistingValue() {
        val store = LocalLoudnessStore()
        store.remember("song", -2.0)

        store.onMeasured("song", 1.5, complete = true)

        assertEquals(1.5, store["song"]!!, 0.0)
    }

    @Test
    fun laterDatabaseReadDoesNotReplaceMeasurement() {
        val store = LocalLoudnessStore()
        store.onMeasured("song", 1.5, complete = true)

        store.remember("song", null)

        assertEquals(1.5, store["song"]!!, 0.0)
    }

    @Test
    fun partialMeasurementIsUsedForSongWithoutValue() {
        val store = LocalLoudnessStore()
        store.remember("song", null)

        store.onMeasured("song", 0.3, complete = false)

        assertEquals(0.3, store["song"]!!, 0.0)
    }

    @Test
    fun partialMeasurementDoesNotReplaceExistingValue() {
        val store = LocalLoudnessStore()
        store.remember("song", -2.0)

        store.onMeasured("song", 0.3, complete = false)

        assertEquals(-2.0, store["song"]!!, 0.0)
    }

    @Test
    fun partialMeasurementDoesNotReplaceCompleteOne() {
        val store = LocalLoudnessStore()
        store.remember("song", null)
        store.onMeasured("song", 1.5, complete = true)

        store.onMeasured("song", 0.3, complete = false)

        assertEquals(1.5, store["song"]!!, 0.0)
    }

    @Test
    fun partialMeasurementOfUnreadSongIsLeftToTheDatabase() {
        val store = LocalLoudnessStore()

        store.onMeasured("song", 0.3, complete = false)

        assertFalse("song" in store)
        assertNull(store["song"])
    }
}
