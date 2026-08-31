package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.CancellationException

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val id = "youtube-subtitle"
    override val name = "YouTube Subtitle"
    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult =
        YouTube.transcript(id).fold(
            onSuccess = { LyricsFetchResult.Found(it) },
            onFailure = {
                if (it is CancellationException) throw it
                // transcript() signals a missing or empty caption track with IllegalStateException (via
                // check()); transport errors surface as other exception types.
                if (it is IllegalStateException) LyricsFetchResult.NotFound else LyricsFetchResult.Failed(it)
            }
        )
}
