package com.tcc.devicehealth.agent

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
