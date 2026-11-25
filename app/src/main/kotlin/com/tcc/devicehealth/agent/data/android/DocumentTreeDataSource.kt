package com.tcc.devicehealth.agent.data.android

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.tcc.devicehealth.agent.model.FileItem
import java.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class FileNotFoundException : Exception()
internal class FileChangedException : Exception()
internal class FileNotAllowedException : Exception()

internal data class AndroidFileReference(
    val uri: Uri,
    val revision: String,
    val canRemove: Boolean,
    val name: String,
    val isMedia: Boolean = false,
)

internal class DocumentTreeDataSource(
    context: Context,
    private val resolver: ContentResolver = context.applicationContext.contentResolver,
) {
    suspend fun collect(tree: String, limit: Int): List<FileItem> {
        val treeUri = Uri.parse(tree)
        val files = mutableListOf<FileItem>()
        walk(treeUri, DocumentsContract.getTreeDocumentId(treeUri), "", 0, limit, files)
        return files
    }

    private suspend fun walk(tree: Uri, parentId: String, parentPath: String, depth: Int, limit: Int, files: MutableList<FileItem>) {
        if (depth > maximumDepth || files.size >= limit) return
        forEachChild(tree, parentId, "${DocumentsContract.Document.COLUMN_LAST_MODIFIED} DESC") { entry ->
            val path = if (parentPath.isEmpty()) entry.name else "$parentPath/${entry.name}"
            if (entry.isDirectory) {
                walk(tree, entry.id, path, depth + 1, limit, files)
            } else {
                files += entry.toFileItem(tree, path)
            }
            files.size < limit
        }
    }

    suspend fun find(tree: String, fileId: String): AndroidFileReference? {
        val treeUri = Uri.parse(tree)
        return findInTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri), 0, fileId)
    }

    private suspend fun findInTree(tree: Uri, parentId: String, depth: Int, fileId: String): AndroidFileReference? {
        if (depth > maximumDepth) return null
        var found: AndroidFileReference? = null
        forEachChild(tree, parentId, null) { entry ->
            found = if (entry.isDirectory) {
                findInTree(tree, entry.id, depth + 1, fileId)
            } else {
                val reference = entry.toReference(tree)
                reference.takeIf { fileReferenceId(it.uri.toString()) == fileId }
            }
            found == null
        }
        return found
    }

    fun remove(reference: AndroidFileReference) {
        if (!reference.canRemove || !DocumentsContract.deleteDocument(resolver, reference.uri)) {
            throw FileNotAllowedException()
        }
    }

    private suspend fun forEachChild(tree: Uri, parentId: String, sort: String?, visit: suspend (DocumentEntry) -> Boolean) {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cursor = resolver.query(uri, projection, null, null, sort) ?: throw FileNotAllowedException()
        cursor.use {
            val columns = DocumentColumns(it)
            while (it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                if (!visit(columns.read(it))) break
            }
        }
    }

    private companion object {
        const val maximumDepth = 20
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
    }
}

private class DocumentColumns(cursor: Cursor) {
    private val id = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
    private val name = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    private val mime = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
    private val size = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
    private val modified = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    private val flags = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)

    fun read(cursor: Cursor) = DocumentEntry(
        cursor.getString(id), cursor.getString(name).orEmpty().ifEmpty { "Unnamed file" },
        cursor.getString(mime), cursor.getLong(size).coerceAtLeast(0), cursor.getLong(modified), cursor.getInt(flags),
    )
}

private data class DocumentEntry(
    val id: String,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val modified: Long,
    val flags: Int,
) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

    fun toReference(tree: Uri): AndroidFileReference {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        return AndroidFileReference(
            uri, fileReferenceId("$uri|$size|$modified"),
            flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0, name,
        )
    }

    fun toFileItem(tree: Uri, path: String): FileItem {
        val reference = toReference(tree)
        return FileItem(
            fileId = fileReferenceId(reference.uri.toString()), sourceId = fileReferenceId(tree.toString()),
            name = name, kind = fileKind(mimeType), mimeType = mimeType, sizeBytes = size,
            modifiedAt = modified.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() },
            displayPath = path, canRemove = reference.canRemove, revision = reference.revision,
        )
    }
}

internal fun fileKind(mimeType: String?): String = when {
    mimeType?.startsWith("image/") == true -> "image"
    mimeType?.startsWith("video/") == true -> "video"
    mimeType?.startsWith("audio/") == true -> "audio"
    else -> "document"
}
