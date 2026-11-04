package io.github.yuuichi_s.astertune.lyrics

import android.app.Application
import android.content.Context
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.createEmptyTestDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Verifies candidate retention, continued search after provider failure, and caching only
 * when no provider failed in [LyricsHelper.getAllLyrics].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ManualLyricsSearchCacheTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        database = createEmptyTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Provider using the default [LyricsProvider.getAllLyrics], which falls back to [getLyrics].
     */
    private class SingleResultProvider(
        override val name: String,
        private val result: LyricsFetchResult,
    ) : LyricsProvider {
        override val id = name
        var calls = 0
        override fun isEnabled(context: Context) = true
        override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult {
            calls++
            return result
        }
    }

    /**
     * Provider with its own candidate search: delivers [candidates], then throws [error] if set.
     */
    private class CandidateProvider(
        override val name: String,
        private val candidates: List<String>,
        private val error: Exception? = null,
    ) : LyricsProvider {
        override val id = name
        var calls = 0
        override fun isEnabled(context: Context) = true
        override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int) =
            throw UnsupportedOperationException()

        override suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, callback: (String) -> Unit) {
            calls++
            candidates.forEach(callback)
            error?.let { throw it }
        }
    }

    private fun helper(vararg providers: LyricsProvider) =
        LyricsHelper(RuntimeEnvironment.getApplication(), database, providers.toList())

    private fun LyricsHelper.search(
        mediaId: String = "id",
        title: String = "title",
        artist: String = "artist",
        duration: Int = 200,
    ): List<LyricsResult> = runBlocking {
        val results = mutableListOf<LyricsResult>()
        getAllLyrics(mediaId, title, artist, duration) { results += it }
        results
    }

    @Test
    fun failedResultKeepsCandidatesAndIsNotCached() {
        val first = CandidateProvider("first", listOf("a"))
        val failing = SingleResultProvider("failing", LyricsFetchResult.Failed(IOException()))
        val last = CandidateProvider("last", listOf("c"))
        val helper = helper(first, failing, last)

        val expected = listOf(LyricsResult("first", "a"), LyricsResult("last", "c"))
        assertEquals(expected, helper.search())
        assertEquals(expected, helper.search())
        assertEquals(listOf(2, 2, 2), listOf(first.calls, failing.calls, last.calls))
    }

    @Test
    fun thrownExceptionKeepsCandidatesAndIsNotCached() {
        val first = CandidateProvider("first", listOf("a"))
        val throwing = CandidateProvider("throwing", listOf("b"), IOException())
        val last = CandidateProvider("last", listOf("c"))
        val helper = helper(first, throwing, last)

        val expected = listOf(LyricsResult("first", "a"), LyricsResult("throwing", "b"), LyricsResult("last", "c"))
        assertEquals(expected, helper.search())
        assertEquals(expected, helper.search())
        assertEquals(listOf(2, 2, 2), listOf(first.calls, throwing.calls, last.calls))
    }

    @Test
    fun returnsCandidatesForTheCurrentSearchAndReusesIdenticalSearches() {
        val provider = object : LyricsProvider {
            override val name = "provider"
            override val id = name
            var calls = 0

            override fun isEnabled(context: Context) = true

            override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult {
                calls++
                return LyricsFetchResult.Found("$id / $title / $artist / $duration")
            }
        }
        val alternate = CandidateProvider("alternate", listOf("alternate candidate"))
        val helper = helper(provider, alternate)
        fun expectedCandidates(lyrics: String) = listOf(
            LyricsResult(provider.name, lyrics),
            LyricsResult(alternate.name, "alternate candidate"),
        )

        val expected = expectedCandidates("id / title / artist / 200")
        assertEquals(expected, helper.search())
        assertEquals(expected, helper.search())
        assertEquals(listOf(1, 1), listOf(provider.calls, alternate.calls))

        assertEquals(
            expectedCandidates("id / title / artist / 240"),
            helper.search(duration = 240),
        )
        assertEquals(
            expectedCandidates("other-id / title / artist / 240"),
            helper.search(mediaId = "other-id", duration = 240),
        )
        assertEquals(
            expectedCandidates("other-id / edited title / artist / 240"),
            helper.search(mediaId = "other-id", title = "edited title", duration = 240),
        )
        assertEquals(
            expectedCandidates("other-id / edited title / edited artist / 240"),
            helper.search(mediaId = "other-id", title = "edited title", artist = "edited artist", duration = 240),
        )
    }

    @Test
    fun allNotFoundResultIsCached() {
        val first = SingleResultProvider("first", LyricsFetchResult.NotFound)
        val second = SingleResultProvider("second", LyricsFetchResult.NotFound)
        val helper = helper(first, second)

        assertEquals(emptyList<LyricsResult>(), helper.search())
        assertEquals(emptyList<LyricsResult>(), helper.search())
        assertEquals(listOf(1, 1), listOf(first.calls, second.calls))
    }

    @Test
    fun cancellationStopsSearchAndPropagates() {
        val cancelling = CandidateProvider("cancelling", listOf("a"), CancellationException())
        val last = CandidateProvider("last", listOf("c"))
        val helper = helper(cancelling, last)

        assertThrows(CancellationException::class.java) { helper.search() }
        assertEquals(0, last.calls)
        assertThrows(CancellationException::class.java) { helper.search() }
        assertEquals(2, cancelling.calls)
    }
}
