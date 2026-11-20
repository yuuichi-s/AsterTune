package io.github.yuuichi_s.astertune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yuuichi_s.astertune.constants.LYRIC_FETCH_TIMEOUT
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.lyrics.LyricsFetchRole
import io.github.yuuichi_s.astertune.lyrics.LyricsHelper
import io.github.yuuichi_s.astertune.lyrics.LyricsResult
import io.github.yuuichi_s.astertune.models.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Manual lyrics search candidate shown in the results list.
 *
 * [result] holds the original lyrics saved on selection. [preview] is the displayed text,
 * and [synced] controls the sync icon.
 */
data class LyricsCandidate(
    val result: LyricsResult,
    val preview: String,
    val synced: Boolean,
)

@HiltViewModel
class LyricsMenuViewModel @Inject constructor(
    private val lyricsHelper: LyricsHelper,
    val database: MusicDatabase,
) : ViewModel() {
    private var job: Job? = null
    val results = MutableStateFlow(emptyList<LyricsCandidate>())
    val isLoading = MutableStateFlow(false)

    /** Starts a manual lyrics search and publishes candidates as they arrive. */
    fun search(mediaId: String, title: String, artist: String, duration: Int) {
        val previous = job
        previous?.cancel()
        isLoading.value = true
        results.value = emptyList()
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Wait for the previous search to stop updating results and loading state.
                previous?.join()
                results.value = emptyList()
                isLoading.value = true
                val parserOptions = lyricsHelper.getParserOptions().copy(errorText = null)
                withTimeoutOrNull(LYRIC_FETCH_TIMEOUT) {
                    lyricsHelper.getAllLyrics(mediaId, title, artist, duration) { result ->
                        val candidate = toCandidate(result, parserOptions)
                        results.update {
                            it + candidate
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                isLoading.value = false
            }
        }
    }

    /** Uses parsed TTML for the preview when possible and falls back to the original text. */
    private fun toCandidate(result: LyricsResult, parserOptions: LrcUtils.LrcParserOptions): LyricsCandidate {
        val raw = result.lyrics
        if (!raw.trimStart().startsWith("<")) {
            return LyricsCandidate(result, raw, synced = raw.startsWith("["))
        }
        val parsed = try {
            LrcUtils.parseLyrics(raw, null, parserOptions, LrcUtils.LyricFormat.TTML)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return LyricsCandidate(result, raw, synced = false)
        val preview = parsed.unsyncedText
            .map { it.first.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .ifEmpty { raw }
        return LyricsCandidate(result, preview, synced = parsed is SemanticLyrics.SyncedLyrics)
    }

    fun cancelSearch() {
        job?.cancel()
        job = null
    }

    fun refetchLyrics(mediaMetadata: MediaMetadata, onDone: (SemanticLyrics?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            withTimeoutOrNull(LYRIC_FETCH_TIMEOUT) {
                // Keep the existing row until its replacement is ready so cancellation does not discard usable lyrics.
                lyricsHelper.fetchAndStoreRemote(mediaMetadata, LyricsFetchRole.MANUAL, forceRefresh = true)
                val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                onDone(lyrics)
            }
        }
    }
}
