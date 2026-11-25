package com.tcc.devicehealth.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tcc.devicehealth.agent.R
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.ui.components.EmptyState
import com.tcc.devicehealth.agent.ui.components.ListRow
import com.tcc.devicehealth.agent.ui.components.PageControls
import com.tcc.devicehealth.agent.ui.components.RefreshIconButton
import com.tcc.devicehealth.agent.ui.components.RemoveButton
import com.tcc.devicehealth.agent.ui.components.RowDivider
import com.tcc.devicehealth.agent.ui.components.SearchField
import com.tcc.devicehealth.agent.ui.components.SectionHeader
import com.tcc.devicehealth.agent.ui.components.SurfaceCard
import com.tcc.devicehealth.agent.ui.components.countText
import com.tcc.devicehealth.agent.ui.components.relativeTimeText

@Composable
internal fun AppsScreen(state: AgentUiState, onSubmitAction: (String) -> Unit, onRemove: (InstalledApp) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val inventory = state.snapshots.apps
    val apps = inventory?.apps.orEmpty().filter { it.name.contains(query, true) || it.applicationId.contains(query, true) }
    val currentPage = page.coerceAtMost((apps.size - 1).coerceAtLeast(0) / listPageSize)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(R.string.apps_title),
            inventory?.let { countText(R.plurals.apps_count, R.string.apps_none, it.apps.size) + " · " + stringResource(R.string.updated_at, relativeTimeText(it.capturedAt)) }
                ?: stringResource(R.string.apps_waiting),
        ) { RefreshIconButton(stringResource(R.string.refresh_apps), "collectAppInventory", state, onSubmitAction) }
        Text(stringResource(R.string.apps_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SearchField(query, stringResource(R.string.search_apps)) {
            query = it
            page = 0
        }
        if (apps.isEmpty()) {
            EmptyState(stringResource(R.string.apps_empty_title), stringResource(emptyListMessage(inventory == null, query, R.string.apps_waiting, R.string.apps_empty_inventory)))
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
