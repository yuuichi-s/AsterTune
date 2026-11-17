package io.github.yuuichi_s.astertune.playback.downloadManager

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.TreeDocumentFileOt
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.utils.scanners.LocalMediaScanner.Companion.scanDfRecursive
import io.github.yuuichi_s.astertune.utils.scanners.documentFileFromUri
import java.io.IOException
import java.io.InputStream

class DownloadDirectoryManagerOt(private var context: Context, dir: Uri, extraDirs: List<Uri>) {
    val TAG = DownloadDirectoryManagerOt::class.simpleName.toString()

    /** The main download directory passed to the last [doInit] call. */
    var dir: Uri = dir
        private set

    var mainDir: DocumentFile? = null
    var allDirs: List<DocumentFile> = mutableListOf()

    @Volatile
    var availableFiles: Set<DocumentFile> = emptySet()

    init {
        doInit(context, dir, extraDirs)
    }

    fun doInit(context: Context, dir: Uri, extraDirs: List<Uri>) {
        Log.i(TAG, "Initializing download manager: $dir")
        this.context = context
        this.dir = dir
        try {
            mainDir = documentFileFromUri(context, dir)
            if (mainDir == null || !mainDir!!.isDirectory) {
                throw IOException("Invalid directory")
            }

            val newAllDirs = mutableListOf<DocumentFile>()
            newAllDirs.add(mainDir!!)
            if (extraDirs.isNotEmpty()) {
                newAllDirs.addAll(
                    documentFileFromUri(context, extraDirs.filterNot { it == dir }).filter { it.isDirectory }
                )
            }
            allDirs = newAllDirs.toList()
            Log.i(TAG, "Download manager initialized successfully. ${allDirs.size}")
        } catch (e: Exception) {
            if (mainDir == null) {
                Log.w(TAG, "Failed to initiate download manager: No directory provided")
            } else if (!mainDir!!.isDirectory) {
                Log.w(TAG, "Failed to initiate download manager: Not a valid directory")
            } else {
                Log.e(TAG, "Failed to initiate download manager: " + e.message)
            }

            mainDir = null
            allDirs = mutableListOf()
        }
    }

    /**
     * Deletes the file for [mediaId] and removes its entry from [availableFiles].
     *
     * @return `true` if a matching entry is found and its file is deleted or reported as missing.
     *     Otherwise, `false`.
     */
    fun deleteFile(mediaId: String): Boolean {
        val file = findFile(mediaId) ?: return false
        if (!file.delete() && file.exists()) return false

        synchronized(this) { availableFiles = availableFiles - file }

        return true
    }

    fun deleteTempFiles() {
        mainDir?.listFiles()
            ?.filter { file ->
                val name = file.name ?: return@filter false
                name.startsWith(TEMP_PREFIX) && name.endsWith(TEMP_SUFFIX) && file.isFile
            }
            ?.forEach { it.delete() }
    }

    /**
     * Saves [input] in the main download directory and adds the file to [availableFiles].
     *
     * @return the saved file, or `null` if the directory is unavailable or saving fails.
     */
    fun saveFile(mediaId: String, input: InputStream, displayName: String?): DocumentFile? {
        val directory = mainDir ?: return null
        val temp = directory.createFile(TEMP_MIME, "$TEMP_PREFIX$mediaId$TEMP_SUFFIX") ?: return null

        try {
            val out = context.contentResolver.openOutputStream(temp.uri) ?: throw IOException("Cannot open ${temp.uri}")
            out.use { input.copyTo(it) }
            val fileName = "${displayName.orEmpty()} [$mediaId].mka"
            if (!temp.renameTo(fileName)) {
                throw IOException("Cannot rename ${temp.uri}")
            }

            val saved = TreeDocumentFileOt(directory, context, temp.uri, temp.name ?: fileName, "audio/mka")
            synchronized(this) { availableFiles = availableFiles + saved }
            return saved
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save $mediaId: ${e.message}")
            temp.delete()
            return null
        }
    }

    fun findFile(mediaId: String): DocumentFile? {
        return availableFiles.find { (it as? TreeDocumentFileOt)?.id == mediaId }
    }

    fun getFilePathIfExists(mediaId: String): Uri? {
        return findFile(mediaId)?.uri
    }

    fun getMissingFiles(mediaId: List<Song>): List<Song> {
        val missingFiles = mediaId.toMutableSet()
        val result = getAvailableFiles(false)
        missingFiles.removeIf { f -> result.any { it.key == f.id } }
        return missingFiles.toList()
    }

    fun getAvailableFiles(useCache: Boolean = true): Map<String, Uri> {
        val availableFiles = HashMap<String, Uri>()
        val result = ArrayList<DocumentFile>()
        if (useCache) {
            result.addAll(this.availableFiles.toList())
        } else {
            for (dir in allDirs) {
                scanDfRecursive(dir, result, true)
            }
        }

        for (file in result) {
            val path = file.name ?: continue
            availableFiles.put(path.substringAfterLast('[').substringBeforeLast(']'), file.uri)
        }
        if (!useCache) {
            synchronized(this) { this.availableFiles = result.toSet() }
        }

        return availableFiles
    }

    fun getMainDlStorageUsage(): Long {
        if (mainDir == null) return -1L
        val result = ArrayList<DocumentFile>()
        scanDfRecursive(mainDir!!, result, true)

        return result.filter { it.name != null }.sumOf { it.length() }
    }

    fun getExtraDlStorageUsage(): Long {
        val dirs = allDirs.filter { it != mainDir }
        if (dirs.isEmpty()) return 0
        val result = ArrayList<DocumentFile>()
        for (dir in dirs) {
            scanDfRecursive(dir, result, true)
        }

        return result.filter { it.name != null }.sumOf { it.length() }
    }

    companion object {
        private const val TEMP_PREFIX = ".astertune-"
        private const val TEMP_SUFFIX = ".part"
        private const val TEMP_MIME = "application/octet-stream"
    }
}
