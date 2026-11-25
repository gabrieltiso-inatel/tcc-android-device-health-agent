package com.tcc.devicehealth.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.tcc.devicehealth.agent.model.FileItem
import com.tcc.devicehealth.agent.ui.components.EmptyState
import com.tcc.devicehealth.agent.ui.components.ListRow
import com.tcc.devicehealth.agent.ui.components.PageControls
import com.tcc.devicehealth.agent.ui.components.RemoveButton
import com.tcc.devicehealth.agent.ui.components.RowDivider
import com.tcc.devicehealth.agent.ui.components.SearchField
import com.tcc.devicehealth.agent.ui.components.SectionHeader
import com.tcc.devicehealth.agent.ui.components.SurfaceCard
import com.tcc.devicehealth.agent.ui.components.bytesText
import com.tcc.devicehealth.agent.ui.components.countText
import com.tcc.devicehealth.agent.ui.components.relativeTimeText

@Composable
internal fun FilesScreen(
    state: AgentUiState,
    onCollect: () -> Unit,
    onSelectFolder: () -> Unit,
    onMediaAccess: () -> Unit,
    onRemove: (FileItem) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val inventory = state.snapshots.files
    val files = inventory?.files.orEmpty().filter { it.name.contains(query, true) }.sortedByDescending { it.sizeBytes }
    val currentPage = page.coerceAtMost((files.size - 1).coerceAtLeast(0) / listPageSize)
    val collecting = state.actions.any { it.type == "collectFileInventory" && it.isActive() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(R.string.files_title),
            inventory?.let { countText(R.plurals.files_count, R.string.files_none, it.files.size) + " · " + stringResource(R.string.updated_at, relativeTimeText(it.capturedAt)) }
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
            EmptyState(stringResource(R.string.files_empty_title), stringResource(emptyListMessage(inventory == null, query, R.string.files_waiting, R.string.files_empty_sources)))
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
