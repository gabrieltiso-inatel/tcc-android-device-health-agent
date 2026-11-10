@file:OptIn(ExperimentalMaterial3Api::class)

package com.tcc.devicehealth.agent

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

internal fun LazyListScope.settingsContent(
    state: AgentUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestMediaAccess: () -> Unit,
    onSelectFolder: () -> Unit,
) {
    item {
        SurfaceCard {
            SectionHeader(stringResource(R.string.settings_theme))
            ChoiceGroup(
                options = listOf(ThemeMode.System to R.string.theme_system, ThemeMode.Light to R.string.theme_light, ThemeMode.Dark to R.string.theme_dark),
                selected = state.displayPreferences.themeMode,
                onSelect = onThemeModeChange,
            )
        }
    }
    item {
        SurfaceCard {
            SectionHeader(stringResource(R.string.settings_language))
            ChoiceGroup(
                options = listOf(AppLanguage.System to R.string.language_system, AppLanguage.Portuguese to R.string.language_portuguese, AppLanguage.English to R.string.language_english),
                selected = state.displayPreferences.language,
                onSelect = onLanguageChange,
            )
        }
    }
    item {
        SurfaceCard {
            SectionHeader(stringResource(R.string.settings_permissions), stringResource(R.string.settings_permissions_description))
            PermissionRow(
                title = stringResource(R.string.permission_notifications),
                description = stringResource(R.string.permission_notifications_description),
                granted = state.permissions.notificationsGranted,
                actionLabel = stringResource(R.string.permission_allow),
                onAction = onRequestNotificationPermission,
            )
            RowDivider()
            PermissionRow(
                title = stringResource(R.string.permission_media),
                description = stringResource(R.string.permission_media_description),
                granted = state.permissions.mediaGranted,
                actionLabel = stringResource(R.string.permission_allow),
                onAction = onRequestMediaAccess,
            )
            RowDivider()
            val folders = state.snapshots.files?.sources.orEmpty().count { it.kind == "selected_folder" }
            ListRow(
                title = stringResource(R.string.permission_folders),
                supporting = countText(R.plurals.permission_folders_count, R.string.permission_folders_none, folders),
                icon = R.drawable.ic_folder,
                trailing = { OutlinedButton(onClick = onSelectFolder) { Text(stringResource(R.string.choose_folder)) } },
            )
        }
    }
    item {
        SurfaceCard {
            SectionHeader(stringResource(R.string.settings_connection))
            ListRow(stringResource(R.string.controller_address), state.controllerAddress.ifEmpty { "—" })
            RowDivider()
            ListRow(
                stringResource(R.string.connection_status),
                stringResource(if (state.isShowingSavedReadings) R.string.connection_saved else R.string.connection_ok),
            )
            state.telemetry?.let { telemetry ->
                RowDivider()
                ListRow(stringResource(R.string.agent_version), "${telemetry.agentVersion} · Android ${telemetry.androidVersion}")
            }
        }
    }
}

@Composable
private fun <T> ChoiceGroup(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.selectableGroup()) {
        options.forEach { (value, @StringRes label) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Spacer(Modifier.size(12.dp))
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, actionLabel: String, onAction: () -> Unit) {
    ListRow(
        title = title,
        supporting = description,
        trailing = {
            if (granted) {
                Text(stringResource(R.string.permission_granted), style = MaterialTheme.typography.labelLarge, color = LocalStatusColors.current.onSuccessContainer)
            } else {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        },
    )
}
