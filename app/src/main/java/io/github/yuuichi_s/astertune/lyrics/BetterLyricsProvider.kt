package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import com.dd3boh.betterlyrics.BetterLyrics
import io.github.yuuichi_s.astertune.constants.EnableBetterLyricsKey
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val id = "betterlyrics"
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableBetterLyricsKey] ?: false

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult =
        BetterLyrics.getLyrics(title, artist, duration).toFetchResult()
}
