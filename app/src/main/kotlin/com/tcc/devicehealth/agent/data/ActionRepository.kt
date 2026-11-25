package com.tcc.devicehealth.agent.data

import com.tcc.devicehealth.agent.actions.ActionExecutor
import com.tcc.devicehealth.agent.actions.applicationRemovalDecisionResult
import com.tcc.devicehealth.agent.actions.applicationRemovalReviewResult
import com.tcc.devicehealth.agent.actions.mapExecutionResult
import com.tcc.devicehealth.agent.data.android.ActionNotificationDataSource
import com.tcc.devicehealth.agent.data.android.AndroidFileRemovalConfirmation
import com.tcc.devicehealth.agent.data.android.FileChangedException
import com.tcc.devicehealth.agent.data.android.FileNotAllowedException
import com.tcc.devicehealth.agent.data.android.FileNotFoundException
import com.tcc.devicehealth.agent.data.local.AgentPreferencesDataSource
import com.tcc.devicehealth.agent.data.local.LocalActionDataSource
import com.tcc.devicehealth.agent.data.remote.ControllerApiClient
import com.tcc.devicehealth.agent.data.remote.ControllerApiResponseException
import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.ActionExecutionResult
import com.tcc.devicehealth.agent.model.ApplicationRemovalDecision
import com.tcc.devicehealth.agent.model.ApplicationRemovalReview
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ActionRepository {
    suspend fun processPendingActions(): Result<Int>
    suspend fun submitAction(
        type: String,
        applicationId: String? = null,
        fileId: String? = null,
        expectedRevision: String? = null,
        expectedVersionCode: Long? = null,
    ): Result<Unit>
    suspend fun getPendingApplicationRemovals(): Result<List<PendingApplicationRemoval>>
    suspend fun getPendingFileRemovals(): Result<List<PendingFileRemoval>>
    suspend fun reviewApplicationRemoval(actionId: String): Result<ApplicationRemovalReview>
    suspend fun completeApplicationRemoval(actionId: String, decision: ApplicationRemovalDecision): Result<Unit>
    suspend fun completeFileRemoval(actionId: String, approved: Boolean): Result<AndroidFileRemovalConfirmation?>
    suspend fun completePlatformFileRemoval(actionId: String, approved: Boolean, launchFailed: Boolean = false): Result<Unit>
    suspend fun getActionHistory(): Result<List<Action>>
}

internal class AndroidActionRepository(
    private val preferences: AgentPreferencesDataSource,
    private val localActions: LocalActionDataSource,
    private val controllerClient: ControllerApiClient,
    private val deviceRepository: DeviceRepository,
    private val actionExecutor: ActionExecutor,
    private val applicationRepository: ApplicationRepository,
    private val fileRepository: FileRepository,
    private val approvalNotificationDataSource: ActionNotificationDataSource,
    private val lifecycleMutex: Mutex,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ActionRepository {
    override suspend fun processPendingActions(): Result<Int> = actionOperation {
        val telemetry = deviceRepository.readTelemetry()
        val token = requireToken()
        resendCompletedApprovalResults()
        val actions = controllerClient.getPendingActions(telemetry.deviceId, token)
        actions.forEach { action -> processAction(action, token) }
        actions.size
    }

    override suspend fun submitAction(type: String, applicationId: String?, fileId: String?, expectedRevision: String?, expectedVersionCode: Long?): Result<Unit> = actionOperation {
        val telemetry = deviceRepository.readTelemetry()
        val token = requireToken()
        val action = controllerClient.createAction(telemetry.deviceId, type, token, applicationId, expectedVersionCode, fileId, expectedRevision)
        processAction(action, token)
    }

    override suspend fun getPendingApplicationRemovals(): Result<List<PendingApplicationRemoval>> = repositoryOperation(ioDispatcher) {
        localActions.getPendingApplicationRemovals().filter { approval -> finalActionResult(approval.actionId) == null }
    }

    override suspend fun getPendingFileRemovals(): Result<List<PendingFileRemoval>> = repositoryOperation(ioDispatcher) {
        localActions.getPendingFileRemovals().filter { approval -> finalActionResult(approval.actionId) == null }
    }

    override suspend fun reviewApplicationRemoval(actionId: String): Result<ApplicationRemovalReview> = actionOperation {
        val approval = requireApplicationApproval(actionId)
        val result = finalActionResult(actionId) ?: applicationRemovalReviewResult(
            isRemoved = applicationRepository.isApplicationRemoved(approval),
            validationFailure = repositoryOperation(ioDispatcher) { applicationRepository.validateApplicationRemoval(approval) }.exceptionOrNull(),
        )
        if (result == null) {
            ApplicationRemovalReview.ConfirmationRequired
        } else {
            finishApplicationRemoval(approval, result)
            ApplicationRemovalReview.Completed
        }
    }

    override suspend fun completeApplicationRemoval(actionId: String, decision: ApplicationRemovalDecision): Result<Unit> = actionOperation {
        val approval = requireApplicationApproval(actionId)
        val result = finalActionResult(actionId) ?: applicationRemovalDecisionResult(
            decision = decision,
            isRemoved = applicationRepository.isApplicationRemoved(approval),
        )
        finishApplicationRemoval(approval, result)
    }

    override suspend fun completeFileRemoval(actionId: String, approved: Boolean): Result<AndroidFileRemovalConfirmation?> = actionOperation {
        val approval = localActions.getPendingFileRemovals().firstOrNull { pending -> pending.actionId == actionId }
            ?: error("File removal approval is no longer pending")
        val previous = finalActionResult(actionId)
        val result = previous ?: if (!approved) {
            ActionExecutionResult(false, "File removal cancelled", "cancelled", "approval_declined")
        } else {
            try {
                if (approval.mediaUri != null) {
                    localActions.savePendingFileRemoval(approval.copy(platformRequestStarted = true))
                }
                val intentSender = fileRepository.removeFile(approval)
                if (intentSender != null) {
                    return@actionOperation AndroidFileRemovalConfirmation(actionId, intentSender)
                }
                ActionExecutionResult(true, "File removed", "completed", "file_removed")
            } catch (error: FileNotFoundException) {
                ActionExecutionResult(false, "File is no longer available", "failed", "file_not_found")
            } catch (error: FileChangedException) {
                ActionExecutionResult(false, "File changed since it was selected", "failed", "file_changed")
            } catch (error: FileNotAllowedException) {
                ActionExecutionResult(false, "File cannot be removed", "failed", "file_not_allowed")
            } catch (error: SecurityException) {
                ActionExecutionResult(false, "File access is no longer authorized", "failed", "file_not_allowed")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ActionExecutionResult(false, "File removal failed", "failed", "execution_failed")
            }
        }
        recordFileResult(approval, result)
        null
    }

    override suspend fun completePlatformFileRemoval(actionId: String, approved: Boolean, launchFailed: Boolean): Result<Unit> = actionOperation {
        val approval = localActions.getPendingFileRemovals().firstOrNull { it.actionId == actionId }
            ?: return@actionOperation
        check(approval.platformRequestStarted && approval.mediaUri != null)
        val previous = finalActionResult(actionId)
        val result = previous ?: if (launchFailed) {
            ActionExecutionResult(false, "Could not open the system confirmation", "failed", "execution_failed")
        } else if (!approved) {
            ActionExecutionResult(false, "File removal cancelled", "cancelled", "approval_declined")
        } else {
            val execution = repositoryOperation(ioDispatcher) {
                fileRepository.finishMediaRemoval(approval)
                ActionExecutionResult(succeeded = true, message = "File removed")
            }
            when (val error = execution.exceptionOrNull()) {
                null -> ActionExecutionResult(true, "File removed", "completed", "file_removed")
                is SecurityException -> ActionExecutionResult(false, "File access is no longer authorized", "failed", "file_not_allowed")
                else -> mapExecutionResult(Result.failure(error)) ?: throw error
            }
        }
        recordFileResult(approval, result)
    }

    override suspend fun getActionHistory(): Result<List<Action>> = repositoryOperation(ioDispatcher) {
        val telemetry = deviceRepository.readTelemetry()
        controllerClient.getActionHistory(telemetry.deviceId, requireToken())
    }

    private suspend fun finishApplicationRemoval(approval: PendingApplicationRemoval, result: ActionExecutionResult) {
        val storedResult = localActions.saveActionResult(approval.actionId, result)
        applicationRepository.syncApplications().getOrThrow()
        sendFinalActionResult(approval.actionId, storedResult, requireToken())
        localActions.removePendingApplicationRemoval(approval.actionId)
        approvalNotificationDataSource.cancel(approval.actionId)
    }

    private suspend fun requireApplicationApproval(actionId: String): PendingApplicationRemoval =
        localActions.getPendingApplicationRemovals().firstOrNull { pending -> pending.actionId == actionId }
            ?: error("Application removal approval is no longer pending")

    private suspend fun finalActionResult(actionId: String): ActionExecutionResult? =
        localActions.getActionResult(actionId)?.takeIf { result -> result.status != "awaiting_approval" }

    private suspend fun sendFinalActionResult(actionId: String, result: ActionExecutionResult, token: String) {
        try {
            controllerClient.sendActionResult(actionId, result, token)
        } catch (error: ControllerApiResponseException) {
            if (error.statusCode != HTTP_CONFLICT) {
                throw error
            }
        }
    }

    private suspend fun recordFileResult(approval: PendingFileRemoval, result: ActionExecutionResult) {
        val actionId = approval.actionId
        val storedResult = localActions.saveActionResult(actionId, result)
        fileRepository.syncFiles().getOrThrow()
        sendFinalActionResult(actionId, storedResult, requireToken())
        localActions.removePendingFileRemoval(actionId)
        approvalNotificationDataSource.cancel(actionId)
    }

    private suspend fun resendCompletedApprovalResults() {
        localActions.getPendingApplicationRemovals().forEach { approval ->
            finalActionResult(approval.actionId)?.let { result ->
                finishApplicationRemoval(approval, result)
            }
        }
        localActions.getPendingFileRemovals().forEach { approval ->
            val result = finalActionResult(approval.actionId)
            if (result != null) {
                recordFileResult(approval, result)
            } else if (approval.platformRequestStarted && approval.mediaUri != null) {
                val absent = try {
                    fileRepository.isMediaAbsent(approval)
                } catch (_: FileNotAllowedException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
                if (absent) {
                    recordFileResult(approval, ActionExecutionResult(true, "File is already absent after the removal request", "completed", "file_already_absent"))
                }
            }
        }
    }

    private suspend fun processAction(action: Action, token: String) {
        val result = localActions.getActionResult(action.id) ?: run {
            val execution = repositoryOperation(ioDispatcher) { actionExecutor.execute(action) }
            val result = mapExecutionResult(execution)
                ?: throw checkNotNull(execution.exceptionOrNull())
            recordApproval(action, result)
            localActions.saveActionResult(action.id, result)
        }
        controllerClient.sendActionResult(action.id, result, token)
    }

    private suspend fun recordApproval(action: Action, result: ActionExecutionResult) {
        if (result.status != "awaiting_approval") {
            return
        }
        when (action.type) {
            "removeApplication" -> {
                val approval = action.applicationId?.let { applicationId ->
                    PendingApplicationRemoval(action.id, applicationId, action.expectedVersionCode)
                } ?: throw IllegalStateException("Application removal target is missing")
                localActions.savePendingApplicationRemoval(approval)
                approvalNotificationDataSource.notify(approval)
            }
            "removeFile" -> {
                val approval = fileRepository.createFileApproval(action)
                localActions.savePendingFileRemoval(approval)
                approvalNotificationDataSource.notifyFile(approval)
            }
        }
    }

    private suspend fun requireToken(): String = checkNotNull(preferences.getToken()) {
        "Device is not paired"
    }

    private suspend fun <T> actionOperation(operation: suspend () -> T): Result<T> = repositoryOperation(ioDispatcher) {
        lifecycleMutex.withLock { operation() }
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
