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
 * non-blank string is [Found], success with null/blank is a definitive [NotFound], and a failure is a
 * transient [Failed]. A cancellation is re-thrown so it is never recorded as a provider failure.
 */
internal fun Result<String?>.toFetchResult(): LyricsFetchResult =
    fold(
        onSuccess = { text -> if (!text.isNullOrBlank()) LyricsFetchResult.Found(text) else LyricsFetchResult.NotFound },
        onFailure = { throwable ->
            if (throwable is CancellationException) throw throwable
            LyricsFetchResult.Failed(throwable)
        }
    )

interface LyricsProvider {
    /** Stable identifier used to build the provider-configuration signature; never localized. */
    val id: String
    val name: String
    fun isEnabled(context: Context): Boolean
    suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult
    suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, callback: (String) -> Unit) {
        (getLyrics(id, title, artist, duration) as? LyricsFetchResult.Found)?.let { callback(it.raw) }
    }
}
