package com.tcc.devicehealth.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

data class AgentUiState(
    val telemetry: DeviceTelemetry? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
)

class AgentViewModel(
    private val repository: DeviceHealthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = mutableState.asStateFlow()

    init {
        sync()
        startPolling()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(telemetry = repository.readTelemetry())
        }
    }

    fun sync() {
        runOperation("Telemetry synchronized") { repository.sendTelemetry() }
    }

    fun checkCommands() {
        runOperation(
            successMessage = { processedCount ->
                if (processedCount == 0) "No pending commands" else "$processedCount command(s) completed"
            },
            operation = { repository.checkCommands() },
        )
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                val result = withContext(Dispatchers.IO) { repository.checkCommands() }
                val processedCount = result.getOrNull() ?: 0
                if (processedCount > 0) {
                    mutableState.value = mutableState.value.copy(
                        telemetry = repository.readTelemetry(),
                        message = "$processedCount command(s) completed",
                    )
                }
            }
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
                message = result.fold(successMessage) { it.message ?: "Operation failed" },
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
