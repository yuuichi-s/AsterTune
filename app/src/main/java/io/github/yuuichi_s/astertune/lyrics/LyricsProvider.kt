package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import kotlinx.coroutines.CancellationException

/**
 * Outcome of a single provider lookup for one song.
 *
 * [NotFound] is reserved for a definitive answer that the song has no lyrics (a successful transport
 * response that carried nothing). Any transport error, timeout or unexpected exception is [Failed], so
 * a transient failure is never mistaken for an absence of lyrics.
 */
sealed interface LyricsFetchResult {
    data class Found(val raw: String) : LyricsFetchResult
    data object NotFound : LyricsFetchResult
    data class Failed(val cause: Throwable? = null) : LyricsFetchResult
}

/**
 * Map a backing module result to a [LyricsFetchResult]. The module contract is: success with a
 * non-blank string is [LyricsFetchResult.Found], success with null/blank is a definitive
 * [LyricsFetchResult.NotFound], and a failure is a transient [LyricsFetchResult.Failed].
 * A cancellation is re-thrown so it is never recorded as a provider failure.
 */
internal fun Result<String?>.toFetchResult(): LyricsFetchResult =
    fold(
        onSuccess = { text -> if (!text.isNullOrBlank()) LyricsFetchResult.Found(text) else LyricsFetchResult.NotFound },
        onFailure = { throwable ->
            if (throwable is CancellationException) throw throwable
            LyricsFetchResult.Failed(throwable)
        }
    )

/** Provider contract for lyric lookup and manual-search candidate delivery. */
interface LyricsProvider {
    /** Stable identifier used to build the provider-configuration signature; never localized. */
    val id: String
    val name: String
    fun isEnabled(context: Context): Boolean
    suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult

    /**
     * Delivers manual-search candidates through [callback].
     * Providers without a candidate search fall back to [getLyrics].
     * [LyricsFetchResult.NotFound] returns normally without a candidate;
     * [LyricsFetchResult.Failed] throws [LyricsSearchFailedException] so the caller can distinguish
     * failure from an empty result.
     */
    suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, callback: (String) -> Unit) {
        when (val result = getLyrics(id, title, artist, duration)) {
            is LyricsFetchResult.Found -> callback(result.raw)
            LyricsFetchResult.NotFound -> Unit
            is LyricsFetchResult.Failed -> throw LyricsSearchFailedException(name, result.cause)
        }
    }
}

/** Manual-search failure with an optional underlying [cause]. */
class LyricsSearchFailedException(providerName: String, cause: Throwable?) :
    Exception("$providerName search failed", cause)
