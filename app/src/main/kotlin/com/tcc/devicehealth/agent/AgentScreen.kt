package com.tcc.devicehealth.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AgentScreen(
    state: AgentUiState,
    onPairingCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    onExecuteAction: (String) -> Unit,
    onRequestApplicationRemoval: (PendingApproval) -> Unit,
    onCancelApplicationRemoval: (PendingApproval) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Device Health Agent", style = MaterialTheme.typography.headlineMedium)
        when {
            !state.isReady -> Text("Loading...")
            state.isPaired -> {
                DeviceTelemetryCard(state.telemetry)
                PendingApprovalsCard(state.pendingApprovals, onRequestApplicationRemoval, onCancelApplicationRemoval)
                DeviceActionsCard(state.isLoading, onExecuteAction)
                ActionHistoryCard(state.actions)
            }
            else -> PairingCard(state.pairingCode, state.isLoading, onPairingCodeChange, onPair)
        }
        state.message?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PendingApprovalsCard(
    approvals: List<PendingApproval>,
    onRequestApproval: (PendingApproval) -> Unit,
    onCancelApproval: (PendingApproval) -> Unit,
) {
    if (approvals.isEmpty()) {
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Approval required", style = MaterialTheme.typography.titleMedium)
            approvals.forEach { approval ->
                Text("Remove ${approval.applicationId}?")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRequestApproval(approval) }) {
                        Text("Review removal")
                    }
                    Button(onClick = { onCancelApproval(approval) }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceActionsCard(isLoading: Boolean, onExecuteAction: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Actions", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { onExecuteAction("collectStorageSummary") }, enabled = !isLoading) {
                Text("Update storage")
            }
            Button(onClick = { onExecuteAction("collectAppInventory") }, enabled = !isLoading) {
                Text("Update apps")
            }
        }
    }
}

@Composable
private fun ActionHistoryCard(actions: List<DeviceAction>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Action history", style = MaterialTheme.typography.titleMedium)
            if (actions.isEmpty()) {
                Text("No actions recorded yet.")
            } else {
                actions.forEach { action ->
                    Text("${action.type} · ${action.status}", style = MaterialTheme.typography.bodyMedium)
                    Text("Origin: ${action.origin} · ${action.requestedAt}", style = MaterialTheme.typography.bodySmall)
                    action.resultMessage?.let { message -> Text(message, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun PairingCard(
    code: String,
    isLoading: Boolean,
    onCodeChange: (String) -> Unit,
    onPair: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connect to controller", style = MaterialTheme.typography.titleMedium)
            Text("Enter the six-digit code shown by the controller.")
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Pairing code") },
                singleLine = true,
                enabled = !isLoading,
            )
            Button(onClick = onPair, enabled = code.length == 6 && !isLoading) {
                Text(if (isLoading) "Connecting..." else "Connect")
            }
        }
    }
}

@Composable
private fun DeviceTelemetryCard(telemetry: DeviceTelemetry?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Device", style = MaterialTheme.typography.titleMedium)
            Text(telemetry?.deviceName ?: "Loading...")
            Text("Battery: ${telemetry?.batteryPercentage ?: 0}%")
            Text(if (telemetry?.isCharging == true) "Charging" else "Discharging")
            Text("Captured: ${telemetry?.capturedAt ?: "-"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
