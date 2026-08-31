package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import com.dd3boh.lrclib.LrcLib
import io.github.yuuichi_s.astertune.constants.EnableLrcLibKey
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get

/**
 * Source: https://github.com/Malopieds/InnerTune
 */
object LrcLibLyricsProvider : LyricsProvider {
    override val id = "lrclib"
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): LyricsFetchResult = LrcLib.getLyrics(title, artist, duration).toFetchResult()

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, null, callback)
    }
}
