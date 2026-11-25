@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tcc.devicehealth.agent.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tcc.devicehealth.agent.R
import com.tcc.devicehealth.agent.model.AppLanguage
import com.tcc.devicehealth.agent.model.FileItem
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import com.tcc.devicehealth.agent.model.ThemeMode
import com.tcc.devicehealth.agent.ui.components.ActionRow
import com.tcc.devicehealth.agent.ui.components.RefreshIconButton
import com.tcc.devicehealth.agent.ui.components.RemovalRequestDialog
import com.tcc.devicehealth.agent.ui.components.RowDivider
import com.tcc.devicehealth.agent.ui.components.SectionHeader
import com.tcc.devicehealth.agent.ui.components.SurfaceCard
import com.tcc.devicehealth.agent.ui.components.bytesText
import com.tcc.devicehealth.agent.ui.components.quantityText
import com.tcc.devicehealth.agent.ui.components.relativeTimeText

enum class AgentTab { Home, Apps, Files, Approvals, History }

@Composable
fun AgentScreen(
    state: AgentUiState,
    onPairingCodeChange: (String) -> Unit,
    onControllerAddressChange: (String) -> Unit,
    onPair: () -> Unit,
    onSubmitAction: (String) -> Unit,
    onReviewApplicationRemoval: (PendingApplicationRemoval) -> Unit,
    onCancelApplicationRemoval: (PendingApplicationRemoval) -> Unit,
    onReviewFileRemoval: (PendingFileRemoval) -> Unit,
    onCancelFileRemoval: (PendingFileRemoval) -> Unit,
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
            if (state.isPaired && !showSettings) {
                AgentNavigationBar(selectedTab, pendingApprovalCount(state)) { selectedTab = it }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }
            val listState = key(showSettings, selectedTab) { rememberLazyListState() }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    !state.isReady -> item {
                        Box(Modifier.fillMaxWidth().padding(48.dp)) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }
                    showSettings -> settingsContent(
                        state,
                        onThemeModeChange,
                        onLanguageChange,
                        onRequestNotificationPermission,
                        onRequestMediaAccess,
                        onSelectFileFolder,
                    )
                    !state.isPaired -> item { PairingCard(state, onPairingCodeChange, onControllerAddressChange, onPair) }
                    else -> {
                        if (state.isShowingSavedReadings) item { SavedReadingsNotice() }
                        when (selectedTab) {
                            AgentTab.Home -> homeContent(
                                state,
                                onSubmitAction,
                                { selectedTab = AgentTab.Approvals },
                                { selectedTab = AgentTab.History },
                            )
                            AgentTab.Apps -> item { AppsScreen(state, onSubmitAction) { removalApp = it } }
                            AgentTab.Files -> item {
                                FilesScreen(state, onCollectFileInventory, onSelectFileFolder, onRequestMediaAccess) {
                                    removalFile = it
                                }
                            }
                            AgentTab.Approvals -> item {
                                ApprovalsScreen(
                                    state,
                                    onReviewApplicationRemoval,
                                    onCancelApplicationRemoval,
                                    onReviewFileRemoval,
                                    onCancelFileRemoval,
                                )
                            }
                            AgentTab.History -> item { HistoryScreen(state) }
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
    state.fileConfirmation?.let { approval ->
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
private fun SavedReadingsNotice() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.saved_readings_notice), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun LazyListScope.homeContent(
    state: AgentUiState,
    onSubmitAction: (String) -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    item { HealthSummaryCard(state, onOpenApprovals) }
    item {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BatteryCard(state, onSubmitAction, Modifier.weight(1f).fillMaxHeight())
            StorageCard(state, onSubmitAction, Modifier.weight(1f).fillMaxHeight())
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
    onSubmitAction: (String) -> Unit,
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
            RefreshIconButton(refreshLabel, refreshType, state, onSubmitAction)
        }
        extra()
        Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BatteryCard(state: AgentUiState, onSubmitAction: (String) -> Unit, modifier: Modifier) {
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
        onSubmitAction = onSubmitAction,
        modifier = modifier,
    )
}

@Composable
private fun StorageCard(state: AgentUiState, onSubmitAction: (String) -> Unit, modifier: Modifier) {
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
        onSubmitAction = onSubmitAction,
        modifier = modifier,
    ) {
        storage?.let { LinearProgressIndicator(progress = storageUsedFraction(it), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) }
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
internal fun PairingCard(
    state: AgentUiState,
    onCodeChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onPair: () -> Unit,
) {
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
internal fun HomeTopBar(state: AgentUiState, onOpenSettings: () -> Unit) {
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
internal fun AgentNavigationBar(selected: AgentTab, approvals: Int, onSelect: (AgentTab) -> Unit) {
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
