package com.tcc.devicehealth.agent

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
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
                val removalLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    viewModel.handleApplicationRemovalResult(result.resultCode == RESULT_OK)
                }
                val folderLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    uri?.let {
                        contentResolver.takePersistableUriPermission(
                            it,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                        viewModel.addFileTreeUri(it.toString())
                    }
                }
                val mediaPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { viewModel.executeAction("collectFileInventory") }
                LaunchedEffect(state.approvalRequest) {
                    state.approvalRequest?.let { approval ->
                        removalLauncher.launch(ApplicationRemovalDataSource.createIntent(approval))
                    }
                }
                AgentScreen(
                    state = state,
                    onPairingCodeChange = viewModel::updatePairingCode,
                    onPair = viewModel::pair,
                    onExecuteAction = viewModel::executeAction,
                    onRequestApplicationRemoval = viewModel::requestApplicationRemoval,
                    onCancelApplicationRemoval = { _ -> viewModel.handleApplicationRemovalResult(false) },
                    onRequestFileRemoval = viewModel::requestFileRemoval,
                    onCancelFileRemoval = { _ -> viewModel.handleFileRemovalResult(false) },
                    onConfirmFileRemoval = { viewModel.handleFileRemovalResult(true) },
                    onCollectFileInventory = { viewModel.executeAction("collectFileInventory") },
                    onSelectFileFolder = { folderLauncher.launch(null) },
                    onRequestMediaAccess = {
                        val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
                            arrayOf(
                                android.Manifest.permission.READ_MEDIA_IMAGES,
                                android.Manifest.permission.READ_MEDIA_VIDEO,
                                android.Manifest.permission.READ_MEDIA_AUDIO,
                            )
                        } else {
                            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        mediaPermissionLauncher.launch(permissions)
                    },
                )
            }
        }
    }
}
