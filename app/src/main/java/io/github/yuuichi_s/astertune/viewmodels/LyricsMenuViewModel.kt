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
import org.akanework.gramophone.logic.utils.SemanticLyrics
import javax.inject.Inject

@HiltViewModel
class LyricsMenuViewModel @Inject constructor(
    private val lyricsHelper: LyricsHelper,
    val database: MusicDatabase,
) : ViewModel() {
    private var job: Job? = null
    val results = MutableStateFlow(emptyList<LyricsResult>())
    val isLoading = MutableStateFlow(false)

    fun search(mediaId: String, title: String, artist: String, duration: Int) {
        isLoading.value = true
        results.value = emptyList()
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeoutOrNull(LYRIC_FETCH_TIMEOUT) {
                    lyricsHelper.getAllLyrics(mediaId, title, artist, duration) { result ->
                        results.update {
                            it + result
                        }
                    }
                }
            } catch (e: Exception) {
            } finally {
                isLoading.value = false
            }
        }
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
