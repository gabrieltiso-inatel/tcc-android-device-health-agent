package com.tcc.devicehealth.agent

import androidx.annotation.StringRes
import java.time.Duration
import java.time.Instant
import java.util.Locale

internal const val listPageSize = 20
private const val lowBatteryPercentage = 20
private const val almostFullStorageFraction = 0.9f

sealed interface HealthVerdict {
    data object Healthy : HealthVerdict
    data class ApprovalsWaiting(val count: Int) : HealthVerdict
    data object LowBattery : HealthVerdict
    data object StorageAlmostFull : HealthVerdict
    data object SavedReadings : HealthVerdict
}

internal fun healthVerdict(state: AgentUiState): HealthVerdict {
    val telemetry = state.telemetry
    val storage = state.snapshots.storage
    val approvals = pendingApprovalCount(state)
    return when {
        approvals > 0 -> HealthVerdict.ApprovalsWaiting(approvals)
        telemetry != null && telemetry.batteryPercentage <= lowBatteryPercentage && !telemetry.isCharging -> HealthVerdict.LowBattery
        storage != null && storageUsedFraction(storage) >= almostFullStorageFraction -> HealthVerdict.StorageAlmostFull
        state.isShowingSavedReadings -> HealthVerdict.SavedReadings
        else -> HealthVerdict.Healthy
    }
}

internal fun storageUsedFraction(storage: StorageSummary): Float =
    if (storage.totalBytes > 0) (storage.usedBytes.toFloat() / storage.totalBytes).coerceIn(0f, 1f) else 0f

sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class MinutesAgo(val value: Int) : RelativeTime
    data class HoursAgo(val value: Int) : RelativeTime
    data class DaysAgo(val value: Int) : RelativeTime
}

internal fun relativeTime(value: String, now: Instant = Instant.now()): RelativeTime? = runCatching {
    val seconds = Duration.between(Instant.parse(value), now).seconds.coerceAtLeast(0)
    when {
        seconds < 60 -> RelativeTime.JustNow
        seconds < 3_600 -> RelativeTime.MinutesAgo((seconds / 60).toInt())
        seconds < 86_400 -> RelativeTime.HoursAgo((seconds / 3_600).toInt())
        else -> RelativeTime.DaysAgo((seconds / 86_400).toInt())
    }
}.getOrNull()

internal fun formatBytes(bytes: Long, locale: Locale = Locale.US): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(locale, "%.1f %s", size, units[unit])
}

@StringRes
internal fun actionLabel(type: String): Int = when (type) {
    "collectTelemetry" -> R.string.action_collect_telemetry
    "collectStorageSummary" -> R.string.action_collect_storage
    "collectAppInventory" -> R.string.action_collect_apps
    "collectFileInventory" -> R.string.action_collect_files
    "removeApplication" -> R.string.action_remove_application
    "removeFile" -> R.string.action_remove_file
    else -> R.string.action_other
}

@StringRes
internal fun emptyListMessage(isWaiting: Boolean, query: String, @StringRes waiting: Int, @StringRes empty: Int): Int = when {
    isWaiting -> waiting
    query.isNotBlank() -> R.string.search_no_results
    else -> empty
}

@StringRes
internal fun actionFinishedMessage(type: String): Int = when (type) {
    "collectTelemetry" -> R.string.message_battery_updated
    "collectStorageSummary" -> R.string.message_storage_updated
    "collectAppInventory" -> R.string.message_apps_updated
    "collectFileInventory" -> R.string.message_files_updated
    "removeApplication", "removeFile" -> R.string.message_removal_requested
    else -> R.string.message_request_processed
}

@StringRes
internal fun statusLabel(status: String): Int = when (status) {
    "pending" -> R.string.status_pending
    "delivered" -> R.string.status_delivered
    "awaiting_approval" -> R.string.status_awaiting_approval
    "completed" -> R.string.status_completed
    "cancelled" -> R.string.status_cancelled
    "failed" -> R.string.status_failed
    "expired" -> R.string.status_expired
    else -> R.string.status_unknown
}

@StringRes
internal fun originLabel(origin: String): Int =
    if (origin == "device") R.string.origin_device else R.string.origin_controller

@StringRes
internal fun resultLabel(message: String): Int? = when (message) {
    "Application removed" -> R.string.result_application_removed
    "Application was already absent" -> R.string.result_application_absent
    "Application removal cancelled" -> R.string.result_application_cancelled
    "Application removal failed" -> R.string.result_application_failed
    "Application is no longer installed" -> R.string.result_application_not_found
    "Application changed since it was selected" -> R.string.result_application_changed
    "Application cannot be removed" -> R.string.result_application_not_allowed
    "File removed" -> R.string.result_file_removed
    "File removal cancelled" -> R.string.result_file_cancelled
    "File is no longer available" -> R.string.result_file_not_found
    "File changed since it was selected" -> R.string.result_file_changed
    "File cannot be removed" -> R.string.result_file_not_allowed
    "File access is no longer authorized" -> R.string.result_file_access_revoked
    "Application is not installed" -> R.string.result_application_not_found
    "File removal failed" -> R.string.result_file_failed
    "File is already absent after the removal request" -> R.string.result_file_removed
    "Could not open the system confirmation" -> R.string.result_confirmation_failed
    "Application removal requires approval", "File removal requires approval" -> R.string.result_requires_approval
    "Telemetry sent" -> R.string.result_telemetry_sent
    "Storage summary collected", "App inventory collected", "File inventory collected" -> R.string.result_collected
    "Action is not supported" -> R.string.result_not_supported
    "Action execution failed" -> R.string.result_execution_failed
    "Action execution timed out", "Action delivery timed out" -> R.string.result_timed_out
    else -> null
}

@StringRes
internal fun fileKindLabel(kind: String): Int = when (kind) {
    "image" -> R.string.file_kind_image
    "video" -> R.string.file_kind_video
    "audio" -> R.string.file_kind_audio
    "document" -> R.string.file_kind_document
    else -> R.string.file_kind_other
}

@StringRes
internal fun authorizationLabel(authorization: String): Int = when (authorization) {
    "available" -> R.string.authorization_available
    "requires_consent" -> R.string.authorization_requires_consent
    "revoked", "unavailable" -> R.string.authorization_unavailable
    else -> R.string.authorization_other
}

internal fun DeviceAction.isActive(): Boolean = status in listOf("pending", "delivered", "awaiting_approval")

internal fun pendingApprovalCount(state: AgentUiState): Int = state.pendingApprovals.size + state.pendingFileApprovals.size

internal fun removalInProgress(actions: List<DeviceAction>, applicationId: String? = null, fileId: String? = null): Boolean =
    actions.any { action -> action.isActive() && (
        (applicationId != null && action.type == "removeApplication" && action.applicationId == applicationId) ||
            (fileId != null && action.type == "removeFile" && action.fileId == fileId)
        ) }

internal fun actionTargetName(action: DeviceAction, snapshots: DeviceSnapshots): String? = when {
    action.applicationId != null -> snapshots.apps?.apps?.find { it.applicationId == action.applicationId }?.name ?: action.applicationId
    action.fileId != null -> snapshots.files?.files?.find { it.fileId == action.fileId }?.name
    else -> null
}
