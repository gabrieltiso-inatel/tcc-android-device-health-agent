package com.tcc.devicehealth.agent.data

import android.content.IntentSender
import com.tcc.devicehealth.agent.data.android.AndroidFileReference
import com.tcc.devicehealth.agent.data.android.DocumentTreeDataSource
import com.tcc.devicehealth.agent.data.android.FileChangedException
import com.tcc.devicehealth.agent.data.android.FileNotAllowedException
import com.tcc.devicehealth.agent.data.android.FileNotFoundException
import com.tcc.devicehealth.agent.data.android.MediaStoreDataSource
import com.tcc.devicehealth.agent.data.android.fileReferenceId
import com.tcc.devicehealth.agent.data.local.AgentPreferencesDataSource
import com.tcc.devicehealth.agent.data.remote.ControllerApiClient
import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.FileInventory
import com.tcc.devicehealth.agent.model.FileSource
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FileRepository {
    suspend fun collectFiles(): FileInventory
    suspend fun syncFiles(): Result<Unit>
    suspend fun addFileTreeUri(uri: String): Result<Unit>
    suspend fun validateFileRemoval(action: Action)
    suspend fun createFileApproval(action: Action): PendingFileRemoval
    suspend fun removeFile(approval: PendingFileRemoval): IntentSender?
    suspend fun finishMediaRemoval(approval: PendingFileRemoval)
    suspend fun isMediaAbsent(approval: PendingFileRemoval): Boolean
}

internal class AndroidFileRepository(
    private val preferences: AgentPreferencesDataSource,
    private val controllerClient: ControllerApiClient,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val documentTreeDataSource: DocumentTreeDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileRepository {
    override suspend fun collectFiles(): FileInventory = withContext(ioDispatcher) {
        val media = mediaStoreDataSource.collect(maximumFileCount)
        val sources = media.sources.toMutableList()
        val files = media.files.toMutableList()
        for (tree in preferences.getFileTreeUris()) {
            if (files.size >= maximumFileCount) break
            val sourceId = fileReferenceId(tree)
            try {
                val collected = documentTreeDataSource.collect(tree, maximumFileCount)
                files += collected.take(maximumFileCount - files.size)
                sources += FileSource(sourceId, "Selected folder", "selected_folder", "available", collected.size)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                sources += FileSource(sourceId, "Selected folder", "selected_folder", "unavailable")
            }
        }
        FileInventory(Instant.now().toString(), sources, files)
    }

    override suspend fun syncFiles(): Result<Unit> = repositoryOperation(ioDispatcher) {
        val token = checkNotNull(preferences.getToken()) { "Device is not paired" }
        controllerClient.sendFileInventory(preferences.getDeviceId(), collectFiles(), token)
    }

    override suspend fun addFileTreeUri(uri: String): Result<Unit> = repositoryOperation(ioDispatcher) {
        preferences.addFileTreeUri(uri)
    }

    override suspend fun validateFileRemoval(action: Action) = withContext(ioDispatcher) {
        requireRemovableFile(action.fileId, action.expectedRevision)
        Unit
    }

    override suspend fun createFileApproval(action: Action): PendingFileRemoval = withContext(ioDispatcher) {
        val file = requireRemovableFile(action.fileId, action.expectedRevision)
        PendingFileRemoval(
            actionId = action.id, fileId = checkNotNull(action.fileId),
            expectedRevision = action.expectedRevision ?: file.revision, name = file.name,
            mediaUri = file.uri.toString().takeIf { file.isMedia },
        )
    }

    override suspend fun removeFile(approval: PendingFileRemoval): IntentSender? = withContext(ioDispatcher) {
        if (approval.mediaUri != null) {
            mediaStoreDataSource.remove(approval)
        } else {
            val file = requireRemovableFile(approval.fileId, approval.expectedRevision)
            documentTreeDataSource.remove(file)
            null
        }
    }

    override suspend fun finishMediaRemoval(approval: PendingFileRemoval) = withContext(ioDispatcher) {
        mediaStoreDataSource.finish(approval)
    }

    override suspend fun isMediaAbsent(approval: PendingFileRemoval): Boolean = withContext(ioDispatcher) {
        mediaStoreDataSource.isAbsent(approval)
    }

    private suspend fun requireRemovableFile(fileId: String?, expectedRevision: String?): AndroidFileReference {
        val file = fileId?.let { findFile(it) } ?: throw FileNotFoundException()
        if (!file.canRemove) throw FileNotAllowedException()
        if (expectedRevision != null && file.revision != expectedRevision) throw FileChangedException()
        return file
    }

    private suspend fun findFile(fileId: String): AndroidFileReference? {
        mediaStoreDataSource.find(fileId)?.let { media ->
            return AndroidFileReference(media.uri, media.revision, media.canRemove, media.name, isMedia = true)
        }
        for (tree in preferences.getFileTreeUris()) {
            documentTreeDataSource.find(tree, fileId)?.let { return it }
        }
        return null
    }

    private companion object {
        const val maximumFileCount = 5_000
    }
}
