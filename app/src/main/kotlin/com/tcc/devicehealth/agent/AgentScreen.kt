package com.tcc.devicehealth.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant

@Composable
fun AgentScreen(
    state: AgentUiState,
    onPairingCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    onExecuteAction: (String) -> Unit,
    onRequestApplicationRemoval: (PendingApproval) -> Unit,
    onCancelApplicationRemoval: (PendingApproval) -> Unit,
    onRequestFileRemoval: (PendingFileApproval) -> Unit,
    onCancelFileRemoval: (PendingFileApproval) -> Unit,
    onConfirmFileRemoval: () -> Unit,
    onCollectFileInventory: () -> Unit,
    onSelectFileFolder: () -> Unit,
    onRequestMediaAccess: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Device Health Agent", style = MaterialTheme.typography.headlineMedium)
        when {
            !state.isReady -> Text("Loading...")
            state.isPaired -> {
                AgentTabs(selectedTab, state.pendingApprovals.size + state.pendingFileApprovals.size) { selectedTab = it }
                when (selectedTab) {
                    0 -> OverviewTab(state, onExecuteAction)
                    1 -> PendingTab(state, onRequestApplicationRemoval, onCancelApplicationRemoval, onRequestFileRemoval, onCancelFileRemoval)
                    2 -> HistoryTab(state.actions)
                    else -> FilesTab(state.isLoading, onCollectFileInventory, onSelectFileFolder, onRequestMediaAccess)
                }
            }
            else -> PairingCard(state.pairingCode, state.isLoading, onPairingCodeChange, onPair)
        }
        state.message?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
        state.fileApprovalRequest?.let { approval ->
            FileApprovalDialog(approval, onCancelFileRemoval, onConfirmFileRemoval)
        }
    }
}

@Composable
private fun AgentTabs(selectedTab: Int, pendingCount: Int, onTabSelected: (Int) -> Unit) {
    ScrollableTabRow(selectedTabIndex = selectedTab) {
        Tab(selected = selectedTab == 0, onClick = { onTabSelected(0) }, text = { Text("Overview") })
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = { Text(if (pendingCount == 0) "Pending" else "Pending ($pendingCount)") },
        )
        Tab(selected = selectedTab == 2, onClick = { onTabSelected(2) }, text = { Text("History") })
        Tab(selected = selectedTab == 3, onClick = { onTabSelected(3) }, text = { Text("Files") })
    }
}

@Composable
private fun OverviewTab(state: AgentUiState, onExecuteAction: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DeviceTelemetryCard(state.telemetry)
        OverviewStatusCard(state)
        DeviceActionsCard(state.isLoading, onExecuteAction)
    }
}

@Composable
private fun OverviewStatusCard(state: AgentUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Activity", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.pendingApprovals.isEmpty()) "No approvals are waiting for you."
                else "${state.pendingApprovals.size} approval(s) need your attention.",
            )
            Text(
                if (state.actions.isEmpty()) "No actions recorded yet."
                else "${state.actions.size} action(s) in the shared history.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PendingTab(
    state: AgentUiState,
    onRequestApproval: (PendingApproval) -> Unit,
    onCancelApplicationRemoval: (PendingApproval) -> Unit,
    onRequestFileRemoval: (PendingFileApproval) -> Unit,
    onCancelFileRemoval: (PendingFileApproval) -> Unit,
) {
    if (state.pendingApprovals.isEmpty() && state.pendingFileApprovals.isEmpty()) {
        EmptyStateCard("No pending actions", "New approvals and actions that need attention will appear here.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Actions waiting for your attention", style = MaterialTheme.typography.titleLarge)
        state.pendingApprovals.forEach { approval ->
            PendingApprovalCard(approval, onRequestApproval, onCancelApplicationRemoval)
        }
        state.pendingFileApprovals.forEach { approval ->
            PendingFileApprovalCard(approval, onRequestFileRemoval, onCancelFileRemoval)
        }
    }
}

@Composable
private fun PendingFileApprovalCard(
    approval: PendingFileApproval,
    onRequestApproval: (PendingFileApproval) -> Unit,
    onCancelApproval: (PendingFileApproval) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Approval required", style = MaterialTheme.typography.titleMedium)
            Text("Remove file ${approval.fileId.take(12)}…?")
            approval.expectedRevision?.let { revision ->
                Text("Revision: ${revision.take(12)}…", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRequestApproval(approval) }) { Text("Review") }
                Button(onClick = { onCancelApproval(approval) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun FileApprovalDialog(
    approval: PendingFileApproval,
    onCancel: (PendingFileApproval) -> Unit,
    onApprove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onCancel(approval) },
        title = { Text("Remove file?") },
        text = { Text("The selected file will be removed from its authorized folder. This action cannot be undone by the agent.") },
        confirmButton = { TextButton(onClick = onApprove) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { onCancel(approval) }) { Text("Cancel") } },
    )
}

@Composable
private fun PendingApprovalCard(
    approval: PendingApproval,
    onRequestApproval: (PendingApproval) -> Unit,
    onCancelApproval: (PendingApproval) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Approval required", style = MaterialTheme.typography.titleMedium)
            Text("Remove application ${approval.applicationId}?")
            approval.expectedVersionCode?.let { version ->
                Text("Expected version: $version", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRequestApproval(approval) }) { Text("Review") }
                Button(onClick = { onCancelApproval(approval) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun HistoryTab(actions: List<DeviceAction>) {
    if (actions.isEmpty()) {
        EmptyStateCard("No actions yet", "Completed and pending actions will be kept here for reference.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Action history", style = MaterialTheme.typography.titleLarge)
        actions.forEach { action -> ActionHistoryItem(action) }
    }
}

@Composable
private fun FilesTab(
    isLoading: Boolean,
    onCollectInventory: () -> Unit,
    onSelectFolder: () -> Unit,
    onRequestMediaAccess: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Files", style = MaterialTheme.typography.titleLarge)
        Text("Collect metadata from shared media and folders you explicitly authorize.")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Shared media", style = MaterialTheme.typography.titleMedium)
                Text("Images, videos and audio can be included after you grant access.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRequestMediaAccess, enabled = !isLoading) { Text("Grant media access") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Authorized folders", style = MaterialTheme.typography.titleMedium)
                Text("Choose a folder to include its files and subfolders.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onSelectFolder, enabled = !isLoading) { Text("Choose folder") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Inventory", style = MaterialTheme.typography.titleMedium)
                Text("Only metadata is sent to the controller. File contents are not read or uploaded.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onCollectInventory, enabled = !isLoading) {
                    Text(if (isLoading) "Collecting..." else "Collect file inventory")
                }
            }
        }
    }
}

@Composable
private fun ActionHistoryItem(action: DeviceAction) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(action.type.toDisplayName(), style = MaterialTheme.typography.titleMedium)
            Text(action.status.toDisplayName(), style = MaterialTheme.typography.bodyMedium)
            Text(
                "${action.origin.toDisplayName()} · ${formatRelativeTime(action.requestedAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
            action.resultMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DeviceActionsCard(isLoading: Boolean, onExecuteAction: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick actions", style = MaterialTheme.typography.titleMedium)
            Text("Request a fresh reading from the controller.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onExecuteAction("collectStorageSummary") }, enabled = !isLoading) {
                    Text("Update storage")
                }
                Button(onClick = { onExecuteAction("collectAppInventory") }, enabled = !isLoading) {
                    Text("Update apps")
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
            Text("Device overview", style = MaterialTheme.typography.titleMedium)
            Text(telemetry?.deviceName ?: "Loading...", style = MaterialTheme.typography.titleLarge)
            Text("Battery: ${telemetry?.batteryPercentage ?: 0}%")
            Text(if (telemetry?.isCharging == true) "Charging" else "Discharging")
            Text("Captured: ${telemetry?.capturedAt?.let(::formatRelativeTime) ?: "-"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun String.toDisplayName(): String = replace(Regex("([a-z])([A-Z])"), "$1 $2")
    .replaceFirstChar { it.uppercase() }

private fun formatRelativeTime(value: String): String = runCatching {
    val seconds = Duration.between(Instant.parse(value), Instant.now()).seconds.coerceAtLeast(0)
    when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3_600}h ago"
        seconds < 2_592_000 -> "${seconds / 86_400}d ago"
        else -> "${seconds / 2_592_000}mo ago"
    }
}.getOrDefault(value)
