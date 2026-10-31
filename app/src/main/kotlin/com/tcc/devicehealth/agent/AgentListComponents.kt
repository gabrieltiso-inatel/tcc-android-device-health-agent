package com.tcc.devicehealth.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun Notice(message: String, isError: Boolean = false) {
    Surface(color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun UpdatedAt(timestamp: String) {
    Text("Updated ${formatRelativeTime(timestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun EmptyState(title: String, message: String) {
    SectionCard(title) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
internal fun StatusBadge(status: String) {
    val colors = when (status) {
        "completed" -> Color(0xFFE5F6ED) to Color(0xFF17633A)
        "pending", "delivered", "awaiting_approval" -> Color(0xFFFFF3D6) to Color(0xFF785300)
        "failed", "expired" -> Color(0xFFFDE9E7) to Color(0xFF9A2C22)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = colors.first, contentColor = colors.second, shape = MaterialTheme.shapes.small) {
        Text(status.toDisplayName(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun SearchField(query: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = query, onValueChange = onChange, label = { Text(label) }, singleLine = true)
}

@Composable
internal fun PageControls(count: Int, page: Int, onPage: (Int) -> Unit) {
    if (count == 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 0) { Text("Previous") }
        Text("${page * listPageSize + 1}–${minOf((page + 1) * listPageSize, count)} of $count", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onPage(page + 1) }, enabled = (page + 1) * listPageSize < count) { Text("Next") }
    }
}

@Composable
internal fun HistoryFilter(status: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("Status: ${status.toDisplayName()} ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("all", "active", "awaiting_approval", "completed", "cancelled", "failed", "expired").forEach { option ->
                DropdownMenuItem(text = { Text(option.toDisplayName()) }, onClick = {
                    expanded = false
                    onChange(option)
                })
            }
        }
    }
}
