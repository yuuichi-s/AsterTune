package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import io.github.yuuichi_s.astertune.constants.EnableSimpMusicKey
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import com.dd3boh.simpmusic.SimpMusicLyrics

object SimpMusicLyricsProvider : LyricsProvider {
    override val id = "simpmusic"
    override val name = "SimpMusic"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableSimpMusicKey] ?: true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult =
        SimpMusicLyrics.getLyrics(id, duration).toFetchResult()

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        SimpMusicLyrics.getAllLyrics(id, duration, callback)
    }
}
