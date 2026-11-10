package io.github.yuuichi_s.astertune.utils.scanners

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import io.github.yuuichi_s.astertune.utils.scanners.LocalMediaScanner.Companion.getScanFiles
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

/** Checks selected folders and discovered songs through the scanner's public entry point. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 36])
class ExcludedScanFolderTest {
    private val external = "com.android.externalstorage.documents"
    private val downloads = "com.android.providers.downloads.documents"
    private val music = "primary:Music"
    private val skip = "$music/Skip"
    private val colon = "$music/Skip:"
    private val rootSong = "$music/root.mp3"
    private val skippedSong = "$skip/a.mp3"
    private val nestedSong = "$skip/Live/b.mp3"
    private val siblingSong = "$music/Skip2/c.mp3"
    private val colonSong = "$colon/d.mp3"
    private val colonSiblingSong = "$music/Skip:Live/e.mp3"
    private val allMusic = listOf(rootSong, skippedSong, nestedSong, siblingSong, colonSong, colonSiblingSong)
    private lateinit var context: Context
    private lateinit var provider: ListingProvider

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        provider = register(external, mapOf(
            music to listOf(audio(rootSong), directory(skip), directory("$music/Skip2"), directory(colon), directory("$music/Skip:Live")),
            skip to listOf(audio(skippedSong), directory("$skip/Live")),
            "$skip/Live" to listOf(audio(nestedSong)),
            "$music/Skip2" to listOf(audio(siblingSong)),
            colon to listOf(audio(colonSong)),
            "$music/Skip:Live" to listOf(audio(colonSiblingSong)),
            "1234-5678:Music" to listOf(audio("1234-5678:Music/sd.mp3")),
        ))
    }

    @Test
    fun scansAllSongsWithoutExclusions() {
        assertEquals(allMusic.map { document(music, it) }, scan(tree(music)))
    }

    @Test
    fun excludesSameSelectedTreeBeforeListing() {
        assertEquals(emptyList<Uri>(), scan(tree(skip), tree(skip)))
        assertEquals(emptyList<String>(), provider.queriedDirectories)
    }

    @Test
    fun excludesSelectedDescendantTreeBeforeListing() {
        assertEquals(emptyList<Uri>(), scan(tree("$skip/Live"), tree(skip)))
        assertEquals(emptyList<String>(), provider.queriedDirectories)
    }

    @Test
    fun excludesFilesAndNestedFilesDiscoveredThroughParentTree() {
        val expected = listOf(rootSong, siblingSong, colonSong, colonSiblingSong)
        assertEquals(expected.map { document(music, it) }, scan(tree(music), tree(skip)))
    }

    @Test
    fun keepsSimilarlyNamedSiblingTree() {
        assertEquals(listOf(document("$music/Skip2", siblingSong)), scan(tree("$music/Skip2"), tree(skip)))
    }

    @Test
    fun excludesColonNamedFolderWithoutExcludingSiblingFiles() {
        val expected = listOf(rootSong, skippedSong, nestedSong, siblingSong, colonSiblingSong)
        assertEquals(expected.map { document(music, it) }, scan(tree(music), tree(colon)))
    }

    @Test
    fun keepsSiblingTreeWhenExcludedFolderEndsWithColon() {
        val sibling = "$music/Skip:Live"
        assertEquals(listOf(document(sibling, colonSiblingSong)), scan(tree(sibling), tree(colon)))
    }

    @Test
    fun excludesVolumeRootBeforeListing() {
        assertEquals(emptyList<Uri>(), scan(tree(music), tree("primary:")))
        assertEquals(emptyList<String>(), provider.queriedDirectories)
    }

    @Test
    fun excludesSecondaryVolumeRootBeforeListing() {
        assertEquals(emptyList<Uri>(), scan(tree("1234-5678:Music"), tree("1234-5678:")))
        assertEquals(emptyList<String>(), provider.queriedDirectories)
    }

    @Test
    fun excludesFilesWhenSelectedTreeIsVolumeRoot() {
        register(external, mapOf(
            "primary:" to listOf(audio("primary:root.mp3"), directory(music)),
            music to listOf(audio("$music/a.mp3")),
        ))
        assertEquals(listOf(document("primary:", "primary:root.mp3")), scan(tree("primary:"), tree(music)))
    }

    @Test
    fun doesNotExcludeAnotherVolume() {
        val sd = "1234-5678:Music"
        assertEquals(listOf(document(sd, "$sd/sd.mp3")), scan(tree(sd), tree("primary:")))
        assertEquals(listOf(sd), provider.queriedDirectories)
    }

    @Test
    fun doesNotExcludeAnotherProvider() {
        val other = "example.documents"
        register(other, mapOf(skip to listOf(audio("$skip/other.mp3"))))
        assertEquals(listOf(document(skip, "$skip/other.mp3", other)), scan(tree(skip, other), tree(skip)))
    }

    @Test
    fun handlesEncodedFolderNamesAndTrailingSlash() {
        val folder = "primary:Music/雨 & Soda/Live #1"
        register(external, mapOf(
            music to listOf(audio(rootSong), directory(folder)),
            folder to listOf(audio("$folder/a %.mp3")),
        ))
        assertEquals(listOf(document(music, rootSong)), scan(tree(music), tree("$folder/")))
    }

    @Test
    fun downloadsRawParentExcludesSelectedDescendantBeforeListing() {
        val folder = "raw:/storage/emulated/0/Download/Skip"
        val included = "$folder/Live"
        val rawProvider = register(downloads, mapOf(included to listOf(audio("$included/a.mp3"))))
        assertEquals(emptyList<Uri>(), scan(tree(included, downloads), tree(folder, downloads)))
        assertEquals(emptyList<String>(), rawProvider.queriedDirectories)
    }

    @Test
    fun downloadsRawSameFolderIsExcludedBeforeListing() {
        val folder = "raw:/storage/emulated/0/Download/Skip"
        val rawProvider = register(downloads, mapOf(folder to listOf(audio("$folder/a.mp3"))))
        assertEquals(emptyList<Uri>(), scan(tree(folder, downloads), tree(folder, downloads)))
        assertEquals(emptyList<String>(), rawProvider.queriedDirectories)
    }

    @Test
    fun downloadsRawSiblingIsNotExcluded() {
        val folder = "raw:/storage/emulated/0/Download/Skip"
        val sibling = "${folder}2"
        register(downloads, mapOf(sibling to listOf(audio("$sibling/a.mp3"))))
        val expected = listOf(document(sibling, "$sibling/a.mp3", downloads))
        assertEquals(expected, scan(tree(sibling, downloads), tree(folder, downloads)))
    }

    @Test
    fun downloadsRawColonNamedFolderDoesNotExcludeSiblingTree() {
        val folder = "raw:/storage/emulated/0/Download/Skip:"
        val sibling = "${folder}Live"
        register(downloads, mapOf(sibling to listOf(audio("$sibling/a.mp3"))))
        val expected = listOf(document(sibling, "$sibling/a.mp3", downloads))
        assertEquals(expected, scan(tree(sibling, downloads), tree(folder, downloads)))
    }

    @Test
    fun excludedDownloadsTreeIsNotReturnedAlongsideValidMusic() {
        val folder = "raw:/storage/emulated/0/Download/Skip"
        val included = "$folder/Live"
        val rawProvider = register(downloads, mapOf(included to listOf(audio("$included/a.mp3"))))
        val uris = getScanFiles(listOf(tree(included, downloads), tree(music)), listOf(tree(folder, downloads)), context)
        assertEquals(allMusic.map { document(music, it) }, uris)
        assertEquals(emptyList<String>(), rawProvider.queriedDirectories)
    }

    @Test
    fun retainsLegacyParentTreeExclusionForOtherProviders() {
        val other = "example.documents"
        val otherProvider = register(other, mapOf("collection/Skip/Live" to listOf(audio("collection/Skip/Live/a.mp3"))))
        assertEquals(emptyList<Uri>(), scan(tree("collection/Skip/Live", other), tree("collection/Skip", other)))
        assertEquals(emptyList<String>(), otherProvider.queriedDirectories)
    }

    private fun scan(included: Uri, vararg excluded: Uri): List<Uri> =
        getScanFiles(listOf(included), excluded.toList(), context)

    private fun tree(id: String, authority: String = external): Uri =
        DocumentsContract.buildTreeDocumentUri(authority, id)

    private fun document(treeId: String, id: String, authority: String = external): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree(treeId, authority), id)

    private fun audio(id: String) = Entry(id, "audio/mpeg")
    private fun directory(id: String) = Entry(id, Document.MIME_TYPE_DIR)

    private fun register(authority: String, children: Map<String, List<Entry>>): ListingProvider =
        ListingProvider(children).also {
            it.attachInfo(context, ProviderInfo().apply { this.authority = authority })
            ShadowContentResolver.registerProviderInternal(authority, it)
        }

    private data class Entry(val id: String, val mime: String)

    private class ListingProvider(private val children: Map<String, List<Entry>>) : ContentProvider() {
        val queriedDirectories = mutableListOf<String>()

        override fun onCreate() = true

        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor {
            check(uri.lastPathSegment == "children") { "Unexpected document query: $uri" }
            val id = DocumentsContract.getDocumentId(uri)
            queriedDirectories.add(id)
            val columns = requireNotNull(projection)
            return MatrixCursor(columns).apply {
                children.getValue(id).forEach { entry ->
                    addRow(columns.map { column ->
                        when (column) {
                            Document.COLUMN_DOCUMENT_ID -> entry.id
                            Document.COLUMN_DISPLAY_NAME -> entry.id.substringAfterLast('/')
                            Document.COLUMN_MIME_TYPE -> entry.mime
                            else -> error("Unexpected column: $column")
                        }
                    })
                }
            }
        }

        override fun getType(uri: Uri): String = error("Unexpected type query: $uri")
        override fun insert(uri: Uri, values: ContentValues?): Uri = error("Read-only test provider")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only test provider")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read-only test provider")
    }
}
