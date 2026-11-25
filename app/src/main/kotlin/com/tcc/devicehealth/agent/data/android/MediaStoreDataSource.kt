package com.tcc.devicehealth.agent.data.android

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.tcc.devicehealth.agent.model.FileItem
import com.tcc.devicehealth.agent.model.FileSource
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class MediaFileReference(val uri: Uri, val name: String, val revision: String, val canRemove: Boolean)

internal fun supportsMediaRemoval(context: Context): Boolean = Build.VERSION.SDK_INT >= 29 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

internal class MediaStoreDataSource(
    context: Context,
    private val resolver: ContentResolver = context.applicationContext.contentResolver,
    private val canRead: (String) -> Boolean = { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    },
) {
    private val appContext = context.applicationContext
    private val collections = listOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
    )

    suspend fun collect(remaining: Int): MediaCollection {
        val sources = mutableListOf<FileSource>()
        val files = mutableListOf<FileItem>()
        for ((index, collection) in collections.withIndex()) {
            val sourceId = sourceIds[index]
            if (!canRead(readPermission(index))) {
                sources += FileSource(sourceId, sourceLabels[index], "media", "requires_consent")
                continue
            }
            try {
                val collected = queryInventory(sourceId, collection, remaining - files.size)
                files += collected
                sources += FileSource(sourceId, sourceLabels[index], "media", "available", collected.size)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                sources += FileSource(sourceId, sourceLabels[index], "media", "unavailable")
            }
        }
        return MediaCollection(sources, files)
    }

    private suspend fun queryInventory(sourceId: String, collection: Uri, limit: Int): List<FileItem> {
        val files = mutableListOf<FileItem>()
        val columns = projection + MediaStore.MediaColumns.MIME_TYPE
        val cursor = resolver.query(collection, columns, null, null, "${MediaStore.MediaColumns.SIZE} DESC")
            ?: throw FileNotAllowedException()
        cursor.use {
            while (files.size < limit && it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                files += readInventoryItem(sourceId, collection, it)
            }
        }
        return files
    }

    private fun readInventoryItem(sourceId: String, collection: Uri, cursor: Cursor): FileItem {
        val uri = Uri.withAppendedPath(collection, cursor.getLong(0).toString())
        val size = cursor.getLong(2).coerceAtLeast(0)
        val modified = cursor.getLong(3)
        val mimeType = cursor.getString(4)
        return FileItem(
            fileId = fileReferenceId(uri.toString()), sourceId = sourceId,
            name = cursor.getString(1).orEmpty().ifEmpty { "Unnamed file" },
            kind = fileKind(mimeType), mimeType = mimeType, sizeBytes = size,
            modifiedAt = modified.takeIf { it > 0 }?.let { Instant.ofEpochSecond(it).toString() },
            canRemove = supportsMediaRemoval(appContext), revision = revision(uri, size, modified),
        )
    }

    private fun readPermission(index: Int): String = if (Build.VERSION.SDK_INT >= 33) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)[index]
    } else Manifest.permission.READ_EXTERNAL_STORAGE

    suspend fun find(fileId: String): MediaFileReference? {
        for (collection in collections) {
            try {
                resolver.query(collection, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val uri = Uri.withAppendedPath(collection, cursor.getLong(0).toString())
                        if (fileReferenceId(uri.toString()) == fileId) {
                            return MediaFileReference(uri, cursor.getString(1).orEmpty(), revision(uri, cursor.getLong(2), cursor.getLong(3)), supportsMediaRemoval(appContext))
                        }
                    }
                }
            } catch (_: SecurityException) {
                continue
            }
        }
        return null
    }

    fun remove(approval: PendingFileRemoval): IntentSender? {
        val uri = validate(approval)
        if (Build.VERSION.SDK_INT >= 30) {
            return MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender
        }
        try {
            if (resolver.delete(uri, null, null) == 0) throw FileNotFoundException()
        } catch (error: SecurityException) {
            if (Build.VERSION.SDK_INT == 29 && error is RecoverableSecurityException) {
                return error.userAction.actionIntent.intentSender
            }
            throw FileNotAllowedException()
        }
        return null
    }

    fun finish(approval: PendingFileRemoval) {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!isAbsent(approval)) throw FileNotAllowedException()
        } else {
            val uri = validate(approval)
            if (resolver.delete(uri, null, null) == 0) throw FileNotFoundException()
        }
    }

    fun isAbsent(approval: PendingFileRemoval): Boolean {
        val uri = approvedUri(approval)
        val collection = collections.first { uri.toString().startsWith("$it/") }
        return resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns._ID} = ?", arrayOf(uri.lastPathSegment), null)?.use { !it.moveToFirst() }
            ?: throw FileNotAllowedException()
    }

    private fun validate(approval: PendingFileRemoval): Uri {
        if (!supportsMediaRemoval(appContext)) throw FileNotAllowedException()
        val uri = approvedUri(approval)
        val collection = collections.first { uri.toString().startsWith("$it/") }
        resolver.query(collection, projection, "${MediaStore.MediaColumns._ID} = ?", arrayOf(uri.lastPathSegment), null)?.use { cursor ->
            if (!cursor.moveToFirst()) throw FileNotFoundException()
            val currentRevision = revision(uri, cursor.getLong(2), cursor.getLong(3))
            if (approval.expectedRevision != null && approval.expectedRevision != currentRevision) throw FileChangedException()
        } ?: throw FileNotAllowedException()
        return uri
    }

    private fun approvedUri(approval: PendingFileRemoval): Uri {
        val uri = approval.mediaUri?.let(Uri::parse) ?: throw FileNotAllowedException()
        if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY || fileReferenceId(uri.toString()) != approval.fileId) {
            throw FileNotAllowedException()
        }
        val collectionIndex = collections.indexOfFirst { collection ->
            uri.toString().startsWith("$collection/") && uri.pathSegments.size == collection.pathSegments.size + 1 &&
                uri.lastPathSegment?.toLongOrNull() != null
        }
        if (collectionIndex < 0) throw FileNotAllowedException()
        if (!canRead(readPermission(collectionIndex))) throw FileNotAllowedException()
        return uri
    }

    private fun revision(uri: Uri, size: Long, modified: Long): String = fileReferenceId("$uri|${size.coerceAtLeast(0)}|$modified")

    private companion object {
        val sourceIds = listOf("media-images", "media-videos", "media-audio")
        val sourceLabels = listOf("Images", "Videos", "Audio")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
    }
}

data class AndroidFileRemovalConfirmation(val actionId: String, val intentSender: IntentSender)

internal data class MediaCollection(val sources: List<FileSource>, val files: List<FileItem>)
