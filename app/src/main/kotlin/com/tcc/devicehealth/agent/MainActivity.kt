package com.tcc.devicehealth.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: AgentViewModel by viewModels {
        AgentViewModelFactory(
            AndroidDeviceHealthRepository(applicationContext, BuildConfig.CONTROLLER_BASE_URL),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                AgentScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onSync = viewModel::sync,
                    onCheckCommands = viewModel::checkCommands,
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun AgentScreen(
    state: AgentUiState,
    onRefresh: () -> Unit,
    onSync: () -> Unit,
    onCheckCommands: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Device Health Agent", style = MaterialTheme.typography.headlineMedium)
        DeviceTelemetryCard(state.telemetry)
        Button(onClick = onRefresh, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh local data")
        }
        Button(onClick = onSync, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isLoading) "Working..." else "Sync telemetry")
        }
        Button(onClick = onCheckCommands, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Check controller commands")
        }
        state.message?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@androidx.compose.runtime.Composable
private fun DeviceTelemetryCard(telemetry: DeviceTelemetry?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Device", style = MaterialTheme.typography.titleMedium)
            Text(telemetry?.deviceName ?: "Loading...")
            Text("Battery: ${telemetry?.batteryPercentage ?: 0}%")
            Text(if (telemetry?.isCharging == true) "Charging" else "Discharging")
            Text("Captured: ${telemetry?.capturedAt ?: "-"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
