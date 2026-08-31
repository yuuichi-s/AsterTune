package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.CancellationException

object YouTubeLyricsProvider : LyricsProvider {
    override val id = "youtube"
    override val name = "YouTube Music"
    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult {
        val nextResult = YouTube.next(WatchEndpoint(videoId = id)).getOrElse {
            if (it is CancellationException) throw it
            return LyricsFetchResult.Failed(it)
        }
        // A missing lyrics endpoint is YouTube's definitive answer that the song has no lyrics.
        val endpoint = nextResult.lyricsEndpoint ?: return LyricsFetchResult.NotFound
        val lyrics = YouTube.lyrics(endpoint).getOrElse {
            if (it is CancellationException) throw it
            return LyricsFetchResult.Failed(it)
        }
        return if (!lyrics.isNullOrBlank()) LyricsFetchResult.Found(lyrics) else LyricsFetchResult.NotFound
    }
}
