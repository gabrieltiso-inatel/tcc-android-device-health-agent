package com.tcc.devicehealth.agent

internal class UnsupportedActionException : Exception("Action is not supported")

internal class ActionExecutor(
    private val sendTelemetry: suspend () -> Unit,
    private val collectStorageSummary: () -> ActionExecution,
    private val collectAppInventory: () -> ActionExecution,
    private val prepareApplicationRemoval: (DeviceAction) -> ActionExecution = { throw UnsupportedActionException() },
) {
    suspend fun execute(action: DeviceAction): ActionExecution = when (action.type) {
        "collectTelemetry" -> {
            sendTelemetry()
            ActionExecution("Telemetry sent")
        }
        "collectStorageSummary" -> collectStorageSummary()
        "collectAppInventory" -> collectAppInventory()
        "removeApplication" -> prepareApplicationRemoval(action)
        else -> throw UnsupportedActionException()
    }
}

internal fun storedActionResult(execution: Result<ActionExecution>): StoredActionResult? {
    val failure = execution.exceptionOrNull()
    if (failure is RetryableControllerException) {
        return null
    }
    return when (failure) {
        null -> execution.getOrThrow().let { actionExecution ->
            StoredActionResult(
                succeeded = !actionExecution.approvalRequired,
                message = actionExecution.message,
                status = if (actionExecution.approvalRequired) "awaiting_approval" else "completed",
                errorCode = if (actionExecution.approvalRequired) "approval_required" else null,
                resultJson = actionExecution.resultJson,
            )
        }
        is UnsupportedActionException -> StoredActionResult(
            succeeded = false,
            message = "Action is not supported",
            errorCode = "unsupported_action",
        )
        is ApplicationNotFoundException -> StoredActionResult(
            succeeded = false,
            message = "Application is not installed",
            errorCode = "application_not_found",
        )
        is ApplicationChangedException -> StoredActionResult(
            succeeded = false,
            message = "Application changed since it was selected",
            errorCode = "application_changed",
        )
        is ApplicationNotAllowedException -> StoredActionResult(
            succeeded = false,
            message = "Application cannot be removed",
            errorCode = "not_allowed",
        )
        else -> StoredActionResult(
            succeeded = false,
            message = "Action execution failed",
            errorCode = "execution_failed",
        )
    }
}
