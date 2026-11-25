package com.tcc.devicehealth.agent.actions

import com.tcc.devicehealth.agent.data.ApplicationRepository
import com.tcc.devicehealth.agent.data.DeviceRepository
import com.tcc.devicehealth.agent.data.FileRepository
import com.tcc.devicehealth.agent.data.android.ApplicationChangedException
import com.tcc.devicehealth.agent.data.android.ApplicationNotAllowedException
import com.tcc.devicehealth.agent.data.android.ApplicationNotFoundException
import com.tcc.devicehealth.agent.data.android.FileChangedException
import com.tcc.devicehealth.agent.data.android.FileNotAllowedException
import com.tcc.devicehealth.agent.data.android.FileNotFoundException
import com.tcc.devicehealth.agent.data.remote.ControllerApiUnavailableException
import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.ActionExecutionResult
import com.tcc.devicehealth.agent.model.ApplicationRemovalDecision
import kotlinx.coroutines.CancellationException

internal class UnsupportedActionException : Exception("Action is not supported")

internal class ActionExecutor(
    private val deviceRepository: DeviceRepository,
    private val applicationRepository: ApplicationRepository,
    private val fileRepository: FileRepository,
) {
    suspend fun execute(action: Action): ActionExecutionResult = when (action.type) {
        "collectTelemetry" -> {
            deviceRepository.sendTelemetry().getOrThrow()
            ActionExecutionResult(succeeded = true, message = "Telemetry sent")
        }
        "collectStorageSummary" -> ActionExecutionResult(succeeded = true, message = "Storage summary collected", collectedData = deviceRepository.collectStorage())
        "collectAppInventory" -> ActionExecutionResult(succeeded = true, message = "App inventory collected", collectedData = applicationRepository.collectApplications())
        "collectFileInventory" -> ActionExecutionResult(succeeded = true, message = "File inventory collected", collectedData = fileRepository.collectFiles())
        "removeApplication" -> {
            applicationRepository.validateApplicationRemoval(action)
            ActionExecutionResult(false, "Application removal requires approval", "awaiting_approval", "approval_required")
        }
        "removeFile" -> {
            fileRepository.validateFileRemoval(action)
            ActionExecutionResult(false, "File removal requires approval", "awaiting_approval", "approval_required")
        }
        else -> throw UnsupportedActionException()
    }
}

internal fun mapExecutionResult(execution: Result<ActionExecutionResult>): ActionExecutionResult? {
    val failure = execution.exceptionOrNull()
    if (failure is CancellationException) throw failure
    if (failure is ControllerApiUnavailableException) {
        return null
    }
    return when (failure) {
        null -> execution.getOrThrow()
        is UnsupportedActionException -> ActionExecutionResult(
            succeeded = false,
            message = "Action is not supported",
            errorCode = "unsupported_action",
        )
        is ApplicationNotFoundException -> ActionExecutionResult(
            succeeded = false,
            message = "Application is not installed",
            errorCode = "application_not_found",
        )
        is ApplicationChangedException -> ActionExecutionResult(
            succeeded = false,
            message = "Application changed since it was selected",
            errorCode = "application_changed",
        )
        is ApplicationNotAllowedException -> ActionExecutionResult(
            succeeded = false,
            message = "Application cannot be removed",
            errorCode = "not_allowed",
        )
        is FileNotFoundException -> ActionExecutionResult(
            succeeded = false,
            message = "File is no longer available",
            errorCode = "file_not_found",
        )
        is FileChangedException -> ActionExecutionResult(
            succeeded = false,
            message = "File changed since it was selected",
            errorCode = "file_changed",
        )
        is FileNotAllowedException -> ActionExecutionResult(
            succeeded = false,
            message = "File cannot be removed",
            errorCode = "file_not_allowed",
        )
        else -> ActionExecutionResult(
            succeeded = false,
            message = "Action execution failed",
            errorCode = "execution_failed",
        )
    }
}

internal fun applicationRemovalReviewResult(isRemoved: Boolean, validationFailure: Throwable?): ActionExecutionResult? = when {
    isRemoved -> ActionExecutionResult(
        succeeded = true,
        message = "Application was already absent",
        errorCode = "already_absent",
    )
    validationFailure != null -> mapExecutionResult(Result.failure(validationFailure))
    else -> null
}

internal fun applicationRemovalDecisionResult(
    decision: ApplicationRemovalDecision,
    isRemoved: Boolean,
): ActionExecutionResult = when {
    decision == ApplicationRemovalDecision.Declined -> declinedApplicationRemoval()
    isRemoved -> ActionExecutionResult(
        succeeded = true,
        message = "Application removed",
        errorCode = "application_removed",
    )
    decision == ApplicationRemovalDecision.ConfirmationAccepted -> ActionExecutionResult(
        succeeded = false,
        message = "Application removal failed",
        errorCode = "execution_failed",
    )
    else -> declinedApplicationRemoval()
}

private fun declinedApplicationRemoval() = ActionExecutionResult(
    succeeded = false,
    message = "Application removal cancelled",
    status = "cancelled",
    errorCode = "approval_declined",
)
