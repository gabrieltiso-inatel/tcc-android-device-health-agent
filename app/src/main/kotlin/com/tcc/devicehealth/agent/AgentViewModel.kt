package com.tcc.devicehealth.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

data class AgentUiState(
    val telemetry: DeviceTelemetry? = null,
    val isReady: Boolean = false,
    val isPaired: Boolean = false,
    val pairingCode: String = "",
    val controllerAddress: String = "",
    val snapshots: DeviceSnapshots = DeviceSnapshots(),
    val snapshotError: String? = null,
    val actions: List<DeviceAction> = emptyList(),
    val pendingApprovals: List<PendingApproval> = emptyList(),
    val pendingFileApprovals: List<PendingFileApproval> = emptyList(),
    val approvalRequest: PendingApproval? = null,
    val fileApprovalRequest: PendingFileApproval? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
)

class AgentViewModel(
    private val repository: DeviceHealthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = mutableState.asStateFlow()
    private var pollingJob: Job? = null
    private var synchronizationJob: Job? = null
    private var isForeground = false

    fun setForeground(isForeground: Boolean) {
        this.isForeground = isForeground
        if (isForeground) {
            startPolling()
        } else {
            pollingJob?.cancel()
            pollingJob = null
            synchronizationJob?.cancel()
            synchronizationJob = null
        }
    }

    init {
        initialize()
    }

    fun updatePairingCode(code: String) {
        mutableState.value = mutableState.value.copy(pairingCode = code.filter(Char::isDigit).take(6))
    }

    fun updateControllerAddress(address: String) {
        mutableState.value = mutableState.value.copy(controllerAddress = address)
    }

    fun pair() {
        val code = mutableState.value.pairingCode
        if (code.length != 6) {
            mutableState.value = mutableState.value.copy(message = "Enter the six-digit pairing code")
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            val result = withContext(ioDispatcher) {
                repository.configureController(mutableState.value.controllerAddress).fold(
                    onSuccess = { repository.pair(code) },
                    onFailure = { Result.failure(it) },
                )
            }
            if (result.isSuccess) {
                mutableState.value = mutableState.value.copy(isPaired = true, isLoading = false, message = "Device paired")
                loadActionHistory()
                loadPendingApprovals()
                loadPendingFileApprovals()
                startPolling()
            } else {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    message = "Pairing failed. Check the controller address and code.",
                )
            }
        }
    }

    fun executeAction(type: String) {
        executeAction(type, null, null)
    }

    fun removeApplication(app: InstalledApp) {
        executeAction("removeApplication", app, null)
    }

    fun removeFile(file: FileItem) {
        executeAction("removeFile", null, file)
    }

    private fun executeAction(type: String, app: InstalledApp?, file: FileItem?) {
        if (state.value.isLoading) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            val result = withContext(ioDispatcher) {
                repository.executeDeviceAction(type, app?.applicationId, file?.fileId, file?.revision, app?.versionCode)
            }
            val actions = if (result.isSuccess) {
                repository.getActionHistory().getOrDefault(mutableState.value.actions)
            } else {
                mutableState.value.actions
            }
            mutableState.value = mutableState.value.copy(
                telemetry = repository.readTelemetry(),
                actions = actions,
                isLoading = false,
                message = result.fold({ "Action processed. Check pending approvals or history." }, { "Could not finish the request. Check history before trying again." }),
            )
            loadPendingApprovals()
            loadPendingFileApprovals()
            loadSnapshots()
        }
    }

    fun addFileTreeUri(uri: String) {
        viewModelScope.launch {
            val result = repository.addFileTreeUri(uri)
            mutableState.value = mutableState.value.copy(message = result.fold(
                { "Folder added. Update files to include it." },
                { "Could not add the folder. Try again." },
            ))
        }
    }

    fun requestApplicationRemoval(approval: PendingApproval) {
        mutableState.value = mutableState.value.copy(approvalRequest = approval, message = null)
    }

    fun requestFileRemoval(approval: PendingFileApproval) {
        val name = approval.name ?: state.value.snapshots.files?.files?.find { it.fileId == approval.fileId }?.name
        mutableState.value = mutableState.value.copy(fileApprovalRequest = approval.copy(name = name), message = null)
    }

    fun dismissFileRemoval() {
        mutableState.value = mutableState.value.copy(fileApprovalRequest = null)
    }

    fun cancelFileRemoval(approval: PendingFileApproval) {
        completeFileRemoval(approval, false)
    }

    fun cancelApplicationRemoval(approval: PendingApproval) {
        completeApplicationRemoval(approval, false)
    }

    fun handleFileRemovalResult(approved: Boolean) {
        val approval = mutableState.value.fileApprovalRequest ?: return
        completeFileRemoval(approval, approved)
    }

    private fun completeFileRemoval(approval: PendingFileApproval, approved: Boolean) {
        if (state.value.isLoading) return
        mutableState.value = mutableState.value.copy(fileApprovalRequest = null, isLoading = true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                repository.completeFileRemoval(approval.actionId, approved)
            }
            loadActionHistory()
            loadPendingFileApprovals()
            loadSnapshots()
            mutableState.value = mutableState.value.copy(
                telemetry = repository.readTelemetry(),
                isLoading = false,
                message = result.fold({ "File action recorded" }, { "Could not finish file action" }),
            )
        }
    }

    fun handleApplicationRemovalResult(approved: Boolean) {
        val approval = mutableState.value.approvalRequest ?: return
        completeApplicationRemoval(approval, approved)
    }

    private fun completeApplicationRemoval(approval: PendingApproval, approved: Boolean) {
        if (state.value.isLoading) return
        mutableState.value = mutableState.value.copy(approvalRequest = null, isLoading = true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                repository.completeApplicationRemoval(approval.actionId, approved)
            }
            loadActionHistory()
            loadPendingApprovals()
            loadSnapshots()
            mutableState.value = mutableState.value.copy(
                telemetry = repository.readTelemetry(),
                isLoading = false,
                message = result.fold({ "Application action recorded" }, { "Could not finish application action" }),
            )
        }
    }

    private fun startPolling() {
        startAutomaticSync()
        if (!isForeground || !state.value.isPaired || pollingJob != null) {
            return
        }
        pollingJob = viewModelScope.launch {
            val schedule = ForegroundPollingSchedule()
            while (isActive) {
                val result = withContext(ioDispatcher) { repository.checkActions() }
                val processedCount = result.getOrNull() ?: 0
                if (processedCount > 0) {
                    loadActionHistory()
                    loadPendingApprovals()
                    loadPendingFileApprovals()
                    loadSnapshots()
                    mutableState.value = mutableState.value.copy(
                        telemetry = repository.readTelemetry(),
                        message = "$processedCount action(s) processed",
                    )
                }
                delay(schedule.nextDelayMillis(result.isSuccess))
            }
        }
    }

    private fun startAutomaticSync() {
        if (!isForeground || !state.value.isPaired || synchronizationJob != null) return
        synchronizationJob = viewModelScope.launch(ioDispatcher) {
            coroutineScope {
                launch {
                    syncPeriodically(SyncIntervals.telemetryMillis, sync = {
                        val result = repository.sendTelemetry()
                        val telemetry = repository.readTelemetry()
                        withContext(Dispatchers.Main) {
                            mutableState.value = mutableState.value.copy(telemetry = telemetry)
                        }
                        result
                    })
                }
                launch { syncPeriodically(SyncIntervals.storageMillis, repository::syncStorageSummary) }
                launch { syncPeriodically(SyncIntervals.appInventoryMillis, repository::syncAppInventory) }
                launch {
                    syncPeriodically(SyncIntervals.screenRefreshMillis, sync = {
                        withContext(Dispatchers.Main) {
                            loadActionHistory()
                            loadPendingApprovals()
                            loadPendingFileApprovals()
                            loadSnapshots()
                        }
                    })
                }
            }
        }
    }

    private fun initialize() {
        viewModelScope.launch {
            val telemetry = repository.readTelemetry()
            val isPaired = repository.isPaired()
            mutableState.value = mutableState.value.copy(
                telemetry = telemetry,
                isReady = true,
                isPaired = isPaired,
                controllerAddress = repository.getControllerAddress(),
            )
            if (isPaired) {
                loadActionHistory()
                loadPendingApprovals()
                loadPendingFileApprovals()
                startPolling()
            }
        }
    }

    private suspend fun loadActionHistory() {
        repository.getActionHistory().getOrNull()?.let { actions ->
            mutableState.value = mutableState.value.copy(actions = actions)
        }
    }

    private suspend fun loadSnapshots(): Result<Unit> {
        val result = repository.getSnapshots()
        mutableState.value = mutableState.value.copy(
            snapshots = result.getOrDefault(state.value.snapshots),
            snapshotError = if (result.isFailure) "Could not refresh saved readings. Showing the last available data." else null,
        )
        return result.map { Unit }
    }

    private suspend fun loadPendingApprovals() {
        repository.getPendingApprovals().getOrNull()?.let { approvals ->
            mutableState.value = mutableState.value.copy(pendingApprovals = approvals)
        }
    }

    private suspend fun loadPendingFileApprovals() {
        repository.getPendingFileApprovals().getOrNull()?.let { approvals ->
            mutableState.value = mutableState.value.copy(pendingFileApprovals = approvals)
        }
    }

}

class AgentViewModelFactory(
    private val repository: DeviceHealthRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AgentViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
