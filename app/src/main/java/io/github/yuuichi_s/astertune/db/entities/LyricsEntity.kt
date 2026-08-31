package io.github.yuuichi_s.astertune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.akanework.gramophone.logic.utils.SemanticLyrics

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val id: String,
    val lyrics: String,
    val provider: String? = null,
    /** Epoch millis of the last remote resolution, or null when the row has no remote-resolution metadata. */
    val lastCheckedAt: Long? = null,
    /** Provider-configuration signature of the last remote resolution, or null when the row has no remote-resolution metadata. */
    val providerSignature: String? = null,
) {
    companion object {
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"
        val uninitializedLyric = SemanticLyrics.UnsyncedLyrics(listOf(Pair(LYRICS_NOT_FOUND, null)))
    }
}