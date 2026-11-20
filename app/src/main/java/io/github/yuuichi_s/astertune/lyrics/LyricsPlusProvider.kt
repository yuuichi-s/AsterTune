package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import io.github.yuuichi_s.astertune.constants.EnableLyricsPlusKey
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import io.github.yuuichi_s.lyricsplus.LyricsPlus

/** Lyrics+ provider, enabled by default. */
object LyricsPlusProvider : LyricsProvider {
    override val id = "lyricsplus"
    override val name = "Lyrics+"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLyricsPlusKey] ?: true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult =
        LyricsPlus.getLyrics(title, artist, duration).toFetchResult()
}
