package com.tcc.devicehealth.agent

import android.content.Context

interface DeviceHealthRepository {
    suspend fun readTelemetry(): DeviceTelemetry
    suspend fun isPaired(): Boolean
    suspend fun pair(code: String): Result<Unit>
    suspend fun getControllerAddress(): String
    suspend fun configureController(address: String): Result<Unit>
    suspend fun sendTelemetry(): Result<Unit>
    suspend fun syncStorageSummary(): Result<Unit>
    suspend fun syncAppInventory(): Result<Unit>
    suspend fun syncFileInventory(): Result<Unit>
    suspend fun addFileTreeUri(uri: String): Result<Unit>
    suspend fun checkActions(): Result<Int>
    suspend fun executeDeviceAction(
        type: String,
        applicationId: String? = null,
        fileId: String? = null,
        expectedRevision: String? = null,
        expectedVersionCode: Long? = null,
    ): Result<Unit>
    suspend fun getPendingApprovals(): Result<List<PendingApproval>>
    suspend fun getPendingFileApprovals(): Result<List<PendingFileApproval>>
    suspend fun reviewApplicationRemoval(actionId: String): Result<ApplicationRemovalReview>
    suspend fun completeApplicationRemoval(actionId: String, decision: ApplicationRemovalDecision): Result<Unit>
    suspend fun completeFileRemoval(actionId: String, approved: Boolean): Result<FileRemovalRequest?>
    suspend fun completePlatformFileRemoval(actionId: String, approved: Boolean, launchFailed: Boolean = false): Result<Unit>
    suspend fun getActionHistory(): Result<List<DeviceAction>>
    suspend fun getSnapshots(): Result<DeviceSnapshots>
    suspend fun getDisplayPreferences(): DisplayPreferences
    suspend fun saveDisplayPreferences(displayPreferences: DisplayPreferences): Result<Unit>
}

class AndroidDeviceHealthRepository(
    context: Context,
    private val controllerBaseUrl: String,
    private val preferences: AgentPreferences = AgentPreferences(context.applicationContext),
) : DeviceHealthRepository {
    private val telemetrySource = DeviceTelemetrySource(context.applicationContext, preferences)
    private val controllerClient = ControllerClient { getControllerAddress() }
    private val storageDataSource = StorageDataSource()
    private val appInventoryDataSource = AppInventoryDataSource(
        packageManager = context.applicationContext.packageManager,
        agentPackageName = context.applicationContext.packageName,
    )
    private val fileInventoryDataSource = FileInventoryDataSource(context.applicationContext, preferences)
    private val fileRemovalDataSource = FileRemovalDataSource(context.applicationContext, preferences)
    private val applicationRemovalDataSource = ApplicationRemovalDataSource(
        packageManager = context.applicationContext.packageManager,
        agentPackageName = context.applicationContext.packageName,
    )
    private val approvalNotificationDataSource = ApprovalNotificationDataSource(context.applicationContext)
    private val actionExecutor = ActionExecutor(
        sendTelemetry = { sendTelemetry().getOrThrow() },
        collectStorageSummary = storageDataSource::collectSummary,
        collectAppInventory = appInventoryDataSource::collect,
        collectFileInventory = fileInventoryDataSource::collect,
        prepareApplicationRemoval = applicationRemovalDataSource::prepare,
        prepareFileRemoval = fileRemovalDataSource::prepare,
    )

    override suspend fun readTelemetry(): DeviceTelemetry = telemetrySource.read()

    override suspend fun isPaired(): Boolean = preferences.getToken() != null

    override suspend fun getDisplayPreferences(): DisplayPreferences = preferences.getDisplayPreferences()

    override suspend fun saveDisplayPreferences(displayPreferences: DisplayPreferences): Result<Unit> = runCatching {
        preferences.saveDisplayPreferences(displayPreferences)
    }

    override suspend fun getControllerAddress(): String = preferences.getControllerAddress() ?: controllerBaseUrl

    override suspend fun configureController(address: String): Result<Unit> = runCatching {
        val normalized = normalizeControllerAddress(address)
        check(!isPaired() || normalized == getControllerAddress())
        preferences.saveControllerAddress(normalized)
    }

    override suspend fun pair(code: String): Result<Unit> = runCatching {
        val token = try {
            controllerClient.pair(code, readTelemetry())
        } catch (error: ControllerResponseException) {
            if (error.statusCode == 400) {
                throw IllegalStateException("Pairing code is invalid or expired")
            }
            throw error
        }
        preferences.saveToken(token)
    }

    override suspend fun sendTelemetry(): Result<Unit> = runCatching {
        controllerClient.sendTelemetry(readTelemetry(), requireToken())
    }

    override suspend fun syncStorageSummary(): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val summary = storageDataSource.collectSummary().resultJson
            ?: error("Storage summary did not produce a result")
        controllerClient.sendStorageSummary(telemetry.deviceId, summary, requireToken())
    }

    override suspend fun syncAppInventory(): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val inventory = appInventoryDataSource.collect().resultJson
            ?: error("App inventory did not produce a result")
        controllerClient.sendAppInventory(telemetry.deviceId, inventory, requireToken())
    }

    override suspend fun syncFileInventory(): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val inventory = fileInventoryDataSource.collect().resultJson
            ?: error("File inventory did not produce a result")
        controllerClient.sendFileInventory(telemetry.deviceId, inventory, requireToken())
    }

    override suspend fun addFileTreeUri(uri: String): Result<Unit> = runCatching {
        preferences.addFileTreeUri(uri)
    }

    override suspend fun checkActions(): Result<Int> = runCatching {
        val telemetry = readTelemetry()
        val token = requireToken()
        resendCompletedApprovalResults(token)
        val actions = controllerClient.getPendingActions(telemetry.deviceId, token)
        actions.forEach { action -> executeAction(action, token) }
        actions.size
    }

    override suspend fun executeDeviceAction(type: String, applicationId: String?, fileId: String?, expectedRevision: String?, expectedVersionCode: Long?): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val token = requireToken()
        val action = controllerClient.createDeviceAction(telemetry.deviceId, type, token, applicationId, expectedVersionCode, fileId, expectedRevision)
        executeAction(action, token)
    }

    override suspend fun getPendingApprovals(): Result<List<PendingApproval>> = runCatching {
        preferences.getPendingApprovals().filter { approval -> finalActionResult(approval.actionId) == null }
    }

    override suspend fun getPendingFileApprovals(): Result<List<PendingFileApproval>> = runCatching {
        preferences.getPendingFileApprovals().filter { approval -> finalActionResult(approval.actionId) == null }
    }

    override suspend fun reviewApplicationRemoval(actionId: String): Result<ApplicationRemovalReview> = runCatching {
        val approval = requirePendingApproval(actionId)
        val result = finalActionResult(actionId) ?: applicationRemovalReviewResult(
            isRemoved = applicationRemovalDataSource.isRemoved(approval),
            validationFailure = runCatching { applicationRemovalDataSource.validate(approval) }.exceptionOrNull(),
        )
        if (result == null) {
            ApplicationRemovalReview.ConfirmationRequired
        } else {
            finishApplicationRemoval(approval, result)
            ApplicationRemovalReview.Completed
        }
    }

    override suspend fun completeApplicationRemoval(actionId: String, decision: ApplicationRemovalDecision): Result<Unit> = runCatching {
        val approval = requirePendingApproval(actionId)
        val result = finalActionResult(actionId) ?: applicationRemovalDecisionResult(
            decision = decision,
            isRemoved = applicationRemovalDataSource.isRemoved(approval),
        )
        finishApplicationRemoval(approval, result)
    }

    private suspend fun finishApplicationRemoval(approval: PendingApproval, result: StoredActionResult) {
        preferences.saveActionResult(approval.actionId, result)
        sendFinalActionResult(approval.actionId, result, requireToken())
        preferences.removePendingApproval(approval.actionId)
        approvalNotificationDataSource.cancel(approval.actionId)
        syncAppInventory().getOrThrow()
    }

    private suspend fun requirePendingApproval(actionId: String): PendingApproval =
        preferences.getPendingApprovals().firstOrNull { pending -> pending.actionId == actionId }
            ?: error("Application removal approval is no longer pending")

    private suspend fun finalActionResult(actionId: String): StoredActionResult? =
        preferences.getActionResult(actionId)?.takeIf { result -> result.status != "awaiting_approval" }

    private suspend fun sendFinalActionResult(actionId: String, result: StoredActionResult, token: String) {
        try {
            controllerClient.sendActionResult(actionId, result, token)
        } catch (error: ControllerResponseException) {
            if (error.statusCode != HTTP_CONFLICT) {
                throw error
            }
        }
    }

    override suspend fun completeFileRemoval(actionId: String, approved: Boolean): Result<FileRemovalRequest?> = runCatching {
        val approval = preferences.getPendingFileApprovals().firstOrNull { pending -> pending.actionId == actionId }
            ?: error("File removal approval is no longer pending")
        val previous = finalActionResult(actionId)
        val result = previous ?: if (!approved) {
            StoredActionResult(false, "File removal cancelled", "cancelled", "approval_declined")
        } else {
            try {
                if (approval.mediaUri != null) {
                    preferences.savePendingFileApproval(approval.copy(platformRequestStarted = true))
                }
                val intentSender = fileRemovalDataSource.remove(approval)
                if (intentSender != null) {
                    return@runCatching FileRemovalRequest(actionId, intentSender)
                }
                StoredActionResult(true, "File removed", "completed", "file_removed")
            } catch (error: FileNotFoundException) {
                StoredActionResult(false, "File is no longer available", "failed", "file_not_found")
            } catch (error: FileChangedException) {
                StoredActionResult(false, "File changed since it was selected", "failed", "file_changed")
            } catch (error: FileNotAllowedException) {
                StoredActionResult(false, "File cannot be removed", "failed", "file_not_allowed")
            } catch (error: SecurityException) {
                StoredActionResult(false, "File access is no longer authorized", "failed", "file_not_allowed")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                StoredActionResult(false, "File removal failed", "failed", "execution_failed")
            }
        }
        recordFileResult(approval, result)
        null
    }

    override suspend fun completePlatformFileRemoval(actionId: String, approved: Boolean, launchFailed: Boolean): Result<Unit> = runCatching {
        val approval = preferences.getPendingFileApprovals().firstOrNull { it.actionId == actionId }
            ?: return@runCatching
        check(approval.platformRequestStarted && approval.mediaUri != null)
        val previous = finalActionResult(actionId)
        val result = previous ?: if (launchFailed) {
            StoredActionResult(false, "Could not open the system confirmation", "failed", "execution_failed")
        } else if (!approved) {
            StoredActionResult(false, "File removal cancelled", "cancelled", "approval_declined")
        } else {
            val execution = runCatching {
                fileRemovalDataSource.finishMediaRemoval(approval)
                ActionExecution("File removed")
            }
            when (val error = execution.exceptionOrNull()) {
                null -> StoredActionResult(true, "File removed", "completed", "file_removed")
                is SecurityException -> StoredActionResult(false, "File access is no longer authorized", "failed", "file_not_allowed")
                else -> storedActionResult(Result.failure(error)) ?: throw error
            }
        }
        recordFileResult(approval, result)
    }

    private suspend fun recordFileResult(approval: PendingFileApproval, result: StoredActionResult) {
        val actionId = approval.actionId
        preferences.saveActionResult(actionId, result)
        syncFileInventory().getOrThrow()
        sendFinalActionResult(actionId, result, requireToken())
        preferences.removePendingFileApproval(actionId)
        approvalNotificationDataSource.cancel(actionId)
    }

    private suspend fun resendCompletedApprovalResults(token: String) {
        preferences.getPendingApprovals().forEach { approval ->
            finalActionResult(approval.actionId)?.let { result ->
                sendFinalActionResult(approval.actionId, result, token)
                preferences.removePendingApproval(approval.actionId)
                approvalNotificationDataSource.cancel(approval.actionId)
            }
        }
        preferences.getPendingFileApprovals().forEach { approval ->
            val result = finalActionResult(approval.actionId)
            if (result != null) {
                recordFileResult(approval, result)
            } else if (approval.platformRequestStarted && approval.mediaUri != null) {
                val absent = try {
                    fileRemovalDataSource.isMediaAbsent(approval)
                } catch (_: FileNotAllowedException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
                if (absent) {
                    recordFileResult(approval, StoredActionResult(true, "File is already absent after the removal request", "completed", "file_already_absent"))
                }
            }
        }
    }

    override suspend fun getActionHistory(): Result<List<DeviceAction>> = runCatching {
        val telemetry = readTelemetry()
        controllerClient.getActionHistory(telemetry.deviceId, requireToken())
    }

    override suspend fun getSnapshots(): Result<DeviceSnapshots> = runCatching {
        controllerClient.getSnapshots(preferences.getDeviceId(), requireToken())
    }

    private suspend fun executeAction(action: DeviceAction, token: String) {
        val result = preferences.getActionResult(action.id) ?: run {
            val execution = runCatching { actionExecutor.execute(action) }
            val failure = execution.exceptionOrNull()
            if (failure != null) {
                val storedResult = storedActionResult(execution)
                if (storedResult != null) {
                    savePendingApproval(action, storedResult)
                    storedResult.also { preferences.saveActionResult(action.id, it) }
                } else {
                    throw failure
                }
            } else {
                val storedResult = storedActionResult(execution)
                    ?: error("Action execution did not produce a result")
                savePendingApproval(action, storedResult)
                storedResult.also { preferences.saveActionResult(action.id, it) }
            }
        }
        controllerClient.sendActionResult(action.id, result, token)
    }

    private suspend fun savePendingApproval(action: DeviceAction, result: StoredActionResult) {
        if (result.status != "awaiting_approval") {
            return
        }
        when (action.type) {
            "removeApplication" -> {
                val approval = action.applicationId?.let { applicationId ->
                    PendingApproval(action.id, applicationId, action.expectedVersionCode)
                } ?: throw IllegalStateException("Application removal target is missing")
                preferences.savePendingApproval(approval)
                approvalNotificationDataSource.notify(approval)
            }
            "removeFile" -> {
                val approval = fileRemovalDataSource.createApproval(action)
                preferences.savePendingFileApproval(approval)
                approvalNotificationDataSource.notifyFile(approval)
            }
        }
    }

    private suspend fun requireToken(): String = checkNotNull(preferences.getToken()) {
        "Device is not paired"
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
