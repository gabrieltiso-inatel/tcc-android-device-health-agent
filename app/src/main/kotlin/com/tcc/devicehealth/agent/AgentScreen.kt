package com.tcc.devicehealth.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AgentScreen(
    state: AgentUiState,
    onPairingCodeChange: (String) -> Unit,
    onControllerAddressChange: (String) -> Unit,
    onPair: () -> Unit,
    onExecuteAction: (String) -> Unit,
    onRequestApplicationRemoval: (PendingApproval) -> Unit,
    onCancelApplicationRemoval: (PendingApproval) -> Unit,
    onRequestFileRemoval: (PendingFileApproval) -> Unit,
    onCancelFileRemoval: (PendingFileApproval) -> Unit,
    onDismissFileRemoval: () -> Unit,
    onRemoveApplication: (InstalledApp) -> Unit,
    onRemoveFile: (FileItem) -> Unit,
    onConfirmFileRemoval: () -> Unit,
    onCollectFileInventory: () -> Unit,
    onSelectFileFolder: () -> Unit,
    onRequestMediaAccess: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var removalApp by remember { mutableStateOf<InstalledApp?>(null) }
    var removalFile by remember { mutableStateOf<FileItem?>(null) }
    val tabs = listOf("Overview", "Apps", "Files", "Pending (${pendingApprovalCount(state)})", "History")
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("DEVICE HEALTH", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(state.telemetry?.deviceName ?: "Your device", style = MaterialTheme.typography.headlineSmall)
        }
        if (state.isPaired) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
        }
        if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.message?.let { message -> item { Notice(message) } }
            if (state.isPaired) state.snapshotError?.let { error -> item { Notice(error, true) } }
            item {
                when {
                    !state.isReady -> CircularProgressIndicator()
                    !state.isPaired -> PairingCard(state, onPairingCodeChange, onControllerAddressChange, onPair)
                    selectedTab == 0 -> OverviewTab(state, onExecuteAction) { selectedTab = 3 }
                    selectedTab == 1 -> AppsTab(state, onExecuteAction) { removalApp = it }
                    selectedTab == 2 -> FilesTab(state, onCollectFileInventory, onSelectFileFolder, onRequestMediaAccess) { removalFile = it }
                    selectedTab == 3 -> PendingTab(state, onRequestApplicationRemoval, onCancelApplicationRemoval, onRequestFileRemoval, onCancelFileRemoval)
                    else -> HistoryTab(state.actions)
                }
            }
        }
    }
    removalApp?.let { app ->
        RemovalDialog(app.name, { removalApp = null }) {
            removalApp = null
            onRemoveApplication(app)
        }
    }
    removalFile?.let { file ->
        RemovalDialog(file.name, { removalFile = null }) {
            removalFile = null
            onRemoveFile(file)
        }
    }
    state.fileApprovalRequest?.let { approval ->
        AlertDialog(
            onDismissRequest = onDismissFileRemoval,
            title = { Text("Remove file?") },
            text = { Text("Remove ${approval.name ?: "the previously selected file"}? This cannot be undone by the agent.") },
            confirmButton = { TextButton(onClick = onConfirmFileRemoval) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismissFileRemoval) { Text("Back") } },
        )
    }
}

@Composable
private fun OverviewTab(state: AgentUiState, onExecuteAction: (String) -> Unit, onOpenPending: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard("Device overview") {
            Text("${state.telemetry?.batteryPercentage ?: 0}%", style = MaterialTheme.typography.displaySmall)
            Text(if (state.telemetry?.isCharging == true) "Charging" else "Not charging")
            state.telemetry?.let { telemetry ->
                Text("${telemetry.manufacturer} ${telemetry.model} · Android ${telemetry.androidVersion}", style = MaterialTheme.typography.bodySmall)
                UpdatedAt(telemetry.capturedAt)
            }
            RefreshButton("Update device", state, "collectTelemetry", onExecuteAction)
        }
        SectionCard("Storage") {
            val storage = state.snapshots.storage
            if (storage == null) Text("No storage reading yet.") else {
                Text("${formatBytes(storage.usedBytes)} of ${formatBytes(storage.totalBytes)}", style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(progress = if (storage.totalBytes > 0) (storage.usedBytes.toFloat() / storage.totalBytes).coerceIn(0f, 1f) else 0f, modifier = Modifier.fillMaxWidth())
                Text("${formatBytes(storage.availableBytes)} available")
                UpdatedAt(storage.capturedAt)
            }
            RefreshButton("Update storage", state, "collectStorageSummary", onExecuteAction)
        }
        SectionCard("Pending approvals") {
            val count = pendingApprovalCount(state)
            Text(if (count == 0) "You're all caught up." else "$count approval(s) need your attention.")
            if (count > 0) Button(onClick = onOpenPending) { Text("Review pending actions") }
        }
    }
}

@Composable
private fun AppsTab(state: AgentUiState, onExecuteAction: (String) -> Unit, onRemove: (InstalledApp) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val inventory = state.snapshots.apps
    val apps = inventory?.apps.orEmpty().filter { it.name.contains(query, true) || it.applicationId.contains(query, true) }
    val currentPage = page.coerceAtMost((apps.size - 1).coerceAtLeast(0) / listPageSize)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Installed apps") {
            Text("User-installed applications. Removal requires your approval.")
            inventory?.let { UpdatedAt(it.capturedAt) }
            RefreshButton("Update apps", state, "collectAppInventory", onExecuteAction)
            SearchField(query, "Search apps") {
                query = it
                page = 0
            }
        }
        if (apps.isEmpty()) EmptyState("No apps to show", if (inventory == null) "Waiting for the first inventory." else "Try a different search.")
        apps.drop(currentPage * listPageSize).take(listPageSize).forEach { app ->
            SectionCard(app.name) {
                Text(app.applicationId, style = MaterialTheme.typography.bodySmall)
                Text("Version ${app.versionName.ifEmpty { app.versionCode.toString() }}", style = MaterialTheme.typography.bodySmall)
                if (app.canRemove && "removeApplication" in state.telemetry?.capabilities.orEmpty()) {
                    RemoveButton(state.isLoading || removalInProgress(state.actions, applicationId = app.applicationId)) { onRemove(app) }
                } else Text("Removal unavailable", style = MaterialTheme.typography.labelMedium)
            }
        }
        PageControls(apps.size, currentPage) { page = it }
    }
}

@Composable
private fun FilesTab(state: AgentUiState, onCollect: () -> Unit, onSelectFolder: () -> Unit, onMediaAccess: () -> Unit, onRemove: (FileItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val inventory = state.snapshots.files
    val files = inventory?.files.orEmpty().filter { it.name.contains(query, true) }.sortedByDescending { it.sizeBytes }
    val currentPage = page.coerceAtMost((files.size - 1).coerceAtLeast(0) / listPageSize)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Files") {
            Text("Files from shared media and folders you authorize. Only metadata is shared.")
            inventory?.let { UpdatedAt(it.capturedAt) }
            OutlinedButton(onClick = onCollect, enabled = !state.isLoading && state.actions.none { it.type == "collectFileInventory" && it.isActive() }) { Text("Update files") }
            SearchField(query, "Search files") {
                query = it
                page = 0
            }
        }
        SectionCard("File access") {
            Text("Choose folders or grant media access to include more files.")
            OutlinedButton(onClick = onSelectFolder, enabled = !state.isLoading) { Text("Choose folder") }
            OutlinedButton(onClick = onMediaAccess, enabled = !state.isLoading) { Text("Manage media access") }
            inventory?.sources?.forEach { source ->
                Text("${source.label} · ${source.authorization.toDisplayName()}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (files.isEmpty()) EmptyState("No files to show", if (inventory == null) "Authorize a source and update files." else "No files match the current search or authorized sources.")
        files.drop(currentPage * listPageSize).take(listPageSize).forEach { file ->
            SectionCard(file.name) {
                Text("${formatBytes(file.sizeBytes)} · ${file.kind.toDisplayName()}")
                Text(inventory?.sources?.find { it.sourceId == file.sourceId }?.label ?: "Authorized source", style = MaterialTheme.typography.bodySmall)
                file.displayPath?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (file.canRemove && "removeFile" in state.telemetry?.capabilities.orEmpty()) {
                    RemoveButton(state.isLoading || removalInProgress(state.actions, fileId = file.fileId)) { onRemove(file) }
                } else Text("Read only", style = MaterialTheme.typography.labelMedium)
            }
        }
        PageControls(files.size, currentPage) { page = it }
    }
}

@Composable
private fun PendingTab(state: AgentUiState, onReviewApp: (PendingApproval) -> Unit, onCancelApp: (PendingApproval) -> Unit, onReviewFile: (PendingFileApproval) -> Unit, onCancelFile: (PendingFileApproval) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (pendingApprovalCount(state) == 0) EmptyState("No pending approvals", "Requests that need your approval will appear here.")
        state.pendingApprovals.forEach { approval ->
            val name = state.snapshots.apps?.apps?.find { it.applicationId == approval.applicationId }?.name ?: approval.applicationId
            SectionCard("Remove $name?") {
                StatusBadge("awaiting_approval")
                Text("Android will ask you to confirm the removal.")
                ApprovalButtons(state.isLoading, { onReviewApp(approval) }, { onCancelApp(approval) })
            }
        }
        state.pendingFileApprovals.forEach { approval ->
            val name = approval.name ?: state.snapshots.files?.files?.find { it.fileId == approval.fileId }?.name ?: "Previously selected file"
            SectionCard("Remove $name?") {
                StatusBadge("awaiting_approval")
                Text("Review before removing this file from its authorized folder.")
                ApprovalButtons(state.isLoading, { onReviewFile(approval) }, { onCancelFile(approval) })
            }
        }
    }
}

@Composable
private fun HistoryTab(actions: List<DeviceAction>) {
    var status by rememberSaveable { mutableStateOf("all") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val filtered = actions.filter { status == "all" || if (status == "active") it.isActive() else it.status == status }
    val currentPage = page.coerceAtMost((filtered.size - 1).coerceAtLeast(0) / listPageSize)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Action history", style = MaterialTheme.typography.titleLarge)
        HistoryFilter(status) {
            status = it
            page = 0
        }
        if (filtered.isEmpty()) EmptyState("No matching actions", "Automatic readings do not create history entries.")
        filtered.drop(currentPage * listPageSize).take(listPageSize).forEach { action ->
            SectionCard(action.type.toDisplayName()) {
                StatusBadge(action.status)
                Text("${action.origin.toDisplayName()} · ${formatRelativeTime(action.requestedAt)}", style = MaterialTheme.typography.bodySmall)
                action.resultMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        PageControls(filtered.size, currentPage) { page = it }
    }
}

@Composable
private fun PairingCard(state: AgentUiState, onCodeChange: (String) -> Unit, onAddressChange: (String) -> Unit, onPair: () -> Unit) {
    SectionCard("Connect to your controller") {
        Text("Enter the controller address and its six-digit pairing code.")
        OutlinedTextField(value = state.controllerAddress, onValueChange = onAddressChange, label = { Text("Controller URL") }, singleLine = true, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth())
        Text("Emulator: http://10.0.2.2:3000 · Phone: use your computer's network address.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = state.pairingCode, onValueChange = onCodeChange, label = { Text("Pairing code") }, singleLine = true, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth())
        Button(onClick = onPair, enabled = state.pairingCode.length == 6 && !state.isLoading) { Text(if (state.isLoading) "Connecting…" else "Connect") }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun RefreshButton(label: String, state: AgentUiState, type: String, onExecute: (String) -> Unit) {
    val pending = state.actions.any { it.type == type && it.isActive() }
    OutlinedButton(onClick = { onExecute(type) }, enabled = !state.isLoading && !pending && type in state.telemetry?.capabilities.orEmpty()) {
        Text(if (pending) "Update in progress…" else label)
    }
}

@Composable
private fun ApprovalButtons(isLoading: Boolean, onReview: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onReview, enabled = !isLoading) { Text("Review") }
        TextButton(onClick = onCancel, enabled = !isLoading) { Text("Decline") }
    }
}

@Composable
private fun RemoveButton(disabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = !disabled) { Text(if (disabled) "Action in progress…" else "Remove", color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun RemovalDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Request removal?") },
        text = { Text("Request removal of $name? You will review the approval before anything is removed.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Request removal") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
