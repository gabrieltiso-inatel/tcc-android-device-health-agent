package com.tcc.devicehealth.agent

data class DeviceTelemetry(
    val deviceId: String,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val agentVersion: String,
    val capabilities: List<String>,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val capturedAt: String,
)

data class DeviceAction(
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

data class ActionExecution(
    val message: String,
    val resultJson: String? = null,
    val approvalRequired: Boolean = false,
)

data class PendingApproval(
    val actionId: String,
    val applicationId: String,
    val expectedVersionCode: Long?,
)

data class PendingFileApproval(
    val actionId: String,
    val fileId: String,
    val expectedRevision: String?,
)

data class FileSource(
    val sourceId: String,
    val label: String,
    val kind: String,
    val authorization: String,
    val itemCount: Int? = null,
)

data class FileItem(
    val fileId: String,
    val sourceId: String,
    val name: String,
    val kind: String,
    val mimeType: String? = null,
    val sizeBytes: Long,
    val modifiedAt: String? = null,
    val displayPath: String? = null,
    val canRemove: Boolean,
    val revision: String,
)

data class FileInventory(
    val capturedAt: String,
    val sources: List<FileSource>,
    val files: List<FileItem>,
)
