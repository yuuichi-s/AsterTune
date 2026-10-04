package io.github.yuuichi_s.astertune.playback

import android.app.Application
import androidx.room.Room
import com.zionhuang.innertube.models.Artist
import com.zionhuang.innertube.models.SongItem
import io.github.yuuichi_s.astertune.db.InternalDatabase
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Verifies that [RelatedSongsSaver] saves related songs without duplicates. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class RelatedSongsSaverTest {
    private lateinit var database: MusicDatabase

    @Before
    fun setUp() {
        // Run transactions on the calling thread, so the rows are written when saveIfMissing returns
        database = MusicDatabase(
            Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), InternalDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        )
        // The source song must exist to satisfy related_song_map's foreign key
        database.insert(SongEntity(id = SOURCE, title = SOURCE, localPath = null))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun relatedSongsAreSaved() = runBlocking {
        RelatedSongsSaver(database) { listOf(item("r1"), item("r2")) }.saveIfMissing(SOURCE)

        assertEquals(listOf("r1", "r2"), relatedIds())
        assertNotNull(database.song("r1").first())
        assertNotNull(database.song("r2").first())
    }

    @Test
    fun songListedTwiceInTheResponseIsSavedOnce() = runBlocking {
        RelatedSongsSaver(database) { listOf(item("r1"), item("r2"), item("r1")) }.saveIfMissing(SOURCE)

        assertEquals(listOf("r1", "r2"), relatedIds())
    }

    @Test
    fun overlappingCallsSaveTheRelatedSongsOnce() = runBlocking {
        val bothFetching = CompletableDeferred<Unit>()
        var fetching = 0
        val saver = RelatedSongsSaver(database) {
            // Hold every fetch until both calls have passed the check before fetching
            if (++fetching == 2) bothFetching.complete(Unit)
            bothFetching.await()
            listOf(item("r1"), item("r2"))
        }

        listOf(async { saver.saveIfMissing(SOURCE) }, async { saver.saveIfMissing(SOURCE) }).awaitAll()

        assertEquals(2, fetching)
        assertEquals(listOf("r1", "r2"), relatedIds())
    }

    private fun relatedIds() = database.openHelper.readableDatabase
        .query("SELECT relatedSongId FROM related_song_map WHERE songId = ? ORDER BY relatedSongId", arrayOf(SOURCE))
        .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun item(id: String) = SongItem(
        id = id,
        title = id,
        artists = listOf(Artist(name = "artist", id = "artist")),
        thumbnail = "https://example.com/$id.jpg",
    )

    private companion object {
        const val SOURCE = "source"
    }
}
