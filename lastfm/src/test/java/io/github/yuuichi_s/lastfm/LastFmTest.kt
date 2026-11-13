package io.github.yuuichi_s.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies the request signature, the scrobbling rules, how Last.fm answers map to queue handling,
 * and that the queue survives being reopened.
 */
class LastFmTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun signatureSortsParametersAndSkipsFormat() {
        val params = mapOf("token" to "T", "method" to "auth.getSession", "api_key" to "A", "format" to "json")
        assertEquals("134c22bd22bd51a2e601bbe6f143590e", LastFm.signature(params, "secret"))
    }

    @Test
    fun signatureUsesAsciiOrderForBatchIndexes() {
        val params = mapOf("artist[1]" to "X", "artist[10]" to "Y", "method" to "track.scrobble", "api_key" to "A")
        assertEquals("08577572b09fc0faa4f1f7a3204e30d8", LastFm.signature(params, "secret"))
    }

    @Test
    fun signatureHashesUtf8() {
        val params = mapOf("artist[0]" to "Ã", "method" to "track.scrobble", "api_key" to "A")
        assertEquals("9f3b04dec11895e5f9fb58968ccf91f8", LastFm.signature(params, "secret"))
    }

    @Test
    fun scrobbleRules() {
        // 30 seconds or shorter never counts
        assertFalse(ScrobbleRules.isScrobblable(durationMs = 30_000, playedMs = 30_000))
        // half of the track
        assertFalse(ScrobbleRules.isScrobblable(durationMs = 200_000, playedMs = 99_999))
        assertTrue(ScrobbleRules.isScrobblable(durationMs = 200_000, playedMs = 100_000))
        // 4 minutes for long tracks
        assertFalse(ScrobbleRules.isScrobblable(durationMs = 600_000, playedMs = 239_999))
        assertTrue(ScrobbleRules.isScrobblable(durationMs = 600_000, playedMs = 240_000))
    }

    @Test
    fun sendOutcomes() {
        assertEquals(SendOutcome.DONE, ApiResult.Success(Unit).toSendOutcome())
        assertEquals(SendOutcome.RETRY, ApiResult.Unavailable("offline").toSendOutcome())
        listOf(8, 11, 16, 29).forEach { assertEquals(SendOutcome.RETRY, ApiResult.Error(it, "").toSendOutcome()) }
        assertEquals(SendOutcome.SESSION_INVALID, ApiResult.Error(9, "").toSendOutcome())
        listOf(4, 10, 13, 26).forEach {
            assertEquals(SendOutcome.API_KEY_REJECTED, ApiResult.Error(it, "").toSendOutcome())
        }
        listOf(6, 7).forEach { assertEquals(SendOutcome.DONE, ApiResult.Error(it, "").toSendOutcome()) }
    }

    @Test
    fun queueKeepsOrderAcrossReopen() {
        val file = File(tempFolder.root, "queue.json")
        val first = Scrobble(artist = "a", track = "1", timestamp = 1, album = "x", durationSec = 100)
        val second = Scrobble(artist = "b", track = "2", timestamp = 2)
        ScrobbleQueue(file).apply {
            add(first)
            add(second)
        }

        val reopened = ScrobbleQueue(file)
        assertEquals(listOf(first, second), reopened.peek(50))
        reopened.removeOldest(1)
        assertEquals(listOf(second), ScrobbleQueue(file).peek(50))
    }

    @Test
    fun queueReadsFileWrittenInCurrentFormat() {
        // Scrobbles queued by an installed version must still be read after an update.
        val file = File(tempFolder.root, "queue.json").apply {
            writeText(
                """[{"artist":"a","track":"1","timestamp":1,"album":"x","durationSec":100},""" +
                    """{"artist":"b","track":"2","timestamp":2}]"""
            )
        }
        assertEquals(
            listOf(
                Scrobble(artist = "a", track = "1", timestamp = 1, album = "x", durationSec = 100),
                Scrobble(artist = "b", track = "2", timestamp = 2),
            ),
            ScrobbleQueue(file).peek(50),
        )
    }

    @Test
    fun queueStartsEmptyWhenFileIsBroken() {
        val file = File(tempFolder.root, "queue.json").apply { writeText("{not json") }
        val queue = ScrobbleQueue(file)
        assertEquals(0, queue.size)
        queue.add(Scrobble(artist = "a", track = "1", timestamp = 1))
        assertEquals(1, ScrobbleQueue(file).size)
    }
}
