package com.tcc.devicehealth.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
            val result = withContext(Dispatchers.IO) {
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

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(telemetry = repository.readTelemetry())
        }
    }

    fun executeAction(type: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            val result = withContext(Dispatchers.IO) { repository.executeDeviceAction(type) }
            val actions = if (result.isSuccess) {
                repository.getActionHistory().getOrDefault(mutableState.value.actions)
            } else {
                mutableState.value.actions
            }
            mutableState.value = mutableState.value.copy(
                telemetry = repository.readTelemetry(),
                actions = actions,
                isLoading = false,
                message = result.fold({ "Action completed" }, { "Action failed. Try again." }),
            )
        }
    }

    fun addFileTreeUri(uri: String) {
        viewModelScope.launch {
            repository.addFileTreeUri(uri)
            mutableState.value = mutableState.value.copy(message = "Folder added. Collect the file inventory to refresh it.")
        }
    }

    fun requestApplicationRemoval(approval: PendingApproval) {
        mutableState.value = mutableState.value.copy(approvalRequest = approval, message = null)
    }

    fun requestFileRemoval(approval: PendingFileApproval) {
        mutableState.value = mutableState.value.copy(fileApprovalRequest = approval, message = null)
    }

    fun handleFileRemovalResult(approved: Boolean) {
        val approval = mutableState.value.fileApprovalRequest ?: return
        mutableState.value = mutableState.value.copy(fileApprovalRequest = null, isLoading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.completeFileRemoval(approval.actionId, approved)
            }
            loadActionHistory()
            loadPendingFileApprovals()
            mutableState.value = mutableState.value.copy(
                telemetry = repository.readTelemetry(),
                isLoading = false,
                message = result.fold({ "File action recorded" }, { "Could not finish file action" }),
            )
        }
    }

    fun handleApplicationRemovalResult(approved: Boolean) {
        val approval = mutableState.value.approvalRequest ?: return
        mutableState.value = mutableState.value.copy(approvalRequest = null, isLoading = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.completeApplicationRemoval(approval.actionId, approved)
            }
            loadActionHistory()
            loadPendingApprovals()
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
                val result = withContext(Dispatchers.IO) { repository.checkActions() }
                val processedCount = result.getOrNull() ?: 0
                if (processedCount > 0) {
                    loadActionHistory()
                    loadPendingApprovals()
                    loadPendingFileApprovals()
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
        synchronizationJob = viewModelScope.launch(Dispatchers.IO) {
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
