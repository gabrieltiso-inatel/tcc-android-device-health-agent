package com.tcc.devicehealth.agent

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

internal class FileNotFoundException : Exception()
internal class FileChangedException : Exception()
internal class FileNotAllowedException : Exception()

internal class FileRemovalDataSource(
    context: Context,
    private val preferences: AgentPreferences,
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    suspend fun prepare(action: DeviceAction): ActionExecution {
        val fileId = action.fileId ?: throw FileNotFoundException()
        val target = findFile(fileId) ?: throw FileNotFoundException()
        if (!target.canRemove) {
            throw FileNotAllowedException()
        }
        return ActionExecution("File removal requires approval", approvalRequired = true)
    }

    suspend fun remove(approval: PendingFileApproval) {
        val target = findFile(approval.fileId) ?: throw FileNotFoundException()
        if (approval.expectedRevision != null && target.revision != approval.expectedRevision) {
            throw FileChangedException()
        }
        if (!target.canRemove) {
            throw FileNotAllowedException()
        }
        if (!DocumentsContract.deleteDocument(resolver, target.uri)) {
            throw FileNotAllowedException()
        }
    }

    private suspend fun findFile(fileId: String): FileTargetReference? {
        for (treeUriValue in preferences.getFileTreeUris()) {
            val treeUri = Uri.parse(treeUriValue)
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val target = findInTree(treeUri, rootId, 0, fileId)
            if (target != null) {
                return target
            }
        }
        return null
    }

    private fun findInTree(treeUri: Uri, parentId: String, depth: Int, fileId: String): FileTargetReference? {
        if (depth > 20) {
            return null
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)
            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idIndex)
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                val mimeType = cursor.getString(mimeIndex)
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    findInTree(treeUri, documentId, depth + 1, fileId)?.let { return it }
                } else if (fileReferenceId(uri.toString()) == fileId) {
                    val size = cursor.getLong(sizeIndex).coerceAtLeast(0)
                    val modified = cursor.getLong(modifiedIndex)
                    return FileTargetReference(
                        uri = uri,
                        revision = fileReferenceId("$uri|$size|$modified"),
                        canRemove = cursor.getInt(flagsIndex) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
                    )
                }
            }
        }
        return null
    }
}

private data class FileTargetReference(
    val uri: Uri,
    val revision: String,
    val canRemove: Boolean,
)
