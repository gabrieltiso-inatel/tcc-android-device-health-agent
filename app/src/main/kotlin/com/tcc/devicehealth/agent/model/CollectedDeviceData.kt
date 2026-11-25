package com.tcc.devicehealth.agent.model

sealed interface CollectedDeviceData

data class InstalledApp(
    val applicationId: String,
    val name: String,
    val canRemove: Boolean,
    val versionName: String,
    val versionCode: Long,
    val installedAt: String = "",
    val updatedAt: String = "",
)

data class ApplicationInventory(val capturedAt: String, val apps: List<InstalledApp>) : CollectedDeviceData

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
) : CollectedDeviceData

data class StorageSummary(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val capturedAt: String,
) : CollectedDeviceData
