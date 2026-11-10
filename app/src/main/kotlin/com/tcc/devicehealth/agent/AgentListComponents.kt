package com.tcc.devicehealth.agent

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun SectionHeader(title: String, supporting: String? = null, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

@Composable
internal fun ListRow(
    title: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Icon(painterResource(it), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.let {
            Spacer(Modifier.size(12.dp))
            it()
        }
    }
}

@Composable
internal fun RowDivider() {
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun EmptyState(title: String, message: String) {
    SurfaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StatusBadge(status: String) {
    val statusColors = LocalStatusColors.current
    val colors = when (status) {
        "completed" -> statusColors.successContainer to statusColors.onSuccessContainer
        "pending", "delivered", "awaiting_approval" -> statusColors.warningContainer to statusColors.onWarningContainer
        "failed", "expired" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = colors.first, contentColor = colors.second, shape = MaterialTheme.shapes.small) {
        Text(stringResource(statusLabel(status)), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun SearchField(query: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun PageControls(count: Int, page: Int, onPage: (Int) -> Unit) {
    if (count <= listPageSize) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 0) { Text(stringResource(R.string.page_previous)) }
        Text(
            stringResource(R.string.page_range, page * listPageSize + 1, minOf((page + 1) * listPageSize, count), count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { onPage(page + 1) }, enabled = (page + 1) * listPageSize < count) { Text(stringResource(R.string.page_next)) }
    }
}

@Composable
internal fun relativeTimeText(value: String): String {
    val resources = LocalContext.current.resources
    return when (val time = relativeTime(value)) {
        null -> value
        RelativeTime.JustNow -> stringResource(R.string.time_just_now)
        is RelativeTime.MinutesAgo -> resources.getQuantityString(R.plurals.time_minutes_ago, time.value, time.value)
        is RelativeTime.HoursAgo -> resources.getQuantityString(R.plurals.time_hours_ago, time.value, time.value)
        is RelativeTime.DaysAgo -> resources.getQuantityString(R.plurals.time_days_ago, time.value, time.value)
    }
}

@Composable
internal fun bytesText(bytes: Long): String = formatBytes(bytes, LocalConfiguration.current.locales[0])

@Composable
internal fun countText(id: Int, zeroId: Int, count: Int): String = if (count == 0) stringResource(zeroId) else quantityText(id, count)

@Composable
internal fun quantityText(id: Int, count: Int): String = LocalContext.current.resources.getQuantityString(id, count, count)
