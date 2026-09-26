package com.tcc.devicehealth.agent

internal class UnsupportedCommandException : Exception("Command is not supported")

internal class CommandExecutor(
    private val sendTelemetry: suspend () -> Unit,
    private val storageDataSource: StorageDataSource,
) {
    suspend fun execute(command: ControllerCommand): CommandExecution = when (command.type) {
        "collectTelemetry" -> {
            sendTelemetry()
            CommandExecution("Telemetry sent")
        }
        "collectStorageSummary" -> storageDataSource.collectSummary()
        else -> throw UnsupportedCommandException()
    }
}

internal fun storedCommandResult(execution: Result<CommandExecution>): StoredCommandResult? {
    val failure = execution.exceptionOrNull()
    if (failure is RetryableControllerException) {
        return null
    }
    return when (failure) {
        null -> execution.getOrThrow().let { commandExecution ->
            StoredCommandResult(
                succeeded = true,
                message = commandExecution.message,
                resultJson = commandExecution.resultJson,
            )
        }
        is UnsupportedCommandException -> StoredCommandResult(
            succeeded = false,
            message = "Command is not supported",
            errorCode = "unsupported_command",
        )
        else -> StoredCommandResult(
            succeeded = false,
            message = "Command execution failed",
            errorCode = "execution_failed",
        )
    }
}
