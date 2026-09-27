package com.tcc.devicehealth.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
    val actions: List<DeviceAction> = emptyList(),
    val pendingApprovals: List<PendingApproval> = emptyList(),
    val approvalRequest: PendingApproval? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
)

class AgentViewModel(
    private val repository: DeviceHealthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = mutableState.asStateFlow()
    private var pollingJob: Job? = null

    init {
        initialize()
    }

    fun updatePairingCode(code: String) {
        mutableState.value = mutableState.value.copy(pairingCode = code.filter(Char::isDigit).take(6))
    }

    fun pair() {
        val code = mutableState.value.pairingCode
        if (code.length != 6) {
            mutableState.value = mutableState.value.copy(message = "Enter the six-digit pairing code")
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            val result = withContext(Dispatchers.IO) { repository.pair(code) }
            if (result.isSuccess) {
                mutableState.value = mutableState.value.copy(isPaired = true, isLoading = false, message = "Device paired")
                sync()
                loadActionHistory()
                loadPendingApprovals()
                startPolling()
            } else {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    message = "Pairing failed. Check the code and try again.",
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(telemetry = repository.readTelemetry())
        }
    }

    fun sync() {
        runOperation("State synchronized") {
            repository.sendTelemetry().fold(
                onSuccess = {
                    repository.syncStorageSummary().fold(
                        onSuccess = { repository.syncAppInventory() },
                        onFailure = { Result.failure(it) },
                    )
                },
                onFailure = { Result.failure(it) },
            )
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

    fun requestApplicationRemoval(approval: PendingApproval) {
        mutableState.value = mutableState.value.copy(approvalRequest = approval, message = null)
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
        if (pollingJob != null) {
            return
        }
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                val result = withContext(Dispatchers.IO) { repository.checkActions() }
                val processedCount = result.getOrNull() ?: 0
                if (processedCount > 0) {
                    loadActionHistory()
                    loadPendingApprovals()
                    mutableState.value = mutableState.value.copy(
                        telemetry = repository.readTelemetry(),
                        message = "$processedCount action(s) completed",
                    )
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
            )
            if (isPaired) {
                loadActionHistory()
                loadPendingApprovals()
                sync()
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

    private fun runOperation(successMessage: String, operation: suspend () -> Result<Unit>) {
        runOperation({ successMessage }, operation)
    }

    private fun <T> runOperation(successMessage: (T) -> String, operation: suspend () -> Result<T>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            val result = withContext(Dispatchers.IO) { operation() }
            mutableState.value = mutableState.value.copy(
                telemetry = repository.readTelemetry(),
                isLoading = false,
                message = result.fold(successMessage) { "Operation failed. Try again." },
            )
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
