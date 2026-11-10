package io.github.yuuichi_s.astertune.viewmodels

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.createEmptyTestDatabase
import io.github.yuuichi_s.astertune.db.entities.Artist
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.SongArtistMap
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class LocalSearchArtistUpdatesTest {
    private lateinit var database: MusicDatabase
    private val time = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val firstPath = "/storage/music/first.mp3"
    private val secondPath = "/storage/music/second.mp3"

    @Before
    fun setUp() {
        database = createEmptyTestDatabase()
        database.insert(ArtistEntity("LAfirst", "First", thumbnailUrl = firstPath, isLocal = true))
        database.insert(ArtistEntity("LAsecond", "Second", isLocal = true))
        listOf("first" to firstPath, "second" to secondPath).forEach { (id, path) ->
            database.insert(SongEntity(
                id, id, thumbnailUrl = path, localPath = path, isLocal = true, inLibrary = time,
                dateDownload = if (id == "first") time else null,
            ))
            database.insert(SongArtistMap(id, if (id == "first") "LAfirst" else "LAsecond", 0))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun LocalSearchViewModel.awaitResult(predicate: (LocalSearchResult) -> Boolean): LocalSearchResult =
        withTimeout(5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            var current = result.value
            while (!predicate(current)) {
                delay(10)
                shadowOf(Looper.getMainLooper()).idle()
                current = result.value
            }
            current
        }

    @Test
    fun queryAndArtworkStayConsistentWhenLibraryAndDownloadMembershipChange() = runBlocking<Unit> {
        val model = LocalSearchViewModel(database)
        val store = ViewModelStore()
        store.put("subject", model)
        val subscription = launch(Dispatchers.Default) { model.result.collect {} }
        fun LocalSearchResult.artist() = map[LocalFilter.ARTIST]?.singleOrNull() as? Artist
        try {
            model.filter.value = LocalFilter.ARTIST
            model.query.value = "First"
            model.awaitResult { it.query == "First" && it.artist()?.artist?.thumbnailUrl == firstPath }

            model.query.value = "Second"
            model.awaitResult { it.query == "Second" && it.artist()?.artist?.thumbnailUrl == secondPath }
            database.disableLocalSong("first")
            val updatedPath = "/storage/music/second-updated.mp3"
            val second = database.song("second").first()!!.song
            database.update(second.copy(localPath = updatedPath, thumbnailUrl = updatedPath))
            val current = model.awaitResult { it.query == "Second" && it.artist()?.artist?.thumbnailUrl == updatedPath }
            assertEquals("LAsecond", current.artist()!!.id)

            model.query.value = "First"
            val downloaded = model.awaitResult {
                it.query == "First" && it.artist()?.id == "LAfirst" && it.artist()?.artist?.thumbnailUrl == null
            }
            assertEquals(1, downloaded.artist()!!.downloadCount)
            assertEquals(firstPath, database.artistById("LAfirst")!!.thumbnailUrl)

            database.updateDownloadStatus("first", null)
            assertTrue(model.awaitResult { it.query == "First" && it.map.isEmpty() }.map.isEmpty())

            val first = database.song("first").first()!!.song
            database.update(first.copy(localPath = firstPath, inLibrary = time))
            model.awaitResult { it.query == "First" && it.artist()?.artist?.thumbnailUrl == firstPath }
        } finally {
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
            subscription.cancelAndJoin()
        }
    }
}
