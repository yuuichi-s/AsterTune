package io.github.yuuichi_s.astertune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aggregation branches of [RemoteLyricsAggregator]. The classifier is faked so the decision logic is
 * exercised without a real lyrics parser: a raw string maps to a [FoundKind] by its own value.
 */
class RemoteLyricsAggregatorTest {

    private val classify: (String) -> FoundKind = { raw ->
        when (raw) {
            "synced" -> FoundKind.SYNCED
            "unsynced" -> FoundKind.UNSYNCED
            else -> FoundKind.UNPARSEABLE
        }
    }

    private fun aggregate(enabledCount: Int, outcomes: List<Pair<String, LyricsFetchResult>>): RemoteLyricsResult {
        val aggregator = RemoteLyricsAggregator(enabledCount)
        for ((name, result) in outcomes) {
            if (aggregator.offer(name, result, classify)) break
        }
        return aggregator.result()
    }

    @Test
    fun allNotFound_isDefinitiveNotFound() {
        val result = aggregate(
            enabledCount = 3,
            outcomes = listOf(
                "a" to LyricsFetchResult.NotFound,
                "b" to LyricsFetchResult.NotFound,
                "c" to LyricsFetchResult.NotFound,
            ),
        )
        assertEquals(RemoteLyricsResult.DefinitiveNotFound, result)
    }

    @Test
    fun notFoundPlusFailed_isIndeterminate() {
        val result = aggregate(
            enabledCount = 2,
            outcomes = listOf(
                "a" to LyricsFetchResult.NotFound,
                "b" to LyricsFetchResult.Failed(),
            ),
        )
        assertEquals(RemoteLyricsResult.Indeterminate, result)
    }

    @Test
    fun foundPlusFailed_isFound() {
        val result = aggregate(
            enabledCount = 2,
            outcomes = listOf(
                "a" to LyricsFetchResult.Failed(),
                "b" to LyricsFetchResult.Found("synced"),
            ),
        )
        assertTrue(result is RemoteLyricsResult.Found)
        result as RemoteLyricsResult.Found
        assertEquals("b", result.provider)
        assertTrue(result.synced)
    }

    @Test
    fun noEnabledProviders_isDefinitiveNotFoundOnlyWhenZeroExpectedAndNoOutcomes() {
        // Skipped is produced by the caller before aggregation; with zero enabled and zero outcomes the
        // aggregator itself reports DefinitiveNotFound (0 == 0). The production path returns Skipped first.
        assertEquals(RemoteLyricsResult.DefinitiveNotFound, aggregate(0, emptyList()))
    }

    @Test
    fun syncedWins_andStopsEarly() {
        val aggregator = RemoteLyricsAggregator(3)
        assertTrue(aggregator.offer("a", LyricsFetchResult.Found("synced"), classify))
        // Later outcomes are never offered because production breaks on the true return.
        val result = aggregator.result()
        assertTrue(result is RemoteLyricsResult.Found)
        assertTrue((result as RemoteLyricsResult.Found).synced)
    }

    @Test
    fun unsyncedHeldAsFallback_whenNoSynced() {
        val result = aggregate(
            enabledCount = 2,
            outcomes = listOf(
                "a" to LyricsFetchResult.Found("unsynced"),
                "b" to LyricsFetchResult.NotFound,
            ),
        )
        assertTrue(result is RemoteLyricsResult.Found)
        result as RemoteLyricsResult.Found
        assertEquals("a", result.provider)
        assertTrue(!result.synced)
    }

    @Test
    fun syncedPreferredOverEarlierUnsynced() {
        val result = aggregate(
            enabledCount = 2,
            outcomes = listOf(
                "a" to LyricsFetchResult.Found("unsynced"),
                "b" to LyricsFetchResult.Found("synced"),
            ),
        )
        assertTrue(result is RemoteLyricsResult.Found)
        result as RemoteLyricsResult.Found
        assertEquals("b", result.provider)
        assertTrue(result.synced)
    }

    @Test
    fun unparseableFound_blocksDefinitiveNotFound() {
        val result = aggregate(
            enabledCount = 2,
            outcomes = listOf(
                "a" to LyricsFetchResult.NotFound,
                "b" to LyricsFetchResult.Found("garbage"), // classified UNPARSEABLE
            ),
        )
        assertEquals(RemoteLyricsResult.Indeterminate, result)
    }

    @Test
    fun missingReports_blockDefinitiveNotFound() {
        // Two providers enabled but only one reported NotFound (the other timed out and never reported).
        val result = aggregate(
            enabledCount = 2,
            outcomes = listOf("a" to LyricsFetchResult.NotFound),
        )
        assertEquals(RemoteLyricsResult.Indeterminate, result)
    }
}
