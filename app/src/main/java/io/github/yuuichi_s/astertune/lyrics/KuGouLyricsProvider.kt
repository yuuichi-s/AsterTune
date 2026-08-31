package io.github.yuuichi_s.astertune.lyrics

import android.content.Context
import io.github.yuuichi_s.astertune.constants.EnableKugouKey
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import com.zionhuang.kugou.KuGou

object KuGouLyricsProvider : LyricsProvider {
    override val id = "kugou"
    override val name = "Kugou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): LyricsFetchResult =
        KuGou.getLyrics(title, artist, duration).toFetchResult()

    override suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, callback: (String) -> Unit) {
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, callback)
    }
}
