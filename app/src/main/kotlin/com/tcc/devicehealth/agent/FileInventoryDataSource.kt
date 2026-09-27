package com.tcc.devicehealth.agent

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

private const val maximumFileCount = 5_000

private data class MediaSource(
    val sourceId: String,
    val label: String,
    val collection: Uri,
)

internal class FileInventoryDataSource(
    context: Context,
    private val preferences: AgentPreferences,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    suspend fun collect(): ActionExecution {
        val sources = mutableListOf<FileSource>()
        val files = mutableListOf<FileItem>()
        collectMedia(sources, files)
        for (treeUri in preferences.getFileTreeUris()) {
            if (files.size >= maximumFileCount) {
                break
            }
            collectTree(treeUri, sources, files)
        }
        val inventory = JSONObject()
            .put("capturedAt", Instant.now().toString())
            .put("sources", JSONArray().apply { sources.forEach { put(sourceJson(it)) } })
            .put("files", JSONArray().apply { files.forEach { put(fileJson(it)) } })
        return ActionExecution("File inventory collected", inventory.toString())
    }

    private fun collectMedia(sources: MutableList<FileSource>, files: MutableList<FileItem>) {
        val mediaSources = listOf(
            MediaSource("media-images", "Images", MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            MediaSource("media-videos", "Videos", MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
            MediaSource("media-audio", "Audio", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
        )
        mediaSources.forEach { source ->
            if (!hasMediaAccess(source.sourceId)) {
                sources += FileSource(source.sourceId, source.label, "media", "requires_consent")
                return@forEach
            }
            val collected = queryMedia(source.sourceId, source.collection, files)
            sources += FileSource(source.sourceId, source.label, "media", "available", collected)
        }
    }

    private fun hasMediaAccess(sourceId: String): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            when (sourceId) {
                "media-images" -> Manifest.permission.READ_MEDIA_IMAGES
                "media-videos" -> Manifest.permission.READ_MEDIA_VIDEO
                else -> Manifest.permission.READ_MEDIA_AUDIO
            }
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun queryMedia(sourceId: String, collection: Uri, files: MutableList<FileItem>): Int {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        var count = 0
        runCatching {
            resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.SIZE} DESC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext() && files.size < maximumFileCount) {
                    val id = cursor.getLong(idIndex)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    val name = cursor.getString(nameIndex).orEmpty().ifEmpty { "Unnamed file" }
                    val size = cursor.getLong(sizeIndex).coerceAtLeast(0)
                    val modifiedSeconds = cursor.getLong(modifiedIndex)
                    files += FileItem(
                        fileId = fileReferenceId(uri.toString()),
                        sourceId = sourceId,
                        name = name,
                        kind = sourceId.removePrefix("media-"),
                        mimeType = cursor.getString(mimeIndex),
                        sizeBytes = size,
                        modifiedAt = modifiedSeconds.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it).toString() },
                        canRemove = false,
                        revision = fileReferenceId("$uri|$size|$modifiedSeconds"),
                    )
                    count += 1
                }
            }
        }
        return count
    }

    private fun collectTree(treeUriValue: String, sources: MutableList<FileSource>, files: MutableList<FileItem>) {
        val treeUri = Uri.parse(treeUriValue)
        val sourceId = fileReferenceId(treeUriValue)
        val collected = mutableListOf<FileItem>()
        val result = runCatching {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            walkTree(treeUri, rootId, "", collected, 0)
        }
        if (result.isSuccess) {
            files += collected.take(maximumFileCount - files.size)
            sources += FileSource(sourceId, "Selected folder", "selected_folder", "available", collected.size)
        } else {
            sources += FileSource(sourceId, "Selected folder", "selected_folder", "unavailable")
        }
    }

    private fun walkTree(treeUri: Uri, parentId: String, parentPath: String, files: MutableList<FileItem>, depth: Int) {
        if (depth > 20 || files.size >= maximumFileCount) {
            return
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
        resolver.query(childrenUri, projection, null, null, "${DocumentsContract.Document.COLUMN_LAST_MODIFIED} DESC")?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)
            while (cursor.moveToNext() && files.size < maximumFileCount) {
                val documentId = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex).orEmpty().ifEmpty { "Unnamed file" }
                val mimeType = cursor.getString(mimeIndex)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                val currentPath = if (parentPath.isEmpty()) name else "$parentPath/$name"
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walkTree(treeUri, documentId, currentPath, files, depth + 1)
                } else {
                    val size = cursor.getLong(sizeIndex).coerceAtLeast(0)
                    val modifiedMillis = cursor.getLong(modifiedIndex)
                    files += FileItem(
                        fileId = fileReferenceId(documentUri.toString()),
                        sourceId = fileReferenceId(treeUri.toString()),
                        name = name,
                        kind = fileKind(mimeType),
                        mimeType = mimeType,
                        sizeBytes = size,
                        modifiedAt = modifiedMillis.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() },
                        displayPath = currentPath,
                        canRemove = cursor.getInt(flagsIndex) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0,
                        revision = fileReferenceId("$documentUri|$size|$modifiedMillis"),
                    )
                }
            }
        }
    }

    private fun fileKind(mimeType: String?): String = when {
        mimeType?.startsWith("image/") == true -> "image"
        mimeType?.startsWith("video/") == true -> "video"
        mimeType?.startsWith("audio/") == true -> "audio"
        else -> "document"
    }

    private fun sourceJson(source: FileSource): JSONObject = JSONObject()
        .put("sourceId", source.sourceId)
        .put("label", source.label)
        .put("kind", source.kind)
        .put("authorization", source.authorization)
        .apply { source.itemCount?.let { put("itemCount", it) } }

    private fun fileJson(file: FileItem): JSONObject = JSONObject()
        .put("fileId", file.fileId)
        .put("sourceId", file.sourceId)
        .put("name", file.name)
        .put("kind", file.kind)
        .apply {
            file.mimeType?.let { put("mimeType", it) }
            put("sizeBytes", file.sizeBytes)
            file.modifiedAt?.let { put("modifiedAt", it) }
            file.displayPath?.let { put("displayPath", it) }
            put("canRemove", file.canRemove)
            put("revision", file.revision)
        }

}
