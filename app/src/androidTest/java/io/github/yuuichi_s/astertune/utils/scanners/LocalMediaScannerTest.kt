package io.github.yuuichi_s.astertune.utils.scanners

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuuichi_s.astertune.constants.SCANNER_OWNER_LM
import io.github.yuuichi_s.astertune.constants.ScannerMatchCriteria
import io.github.yuuichi_s.astertune.db.InternalDatabase
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises real file extraction and Room synchronization without opening an Activity or SAF UI. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class LocalMediaScannerTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var database: MusicDatabase
    private lateinit var scanner: LocalMediaScanner

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val external = requireNotNull(context.getExternalFilesDir(null))
        directory = File(external, "scanner-test-${java.util.UUID.randomUUID()}")
        check(directory.mkdirs())
        database = MusicDatabase(Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build())
        LocalMediaScanner.destroyScanner(SCANNER_OWNER_LM)
        scanner = LocalMediaScanner.getScanner(context, SCANNER_OWNER_LM)
    }

    @After
    fun tearDown() = runBlocking {
        try {
            LocalMediaScanner.destroyScanner(SCANNER_OWNER_LM)
        } finally {
            try {
                if (::database.isInitialized) database.close()
            } finally {
                if (::directory.isInitialized) check(directory.deleteRecursively())
            }
        }
    }

    @Test
    fun quickScanRegistersTagsAndFormat() = runBlocking {
        val file = copyAudio("a-before.flac", "a.flac")
        quick(file)
        val song = database.allLocalDbSongs().single()
        assertTags(song, "Scanner Song A", "Original Artist", "Original Album")
        assertEquals(file.absolutePath, song.song.localPath)
        assertNotNull(song.song.inLibrary)
        assertTrue(song.song.isLocal)
        val format = requireNotNull(database.format(song.id).first())
        assertEquals(song.id, format.id)
        assertEquals(44100, format.sampleRate)
        assertTrue(format.bitrate > 0)
        assertTrue(format.mimeType.isNotBlank())
        assertTrue(format.codecs.isNotBlank())
    }

    @Test
    fun quickScanKeepsExistingMetadata() = runBlocking {
        val file = copyAudio("a-before.flac", "a.flac")
        quick(file)
        val before = database.allLocalDbSongs().single()
        val formatBefore = database.format(before.id).first()
        copyAudio("a-after.flac", "a.flac")
        quick(file)
        val after = database.allLocalDbSongs().single()
        assertEquals(before.id, after.id)
        assertTags(after, "Scanner Song A", "Original Artist", "Original Album")
        assertEquals(formatBefore, database.format(after.id).first())
    }

    @Test
    fun fullScanRefreshesMatchedSongWithoutChangingId() = runBlocking {
        val file = copyAudio("a-before.flac", "a.flac")
        quick(file)
        val before = database.allLocalDbSongs().single()
        // Keep the title: syncDB applies a title prefilter even when paths match.
        copyAudio("a-after.flac", "a.flac")
        full(file)
        val after = database.allLocalDbSongs().single()
        assertEquals(before.id, after.id)
        assertEquals(before.song.inLibrary, after.song.inLibrary)
        assertTags(after, "Scanner Song A", "Updated Artist", "Updated Album")
        assertNotNull(database.format(after.id).first())
    }

    @Test
    fun fullScanDisablesMissingSongWhenAnotherValidSongRemains() = runBlocking {
        val a = copyAudio("a-before.flac", "a.flac")
        val b = copyAudio("b.flac", "b.flac")
        quick(a, b)
        val before = database.allLocalDbSongs()
        assertEquals(2, before.size)
        val missingId = before.single { it.song.localPath == a.absolutePath }.id
        val remainingId = before.single { it.song.localPath == b.absolutePath }.id
        // Empty full scans intentionally leave the database unchanged. Keep B in the input.
        full(b)
        val after = database.allLocalDbSongs()
        assertEquals(2, after.size)
        assertNull(after.single { it.id == missingId }.song.inLibrary)
        assertEquals(remainingId, database.allLocalSongs().single().id)
    }

    private fun copyAudio(asset: String, name: String): File = File(directory, name).also { file ->
        InstrumentationRegistry.getInstrumentation().context.assets.open("scanner/$asset").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        check(file.canRead() && file.length() > 0)
    }

    private fun uri(file: File): Uri {
        val storage = context.getSystemService(StorageManager::class.java)
        val root = requireNotNull(storage.primaryStorageVolume.directory)
        val documentId = "primary:${file.relativeTo(root).invariantSeparatorsPath}"
        val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:")
        // fileFromUri only decodes this URI; it does not ask DocumentsProvider to open it.
        // Access comes from the target app owning getExternalFilesDir(), not a synthetic SAF grant.
        return DocumentsContract.buildDocumentUriUsingTree(tree, documentId).also {
            assertEquals(file.canonicalFile, fileFromUri(context, it)?.canonicalFile)
        }
    }

    private suspend fun quick(vararg files: File) = scanner.quickSync(
        database, files.map(::uri), ScannerMatchCriteria.LEVEL_2, false, false,
    )

    private suspend fun full(vararg files: File) = scanner.fullSync(
        database, files.map(::uri), ScannerMatchCriteria.LEVEL_2, false, false,
    )

    private fun assertTags(song: Song, title: String, artist: String, album: String) {
        assertEquals(title, song.song.title)
        assertEquals(listOf(artist), song.artists.map { it.name })
        assertEquals(album, song.song.albumName)
        assertEquals(album, song.album?.title)
        assertNotNull(song.song.albumId)
        assertEquals(song.song.albumId, song.album?.id)
    }
}
