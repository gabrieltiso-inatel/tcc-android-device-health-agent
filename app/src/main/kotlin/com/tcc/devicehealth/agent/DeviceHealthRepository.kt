package com.tcc.devicehealth.agent

import android.content.Context

interface DeviceHealthRepository {
    suspend fun readTelemetry(): DeviceTelemetry
    suspend fun isPaired(): Boolean
    suspend fun pair(code: String): Result<Unit>
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
    ): Result<Unit>
    suspend fun getPendingApprovals(): Result<List<PendingApproval>>
    suspend fun getPendingFileApprovals(): Result<List<PendingFileApproval>>
    suspend fun completeApplicationRemoval(actionId: String, approved: Boolean): Result<Unit>
    suspend fun completeFileRemoval(actionId: String, approved: Boolean): Result<Unit>
    suspend fun getActionHistory(): Result<List<DeviceAction>>
}

class AndroidDeviceHealthRepository(
    context: Context,
    controllerBaseUrl: String,
    private val preferences: AgentPreferences = AgentPreferences(context.applicationContext),
) : DeviceHealthRepository {
    private val telemetrySource = DeviceTelemetrySource(context.applicationContext, preferences)
    private val controllerClient = ControllerClient(controllerBaseUrl)
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

    override suspend fun executeDeviceAction(type: String, applicationId: String?, fileId: String?, expectedRevision: String?): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val token = requireToken()
        val action = controllerClient.createDeviceAction(telemetry.deviceId, type, token, applicationId, null, fileId, expectedRevision)
        executeAction(action, token)
    }

    override suspend fun getPendingApprovals(): Result<List<PendingApproval>> = runCatching {
        preferences.getPendingApprovals()
    }

    override suspend fun getPendingFileApprovals(): Result<List<PendingFileApproval>> = runCatching {
        preferences.getPendingFileApprovals()
    }

    override suspend fun completeApplicationRemoval(actionId: String, approved: Boolean): Result<Unit> = runCatching {
        val approval = preferences.getPendingApprovals().firstOrNull { pending -> pending.actionId == actionId }
            ?: error("Application removal approval is no longer pending")
        val result = if (!approved) {
            StoredActionResult(
                succeeded = false,
                status = "cancelled",
                message = "Application removal cancelled",
                errorCode = "approval_declined",
            )
        } else if (applicationRemovalDataSource.isRemoved(approval)) {
            StoredActionResult(
                succeeded = true,
                status = "completed",
                message = "Application was already absent",
                errorCode = "already_absent",
            )
        } else {
            try {
                applicationRemovalDataSource.validate(approval)
                if (applicationRemovalDataSource.isRemoved(approval)) {
                    StoredActionResult(
                        succeeded = true,
                        status = "completed",
                        message = "Application removed",
                        errorCode = "application_removed",
                    )
                } else {
                    StoredActionResult(
                        succeeded = false,
                        status = "failed",
                        message = "Application removal failed",
                        errorCode = "execution_failed",
                    )
                }
            } catch (error: ApplicationNotFoundException) {
                StoredActionResult(false, "Application is no longer installed", "failed", "application_not_found")
            } catch (error: ApplicationChangedException) {
                StoredActionResult(false, "Application changed since it was selected", "failed", "application_changed")
            } catch (error: ApplicationNotAllowedException) {
                StoredActionResult(false, "Application cannot be removed", "failed", "not_allowed")
            }
        }
        preferences.saveActionResult(actionId, result)
        controllerClient.sendActionResult(actionId, result, requireToken())
        preferences.removePendingApproval(actionId)
        approvalNotificationDataSource.cancel(actionId)
        syncAppInventory().getOrThrow()
    }

    override suspend fun completeFileRemoval(actionId: String, approved: Boolean): Result<Unit> = runCatching {
        val approval = preferences.getPendingFileApprovals().firstOrNull { pending -> pending.actionId == actionId }
            ?: error("File removal approval is no longer pending")
        val result = if (!approved) {
            StoredActionResult(false, "File removal cancelled", "cancelled", "approval_declined")
        } else {
            try {
                fileRemovalDataSource.remove(approval)
                StoredActionResult(true, "File removed", "completed", "file_removed")
            } catch (error: FileNotFoundException) {
                StoredActionResult(false, "File is no longer available", "failed", "file_not_found")
            } catch (error: FileChangedException) {
                StoredActionResult(false, "File changed since it was selected", "failed", "file_changed")
            } catch (error: FileNotAllowedException) {
                StoredActionResult(false, "File cannot be removed", "failed", "file_not_allowed")
            }
        }
        preferences.saveActionResult(actionId, result)
        controllerClient.sendActionResult(actionId, result, requireToken())
        preferences.removePendingFileApproval(actionId)
        approvalNotificationDataSource.cancel(actionId)
        syncFileInventory().getOrThrow()
    }

    private suspend fun resendCompletedApprovalResults(token: String) {
        preferences.getPendingApprovals().forEach { approval ->
            val result = preferences.getActionResult(approval.actionId)
            if (result != null && result.status != "awaiting_approval") {
                controllerClient.sendActionResult(approval.actionId, result, token)
                preferences.removePendingApproval(approval.actionId)
                approvalNotificationDataSource.cancel(approval.actionId)
            }
        }
        preferences.getPendingFileApprovals().forEach { approval ->
            val result = preferences.getActionResult(approval.actionId)
            if (result != null && result.status != "awaiting_approval") {
                controllerClient.sendActionResult(approval.actionId, result, token)
                preferences.removePendingFileApproval(approval.actionId)
                approvalNotificationDataSource.cancel(approval.actionId)
            }
        }
    }

    override suspend fun getActionHistory(): Result<List<DeviceAction>> = runCatching {
        val telemetry = readTelemetry()
        controllerClient.getActionHistory(telemetry.deviceId, requireToken())
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
                val approval = action.fileId?.let { fileId ->
                    PendingFileApproval(action.id, fileId, action.expectedRevision)
                } ?: throw IllegalStateException("File removal target is missing")
                preferences.savePendingFileApproval(approval)
                approvalNotificationDataSource.notifyFile(approval)
            }
        }
    }

    private suspend fun requireToken(): String = checkNotNull(preferences.getToken()) {
        "Device is not paired"
    }
}
