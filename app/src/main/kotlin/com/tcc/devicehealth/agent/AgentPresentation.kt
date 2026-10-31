package com.tcc.devicehealth.agent

import java.time.Duration
import java.time.Instant
import java.util.Locale

internal const val listPageSize = 20

internal fun String.toDisplayName(): String = when (this) {
    "collectTelemetry" -> "Update device"
    "collectStorageSummary" -> "Update storage"
    "collectAppInventory" -> "Update apps"
    "collectFileInventory" -> "Update files"
    "removeApplication" -> "Remove application"
    "removeFile" -> "Remove file"
    "awaiting_approval" -> "Awaiting approval"
    "device" -> "Agent"
    "controller" -> "Controller"
    else -> replace('_', ' ').replaceFirstChar { it.uppercase() }
}

internal fun formatRelativeTime(value: String): String = runCatching {
    val seconds = Duration.between(Instant.parse(value), Instant.now()).seconds.coerceAtLeast(0)
    when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3_600}h ago"
        seconds < 2_592_000 -> "${seconds / 86_400}d ago"
        else -> "${seconds / 2_592_000}mo ago"
    }
}.getOrDefault(value)

internal fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", size, units[unit])
}

internal fun DeviceAction.isActive(): Boolean = status in listOf("pending", "delivered", "awaiting_approval")

internal fun pendingApprovalCount(state: AgentUiState): Int = state.pendingApprovals.size + state.pendingFileApprovals.size

internal fun removalInProgress(actions: List<DeviceAction>, applicationId: String? = null, fileId: String? = null): Boolean =
    actions.any { action -> action.isActive() && (
        (applicationId != null && action.type == "removeApplication" && action.applicationId == applicationId) ||
            (fileId != null && action.type == "removeFile" && action.fileId == fileId)
        ) }
