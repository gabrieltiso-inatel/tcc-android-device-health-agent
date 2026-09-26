package com.tcc.devicehealth.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: AgentViewModel by viewModels {
        AgentViewModelFactory(
            AndroidDeviceHealthRepository(applicationContext, BuildConfig.CONTROLLER_BASE_URL),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActionPollingScheduler.schedule(applicationContext)
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                AgentScreen(
                    state = state,
                    onPairingCodeChange = viewModel::updatePairingCode,
                    onPair = viewModel::pair,
                    onExecuteAction = viewModel::executeAction,
                )
            }
        }
    }
}
