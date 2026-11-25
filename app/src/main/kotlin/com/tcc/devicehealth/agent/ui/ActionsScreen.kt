@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tcc.devicehealth.agent.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tcc.devicehealth.agent.R
import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import com.tcc.devicehealth.agent.ui.components.ActionRow
import com.tcc.devicehealth.agent.ui.components.EmptyState
import com.tcc.devicehealth.agent.ui.components.PageControls
import com.tcc.devicehealth.agent.ui.components.RowDivider
import com.tcc.devicehealth.agent.ui.components.SectionHeader
import com.tcc.devicehealth.agent.ui.components.StatusBadge
import com.tcc.devicehealth.agent.ui.components.SurfaceCard
import com.tcc.devicehealth.agent.ui.components.relativeTimeText

@Composable
internal fun ApprovalsScreen(
    state: AgentUiState,
    onReviewApp: (PendingApplicationRemoval) -> Unit,
    onCancelApp: (PendingApplicationRemoval) -> Unit,
    onReviewFile: (PendingFileRemoval) -> Unit,
    onCancelFile: (PendingFileRemoval) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.approvals_title), stringResource(R.string.approvals_description))
        if (pendingApprovalCount(state) == 0) EmptyState(stringResource(R.string.approvals_empty_title), stringResource(R.string.approvals_empty_message))
        state.pendingApplicationRemovals.forEach { approval ->
            val name = state.snapshots.apps?.apps?.find { it.applicationId == approval.applicationId }?.name ?: approval.applicationId
            ApprovalCard(
                stringResource(R.string.approval_remove_title, name),
                stringResource(R.string.approval_app_message),
                state.actions.find { it.id == approval.actionId },
                state.isLoading,
                { onReviewApp(approval) },
                { onCancelApp(approval) },
            )
        }
        state.pendingFileRemovals.forEach { approval ->
            val name = approval.name ?: state.snapshots.files?.files?.find { it.fileId == approval.fileId }?.name ?: stringResource(R.string.file_previously_selected)
            ApprovalCard(
                stringResource(R.string.approval_remove_title, name),
                stringResource(R.string.approval_file_message),
                state.actions.find { it.id == approval.actionId },
                state.isLoading,
                { onReviewFile(approval) },
                { onCancelFile(approval) },
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    title: String,
    message: String,
    action: Action?,
    isLoading: Boolean,
    onReview: () -> Unit,
    onDecline: () -> Unit,
) {
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
internal fun HistoryScreen(state: AgentUiState) {
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
