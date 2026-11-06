@file:OptIn(ExperimentalMaterial3Api::class)

package com.tcc.devicehealth.agent

import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.key
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

enum class AgentTab { Home, Apps, Files, Approvals, History }

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
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onLanguageChange: (AppLanguage) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onMessageShown: (AgentMessage) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(AgentTab.Home) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var removalApp by remember { mutableStateOf<InstalledApp?>(null) }
    var removalFile by remember { mutableStateOf<FileItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        messageText?.let { snackbarHostState.showSnackbar(it) }
        onMessageShown(message)
    }

    Scaffold(
        topBar = {
            if (showSettings) {
                SettingsTopBar { showSettings = false }
            } else {
                HomeTopBar(state) { showSettings = true }
            }
        },
        bottomBar = {
            if (state.isPaired && !showSettings) AgentNavigationBar(selectedTab, pendingApprovalCount(state)) { selectedTab = it }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            val listState = key(showSettings, selectedTab) { rememberLazyListState() }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    !state.isReady -> item { Box(Modifier.fillMaxWidth().padding(48.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) } }
                    showSettings -> settingsContent(state, onThemeModeChange, onLanguageChange, onRequestNotificationPermission, onRequestMediaAccess, onSelectFileFolder)
                    !state.isPaired -> item { PairingCard(state, onPairingCodeChange, onControllerAddressChange, onPair) }
                    else -> {
                        if (state.isShowingSavedReadings) item { SavedReadingsNotice() }
                        when (selectedTab) {
                            AgentTab.Home -> homeContent(state, onExecuteAction, { selectedTab = AgentTab.Approvals }, { selectedTab = AgentTab.History })
                            AgentTab.Apps -> item { AppsTab(state, onExecuteAction) { removalApp = it } }
                            AgentTab.Files -> item { FilesTab(state, onCollectFileInventory, onSelectFileFolder, onRequestMediaAccess) { removalFile = it } }
                            AgentTab.Approvals -> item { ApprovalsTab(state, onRequestApplicationRemoval, onCancelApplicationRemoval, onRequestFileRemoval, onCancelFileRemoval) }
                            AgentTab.History -> item { HistoryTab(state) }
                        }
                    }
                }
            }
        }
    }
    removalApp?.let { app ->
        RemovalRequestDialog(app.name, { removalApp = null }) {
            removalApp = null
            onRemoveApplication(app)
        }
    }
    removalFile?.let { file ->
        RemovalRequestDialog(file.name, { removalFile = null }) {
            removalFile = null
            onRemoveFile(file)
        }
    }
    state.fileApprovalRequest?.let { approval ->
        AlertDialog(
            onDismissRequest = onDismissFileRemoval,
            title = { Text(stringResource(R.string.file_confirm_title)) },
            text = { Text(stringResource(R.string.file_confirm_message, approval.name ?: stringResource(R.string.file_previously_selected))) },
            confirmButton = { TextButton(onClick = onConfirmFileRemoval) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismissFileRemoval) { Text(stringResource(R.string.back)) } },
        )
    }
}

@Composable
private fun HomeTopBar(state: AgentUiState, onOpenSettings: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.app_brand), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(state.telemetry?.deviceName ?: stringResource(R.string.this_device), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun AgentNavigationBar(selected: AgentTab, approvals: Int, onSelect: (AgentTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationItem(selected == AgentTab.Home, stringResource(R.string.tab_home), vector = Icons.Filled.Home) { onSelect(AgentTab.Home) }
        NavigationItem(selected == AgentTab.Apps, stringResource(R.string.tab_apps), drawable = R.drawable.ic_apps) { onSelect(AgentTab.Apps) }
        NavigationItem(selected == AgentTab.Files, stringResource(R.string.tab_files), drawable = R.drawable.ic_folder) { onSelect(AgentTab.Files) }
        NavigationItem(selected == AgentTab.Approvals, stringResource(R.string.tab_approvals), vector = Icons.Filled.Notifications, badge = approvals) { onSelect(AgentTab.Approvals) }
        NavigationItem(selected == AgentTab.History, stringResource(R.string.tab_history), drawable = R.drawable.ic_history) { onSelect(AgentTab.History) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavigationItem(
    selected: Boolean,
    label: String,
    vector: ImageVector? = null,
    @DrawableRes drawable: Int? = null,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        icon = {
            BadgedBox(badge = { if (badge > 0) Badge { Text(badge.toString()) } }) {
                if (vector != null) Icon(vector, contentDescription = null) else Icon(painterResource(checkNotNull(drawable)), contentDescription = null)
            }
        },
    )
}

private fun LazyListScope.homeContent(state: AgentUiState, onExecuteAction: (String) -> Unit, onOpenApprovals: () -> Unit, onOpenHistory: () -> Unit) {
    item { HealthSummaryCard(state, onOpenApprovals) }
    item {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BatteryCard(state, onExecuteAction, Modifier.weight(1f).fillMaxHeight())
            StorageCard(state, onExecuteAction, Modifier.weight(1f).fillMaxHeight())
        }
    }
    item { RecentActivityCard(state, onOpenHistory) }
}

@Composable
private fun HealthSummaryCard(state: AgentUiState, onOpenApprovals: () -> Unit) {
    val verdict = healthVerdict(state)
    val statusColors = LocalStatusColors.current
    val healthy = verdict == HealthVerdict.Healthy
    Surface(
        color = if (healthy) statusColors.successContainer else statusColors.warningContainer,
        contentColor = if (healthy) statusColors.onSuccessContainer else statusColors.onWarningContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (healthy) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
                Text(verdictTitle(verdict), style = MaterialTheme.typography.headlineSmall)
            }
            Text(verdictMessage(verdict), style = MaterialTheme.typography.bodyLarge)
            state.telemetry?.let { Text(stringResource(R.string.last_sync, relativeTimeText(it.capturedAt)), style = MaterialTheme.typography.bodyMedium) }
            if (verdict is HealthVerdict.ApprovalsWaiting) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onOpenApprovals) { Text(stringResource(R.string.review_approvals)) }
            }
        }
    }
}

@Composable
private fun verdictTitle(verdict: HealthVerdict): String = when (verdict) {
    HealthVerdict.Healthy -> stringResource(R.string.health_good_title)
    is HealthVerdict.ApprovalsWaiting -> stringResource(R.string.health_attention_title)
    HealthVerdict.LowBattery -> stringResource(R.string.health_low_battery_title)
    HealthVerdict.StorageAlmostFull -> stringResource(R.string.health_storage_title)
    HealthVerdict.SavedReadings -> stringResource(R.string.health_saved_title)
}

@Composable
private fun verdictMessage(verdict: HealthVerdict): String = when (verdict) {
    HealthVerdict.Healthy -> stringResource(R.string.health_good_message)
    is HealthVerdict.ApprovalsWaiting -> quantityText(R.plurals.health_approvals_message, verdict.count)
    HealthVerdict.LowBattery -> stringResource(R.string.health_low_battery_message)
    HealthVerdict.StorageAlmostFull -> stringResource(R.string.health_storage_message)
    HealthVerdict.SavedReadings -> stringResource(R.string.health_saved_message)
}

@Composable
private fun MetricCard(
    title: String,
    @DrawableRes icon: Int,
    value: String,
    supporting: String,
    refreshLabel: String,
    refreshType: String,
    state: AgentUiState,
    onExecuteAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    SurfaceCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = metricTextStyle, modifier = Modifier.weight(1f))
            RefreshIconButton(refreshLabel, refreshType, state, onExecuteAction)
        }
        extra()
        Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BatteryCard(state: AgentUiState, onExecuteAction: (String) -> Unit, modifier: Modifier) {
    val telemetry = state.telemetry
    MetricCard(
        title = stringResource(R.string.battery_title),
        icon = R.drawable.ic_battery,
        value = telemetry?.let { "${it.batteryPercentage}%" } ?: "—",
        supporting = when {
            telemetry == null -> stringResource(R.string.no_reading_yet)
            telemetry.isCharging -> stringResource(R.string.battery_charging)
            else -> stringResource(R.string.battery_not_charging)
        },
        refreshLabel = stringResource(R.string.refresh_battery),
        refreshType = "collectTelemetry",
        state = state,
        onExecuteAction = onExecuteAction,
        modifier = modifier,
    )
}

@Composable
private fun StorageCard(state: AgentUiState, onExecuteAction: (String) -> Unit, modifier: Modifier) {
    val storage = state.snapshots.storage
    MetricCard(
        title = stringResource(R.string.storage_title),
        icon = R.drawable.ic_storage,
        value = storage?.let { "${(storageUsedFraction(it) * 100).toInt()}%" } ?: "—",
        supporting = storage?.let { stringResource(R.string.storage_used_free_of, bytesText(it.availableBytes), bytesText(it.totalBytes)) }
            ?: stringResource(R.string.no_reading_yet),
        refreshLabel = stringResource(R.string.refresh_storage),
        refreshType = "collectStorageSummary",
        state = state,
        onExecuteAction = onExecuteAction,
        modifier = modifier,
    ) {
        storage?.let { LinearProgressIndicator(progress = storageUsedFraction(it), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) }
    }
}

@Composable
private fun RefreshIconButton(label: String, type: String, state: AgentUiState, onExecute: (String) -> Unit) {
    val pending = state.actions.any { it.type == type && it.isActive() }
    val available = type in state.telemetry?.capabilities.orEmpty()
    if (!available) return
    IconButton(onClick = { onExecute(type) }, enabled = !state.isLoading && !pending, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Filled.Refresh, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RecentActivityCard(state: AgentUiState, onOpenHistory: () -> Unit) {
    SurfaceCard {
        SectionHeader(stringResource(R.string.recent_activity)) {
            if (state.actions.isNotEmpty()) TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.see_all)) }
        }
        if (state.actions.isEmpty()) {
            Text(stringResource(R.string.no_activity), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.actions.take(3).forEachIndexed { index, action ->
            if (index > 0) RowDivider()
            ActionRow(action, state.snapshots)
        }
    }
}

@Composable
private fun ActionRow(action: DeviceAction, snapshots: DeviceSnapshots) {
    val target = actionTargetName(action, snapshots)
    val title = stringResource(actionLabel(action.type)) + (target?.let { " · $it" } ?: "")
    val result = action.resultMessage?.let { message -> resultLabel(message)?.let { stringResource(it) } ?: message }
    val origin = stringResource(originLabel(action.origin))
    ListRow(
        title = title,
        supporting = listOfNotNull(result, origin, relativeTimeText(action.requestedAt)).joinToString(" · "),
        trailing = { StatusBadge(action.status) },
    )
}

@Composable
private fun AppsTab(state: AgentUiState, onExecuteAction: (String) -> Unit, onRemove: (InstalledApp) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val inventory = state.snapshots.apps
    val apps = inventory?.apps.orEmpty().filter { it.name.contains(query, true) || it.applicationId.contains(query, true) }
    val currentPage = page.coerceAtMost((apps.size - 1).coerceAtLeast(0) / listPageSize)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(R.string.apps_title),
            inventory?.let { quantityText(R.plurals.apps_count, it.apps.size) + " · " + stringResource(R.string.updated_at, relativeTimeText(it.capturedAt)) }
                ?: stringResource(R.string.apps_waiting),
        ) { RefreshIconButton(stringResource(R.string.refresh_apps), "collectAppInventory", state, onExecuteAction) }
        Text(stringResource(R.string.apps_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SearchField(query, stringResource(R.string.search_apps)) {
            query = it
            page = 0
        }
        if (apps.isEmpty()) {
            EmptyState(stringResource(R.string.apps_empty_title), stringResource(if (inventory == null) R.string.apps_waiting else R.string.search_no_results))
        } else {
            SurfaceCard {
                apps.drop(currentPage * listPageSize).take(listPageSize).forEachIndexed { index, app ->
                    if (index > 0) RowDivider()
                    ListRow(
                        title = app.name,
                        supporting = "${app.applicationId}\n" + stringResource(R.string.version, app.versionName.ifEmpty { app.versionCode.toString() }),
                        icon = R.drawable.ic_apps,
                        trailing = {
                            if (app.canRemove && "removeApplication" in state.telemetry?.capabilities.orEmpty()) {
                                RemoveButton(removalInProgress(state.actions, applicationId = app.applicationId)) { onRemove(app) }
                            } else {
                                Text(stringResource(R.string.removal_unavailable), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
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
    val collecting = state.actions.any { it.type == "collectFileInventory" && it.isActive() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(R.string.files_title),
            inventory?.let { quantityText(R.plurals.files_count, it.files.size) + " · " + stringResource(R.string.updated_at, relativeTimeText(it.capturedAt)) }
                ?: stringResource(R.string.files_waiting),
        ) {
            IconButton(onClick = onCollect, enabled = !state.isLoading && !collecting) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh_files))
            }
        }
        SurfaceCard {
            Text(stringResource(R.string.file_access_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.files_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            inventory?.sources?.forEach { source ->
                ListRow(source.label, stringResource(authorizationLabel(source.authorization)), icon = R.drawable.ic_folder)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelectFolder, enabled = !state.isLoading) { Text(stringResource(R.string.choose_folder)) }
                OutlinedButton(onClick = onMediaAccess, enabled = !state.isLoading) { Text(stringResource(R.string.media_access)) }
            }
        }
        SearchField(query, stringResource(R.string.search_files)) {
            query = it
            page = 0
        }
        if (files.isEmpty()) {
            EmptyState(stringResource(R.string.files_empty_title), stringResource(if (inventory == null) R.string.files_waiting else R.string.search_no_results))
        } else {
            SurfaceCard {
                files.drop(currentPage * listPageSize).take(listPageSize).forEachIndexed { index, file ->
                    if (index > 0) RowDivider()
                    val source = inventory?.sources?.find { it.sourceId == file.sourceId }?.label
                    ListRow(
                        title = file.name,
                        supporting = listOfNotNull(bytesText(file.sizeBytes), stringResource(fileKindLabel(file.kind)), source).joinToString(" · "),
                        icon = R.drawable.ic_folder,
                        trailing = {
                            if (file.canRemove && "removeFile" in state.telemetry?.capabilities.orEmpty()) {
                                RemoveButton(removalInProgress(state.actions, fileId = file.fileId)) { onRemove(file) }
                            } else {
                                Text(stringResource(R.string.read_only), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
        }
        PageControls(files.size, currentPage) { page = it }
    }
}

@Composable
private fun ApprovalsTab(state: AgentUiState, onReviewApp: (PendingApproval) -> Unit, onCancelApp: (PendingApproval) -> Unit, onReviewFile: (PendingFileApproval) -> Unit, onCancelFile: (PendingFileApproval) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.approvals_title), stringResource(R.string.approvals_description))
        if (pendingApprovalCount(state) == 0) EmptyState(stringResource(R.string.approvals_empty_title), stringResource(R.string.approvals_empty_message))
        state.pendingApprovals.forEach { approval ->
            val name = state.snapshots.apps?.apps?.find { it.applicationId == approval.applicationId }?.name ?: approval.applicationId
            ApprovalCard(stringResource(R.string.approval_remove_title, name), stringResource(R.string.approval_app_message), state.actions.find { it.id == approval.actionId }, state.isLoading, { onReviewApp(approval) }, { onCancelApp(approval) })
        }
        state.pendingFileApprovals.forEach { approval ->
            val name = approval.name ?: state.snapshots.files?.files?.find { it.fileId == approval.fileId }?.name ?: stringResource(R.string.file_previously_selected)
            ApprovalCard(stringResource(R.string.approval_remove_title, name), stringResource(R.string.approval_file_message), state.actions.find { it.id == approval.actionId }, state.isLoading, { onReviewFile(approval) }, { onCancelFile(approval) })
        }
    }
}

@Composable
private fun ApprovalCard(title: String, message: String, action: DeviceAction?, isLoading: Boolean, onReview: () -> Unit, onDecline: () -> Unit) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusBadge("awaiting_approval")
        }
        action?.let {
            Text(
                stringResource(if (it.origin == "device") R.string.requested_on_device else R.string.requested_from_controller, relativeTimeText(it.requestedAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onReview, enabled = !isLoading) { Text(stringResource(R.string.review)) }
            TextButton(onClick = onDecline, enabled = !isLoading) { Text(stringResource(R.string.decline)) }
        }
    }
}

@Composable
private fun HistoryTab(state: AgentUiState) {
    var status by rememberSaveable { mutableStateOf("all") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val filtered = state.actions.filter { status == "all" || if (status == "active") it.isActive() else it.status == status }
    val currentPage = page.coerceAtMost((filtered.size - 1).coerceAtLeast(0) / listPageSize)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.history_title), stringResource(R.string.history_description))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            listOf("all" to R.string.filter_all, "active" to R.string.filter_active, "completed" to R.string.status_completed, "cancelled" to R.string.status_cancelled, "failed" to R.string.status_failed)
                .forEach { (value, label) ->
                    FilterChip(selected = status == value, onClick = {
                        status = value
                        page = 0
                    }, label = { Text(stringResource(label)) })
                }
        }
        if (filtered.isEmpty()) {
            EmptyState(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_message))
        } else {
            SurfaceCard {
                filtered.drop(currentPage * listPageSize).take(listPageSize).forEachIndexed { index, action ->
                    if (index > 0) RowDivider()
                    ActionRow(action, state.snapshots)
                }
            }
        }
        PageControls(filtered.size, currentPage) { page = it }
    }
}

@Composable
private fun PairingCard(state: AgentUiState, onCodeChange: (String) -> Unit, onAddressChange: (String) -> Unit, onPair: () -> Unit) {
    SurfaceCard {
        Text(stringResource(R.string.pairing_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.pairing_description), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.controllerAddress,
            onValueChange = onAddressChange,
            label = { Text(stringResource(R.string.controller_address)) },
            supportingText = { Text(stringResource(R.string.controller_address_hint)) },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.pairingCode,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.pairing_code)) },
            supportingText = { Text(stringResource(R.string.pairing_code_hint)) },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onPair, enabled = state.pairingCode.length == 6 && !state.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.isLoading) R.string.connecting else R.string.connect))
        }
    }
}

@Composable
private fun SavedReadingsNotice() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.saved_readings_notice), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RemoveButton(inProgress: Boolean, onClick: () -> Unit) {
    if (inProgress) {
        Text(stringResource(R.string.removal_in_progress), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        TextButton(onClick = onClick) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun RemovalRequestDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.request_removal_title)) },
        text = { Text(stringResource(R.string.request_removal_message, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.request_removal)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun messageText(message: AgentMessage): String = when (message) {
    AgentMessage.EnterPairingCode -> stringResource(R.string.message_enter_code)
    AgentMessage.Paired -> stringResource(R.string.message_paired)
    AgentMessage.PairingFailed -> stringResource(R.string.message_pairing_failed)
    AgentMessage.RequestProcessed -> stringResource(R.string.message_request_processed)
    AgentMessage.RequestFailed -> stringResource(R.string.message_request_failed)
    AgentMessage.FolderAdded -> stringResource(R.string.message_folder_added)
    AgentMessage.FolderFailed -> stringResource(R.string.message_folder_failed)
    AgentMessage.ConfirmInAndroidDialog -> stringResource(R.string.message_confirm_android)
    AgentMessage.FileActionRecorded -> stringResource(R.string.message_file_recorded)
    AgentMessage.FileActionFailed -> stringResource(R.string.message_file_failed)
    AgentMessage.FileActionWillRetry -> stringResource(R.string.message_file_retry)
    AgentMessage.ApplicationActionRecorded -> stringResource(R.string.message_app_recorded)
    AgentMessage.ApplicationActionFailed -> stringResource(R.string.message_app_failed)
    AgentMessage.PreferencesFailed -> stringResource(R.string.message_preferences_failed)
    is AgentMessage.ApplicationRemovalFinished -> when (message.status) {
        "completed" -> stringResource(R.string.message_app_removed, message.applicationName)
        "cancelled" -> stringResource(R.string.message_app_declined, message.applicationName)
        else -> stringResource(R.string.message_app_not_removed, message.applicationName)
    }
    is AgentMessage.ActionsProcessed -> LocalContext.current.resources.getQuantityString(R.plurals.message_actions_processed, message.count, message.count)
}
