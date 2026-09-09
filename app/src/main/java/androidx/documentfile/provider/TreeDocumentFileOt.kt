/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.documentfile.provider

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(21)
class TreeDocumentFileOt(
    parent: DocumentFile?,
    private val context: Context,
    private var _uri: Uri,
) : DocumentFile(parent) {
    private var _mime: String? = null
    private var _name: String? = null

    var id: String? = null
        private set

    constructor(
        parent: DocumentFile?,
        context: Context,
        uri: Uri,
        name: String,
        mime: String,
    ) : this(parent, context, uri) {
        _mime = mime
        _name = name
        val startIndex = name.lastIndexOf("[")
        val endIndex = name.lastIndexOf("]")
        if (startIndex < endIndex) {
            id = name.substring(startIndex + 1, endIndex)
        }
    }

    override fun createFile(mimeType: String, displayName: String): DocumentFile? {
        val result = createFile(context, _uri, mimeType, displayName)
        return if (result != null) TreeDocumentFileOt(this, context, result) else null
    }

    override fun createDirectory(displayName: String): DocumentFile? {
        val result = createFile(
            context, _uri, DocumentsContract.Document.MIME_TYPE_DIR, displayName
        )
        return if (result != null) TreeDocumentFileOt(this, context, result) else null
    }

    override fun getUri(): Uri {
        return _uri
    }

    override fun getName(): String? {
        return _name ?: DocumentsContractApi19Ot.getName(context, _uri)
    }

    override fun getType(): String? {
        return _mime ?: DocumentsContractApi19Ot.getType(context, _uri)
    }

    override fun isDirectory(): Boolean {
        return DocumentsContractApi19Ot.isDirectory(context, _uri)
    }

    override fun isFile(): Boolean {
        return DocumentsContractApi19Ot.isFile(context, _uri)
    }

    override fun isVirtual(): Boolean {
        return DocumentsContractApi19Ot.isVirtual(context, _uri)
    }

    override fun lastModified(): Long {
        return DocumentsContractApi19Ot.lastModified(context, _uri)
    }

    override fun length(): Long {
        return DocumentsContractApi19Ot.length(context, _uri)
    }

    override fun canRead(): Boolean {
        return DocumentsContractApi19Ot.canRead(context, _uri)
    }

    override fun canWrite(): Boolean {
        return DocumentsContractApi19Ot.canWrite(context, _uri)
    }

    override fun delete(): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, _uri)
        } catch (e: Exception) {
            false
        }
    }

    override fun exists(): Boolean {
        return DocumentsContractApi19Ot.exists(context, _uri)
    }

    override fun listFiles(): Array<DocumentFile> = listFiles(suppressErrors = true)

    /**
     * Lists child documents without converting a provider query failure into a partial result.
     */
    fun listFilesOrThrow(): Array<DocumentFile> = listFiles(suppressErrors = false)

    private fun listFiles(suppressErrors: Boolean): Array<DocumentFile> {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            _uri, DocumentsContract.getDocumentId(_uri)
        )
        val results = ArrayList<Uri>()
        val resultMimes = ArrayList<String>()
        val resultNames = ArrayList<String>()
        var c: Cursor? = null
        try {
            c = resolver.query(
                childrenUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ), null, null, null
            )
            while (c!!.moveToNext()) {
                val documentId = c.getString(0)
                val documentName = c.getString(1)
                val documentMime = c.getString(2)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(_uri, documentId)
                results.add(documentUri)
                resultMimes.add(documentMime)
                resultNames.add(documentName)
            }
        } catch (e: Exception) {
            if (!suppressErrors) throw e
            Log.w(TAG, "Failed query: $e")
        } finally {
            closeQuietly(c)
        }
        return Array<DocumentFile>(results.size) { i ->
            TreeDocumentFileOt(this, context, results[i], resultNames[i], resultMimes[i])
        }
    }

    override fun renameTo(displayName: String): Boolean {
        return try {
            val result =
                DocumentsContract.renameDocument(context.contentResolver, _uri, displayName)
            if (result != null) {
                _uri = result
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "DocumentFile"

        private fun createFile(
            context: Context, self: Uri, mimeType: String, displayName: String
        ): Uri? {
            return try {
                DocumentsContract.createDocument(
                    context.contentResolver, self, mimeType, displayName
                )
            } catch (e: Exception) {
                null
            }
        }

        private fun closeQuietly(closeable: AutoCloseable?) {
            if (closeable != null) {
                try {
                    closeable.close()
                } catch (rethrown: RuntimeException) {
                    throw rethrown
                } catch (ignored: Exception) {
                }
            }
        }
    }
}
