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
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
        CommandPollingScheduler.schedule(applicationContext)
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                AgentScreen(
                    state = state,
                    onPairingCodeChange = viewModel::updatePairingCode,
                    onPair = viewModel::pair,
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun AgentScreen(
    state: AgentUiState,
    onPairingCodeChange: (String) -> Unit,
    onPair: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Device Health Agent", style = MaterialTheme.typography.headlineMedium)
        if (!state.isReady) {
            Text("Loading...")
        } else if (state.isPaired) {
            DeviceTelemetryCard(state.telemetry)
        } else {
            PairingCard(
                code = state.pairingCode,
                isLoading = state.isLoading,
                onCodeChange = onPairingCodeChange,
                onPair = onPair,
            )
        }
        state.message?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@androidx.compose.runtime.Composable
private fun PairingCard(
    code: String,
    isLoading: Boolean,
    onCodeChange: (String) -> Unit,
    onPair: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Connect to controller", style = MaterialTheme.typography.titleMedium)
            Text("Enter the six-digit code shown by the controller.")
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("Pairing code") },
                singleLine = true,
                enabled = !isLoading,
            )
            Button(onClick = onPair, enabled = code.length == 6 && !isLoading) {
                Text(if (isLoading) "Connecting..." else "Connect")
            }
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
