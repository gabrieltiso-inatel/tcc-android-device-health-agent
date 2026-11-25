package com.tcc.devicehealth.agent.model

data class Action(
    val id: String,
    val type: String,
    val applicationId: String? = null,
    val expectedVersionCode: Long? = null,
    val fileId: String? = null,
    val expectedRevision: String? = null,
    val origin: String,
    val status: String,
    val requestedAt: String,
    val completedAt: String? = null,
    val resultMessage: String? = null,
)

data class ActionExecutionResult(
    val succeeded: Boolean,
    val message: String,
    val status: String = if (succeeded) "completed" else "failed",
    val errorCode: String? = null,
    val collectedData: CollectedDeviceData? = null,
)

data class PendingApplicationRemoval(
    val actionId: String,
    val applicationId: String,
    val expectedVersionCode: Long?,
)

data class PendingFileRemoval(
    val actionId: String,
    val fileId: String,
    val expectedRevision: String?,
    val name: String? = null,
    val mediaUri: String? = null,
    val platformRequestStarted: Boolean = false,
)

enum class ApplicationRemovalReview {
    ConfirmationRequired,
    Completed,
}

enum class ApplicationRemovalDecision {
    Declined,
    ConfirmationAccepted,
    ConfirmationDismissed,
}
