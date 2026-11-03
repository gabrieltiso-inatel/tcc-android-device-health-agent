package com.tcc.devicehealth.agent

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

internal data class MediaFileReference(val uri: Uri, val name: String, val revision: String)

internal fun supportsMediaRemoval(context: Context): Boolean = Build.VERSION.SDK_INT >= 29 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

internal class MediaFileRemovalDataSource(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val collections = listOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
    )

    fun find(fileId: String): MediaFileReference? {
        for (collection in collections) {
            try {
                resolver.query(collection, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val uri = Uri.withAppendedPath(collection, cursor.getLong(0).toString())
                        if (fileReferenceId(uri.toString()) == fileId) {
                            return MediaFileReference(uri, cursor.getString(1).orEmpty(), revision(uri, cursor.getLong(2), cursor.getLong(3)))
                        }
                    }
                }
            } catch (_: SecurityException) {
                continue
            }
        }
        return null
    }

    fun remove(approval: PendingFileApproval): IntentSender? {
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

    fun finish(approval: PendingFileApproval) {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!isAbsent(approval)) throw FileNotAllowedException()
        } else {
            val uri = validate(approval)
            if (resolver.delete(uri, null, null) == 0) throw FileNotFoundException()
        }
    }

    fun isAbsent(approval: PendingFileApproval): Boolean {
        val uri = approvedUri(approval)
        val collection = collections.first { uri.toString().startsWith("$it/") }
        return resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns._ID} = ?", arrayOf(uri.lastPathSegment), null)?.use { !it.moveToFirst() }
            ?: throw FileNotAllowedException()
    }

    private fun validate(approval: PendingFileApproval): Uri {
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

    private fun approvedUri(approval: PendingFileApproval): Uri {
        val uri = approval.mediaUri?.let(Uri::parse) ?: throw FileNotAllowedException()
        if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY || fileReferenceId(uri.toString()) != approval.fileId) {
            throw FileNotAllowedException()
        }
        val collectionIndex = collections.indexOfFirst { collection ->
            uri.toString().startsWith("$collection/") && uri.pathSegments.size == collection.pathSegments.size + 1 &&
                uri.lastPathSegment?.toLongOrNull() != null
        }
        if (collectionIndex < 0) throw FileNotAllowedException()
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)[collectionIndex]
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(appContext, permission) != PackageManager.PERMISSION_GRANTED) throw FileNotAllowedException()
        return uri
    }

    private fun revision(uri: Uri, size: Long, modified: Long): String = fileReferenceId("$uri|${size.coerceAtLeast(0)}|$modified")

    private companion object {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
    }
}
