package com.tcc.devicehealth.agent

enum class ApplicationRemovalReview {
    ConfirmationRequired,
    Completed,
}

enum class ApplicationRemovalDecision {
    Declined,
    ConfirmationAccepted,
    ConfirmationDismissed,
}

internal fun applicationRemovalReviewResult(isRemoved: Boolean, validationFailure: Throwable?): StoredActionResult? = when {
    isRemoved -> StoredActionResult(
        succeeded = true,
        message = "Application was already absent",
        errorCode = "already_absent",
    )
    validationFailure != null -> storedActionResult(Result.failure(validationFailure))
    else -> null
}

internal fun applicationRemovalDecisionResult(
    decision: ApplicationRemovalDecision,
    isRemoved: Boolean,
): StoredActionResult = when {
    decision == ApplicationRemovalDecision.Declined -> declinedApplicationRemoval()
    isRemoved -> StoredActionResult(
        succeeded = true,
        message = "Application removed",
        errorCode = "application_removed",
    )
    decision == ApplicationRemovalDecision.ConfirmationAccepted -> StoredActionResult(
        succeeded = false,
        message = "Application removal failed",
        errorCode = "execution_failed",
    )
    else -> declinedApplicationRemoval()
}

private fun declinedApplicationRemoval() = StoredActionResult(
    succeeded = false,
    message = "Application removal cancelled",
    status = "cancelled",
    errorCode = "approval_declined",
)
