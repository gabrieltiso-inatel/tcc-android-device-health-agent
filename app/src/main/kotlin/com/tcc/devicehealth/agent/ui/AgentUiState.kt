package com.tcc.devicehealth.agent.ui

import com.tcc.devicehealth.agent.data.android.AndroidFileRemovalConfirmation
import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.DeviceTelemetry
import com.tcc.devicehealth.agent.model.DisplayPreferences
import com.tcc.devicehealth.agent.model.LatestDeviceSnapshots
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import com.tcc.devicehealth.agent.model.PendingFileRemoval

data class AgentUiState(
    val telemetry: DeviceTelemetry? = null,
    val isReady: Boolean = false,
    val isPaired: Boolean = false,
    val pairingCode: String = "",
    val controllerAddress: String = "",
    val snapshots: LatestDeviceSnapshots = LatestDeviceSnapshots(),
    val isShowingSavedReadings: Boolean = false,
    val actions: List<Action> = emptyList(),
    val pendingApplicationRemovals: List<PendingApplicationRemoval> = emptyList(),
    val pendingFileRemovals: List<PendingFileRemoval> = emptyList(),
    val applicationConfirmation: PendingApplicationRemoval? = null,
    val fileConfirmation: PendingFileRemoval? = null,
    val platformFileConfirmation: AndroidFileRemovalConfirmation? = null,
    val isLoading: Boolean = false,
    val message: AgentMessage? = null,
    val displayPreferences: DisplayPreferences = DisplayPreferences(),
    val permissions: PermissionStatus = PermissionStatus(),
)

sealed interface AgentMessage {
    data object EnterPairingCode : AgentMessage
    data object Paired : AgentMessage
    data object PairingFailed : AgentMessage
    data object RequestFailed : AgentMessage
    data object MediaAccessGranted : AgentMessage
    data object MediaAccessDenied : AgentMessage
    data class ActionFinished(val type: String) : AgentMessage
    data object FolderAdded : AgentMessage
    data object FolderFailed : AgentMessage
    data object ConfirmInAndroidDialog : AgentMessage
    data object FileActionRecorded : AgentMessage
    data object FileActionFailed : AgentMessage
    data object FileActionWillRetry : AgentMessage
    data object ApplicationActionRecorded : AgentMessage
    data object ApplicationActionFailed : AgentMessage
    data object PreferencesFailed : AgentMessage
    data class ApplicationRemovalFinished(val applicationName: String, val status: String) : AgentMessage
    data class ActionsProcessed(val count: Int) : AgentMessage
}

data class PermissionStatus(
    val notificationsGranted: Boolean = true,
    val mediaGranted: Boolean = false,
)
