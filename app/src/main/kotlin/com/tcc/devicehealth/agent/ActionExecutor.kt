package com.tcc.devicehealth.agent

internal class UnsupportedActionException : Exception("Action is not supported")

internal class ActionExecutor(
    private val sendTelemetry: suspend () -> Unit,
    private val collectStorageSummary: () -> ActionExecution,
    private val collectAppInventory: () -> ActionExecution,
) {
    suspend fun execute(action: DeviceAction): ActionExecution = when (action.type) {
        "collectTelemetry" -> {
            sendTelemetry()
            ActionExecution("Telemetry sent")
        }
        "collectStorageSummary" -> collectStorageSummary()
        "collectAppInventory" -> collectAppInventory()
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
                succeeded = true,
                message = actionExecution.message,
                resultJson = actionExecution.resultJson,
            )
        }
        is UnsupportedActionException -> StoredActionResult(
            succeeded = false,
            message = "Action is not supported",
            errorCode = "unsupported_action",
        )
        else -> StoredActionResult(
            succeeded = false,
            message = "Action execution failed",
            errorCode = "execution_failed",
        )
    }
}
