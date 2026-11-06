package com.tcc.devicehealth.agent

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        viewModel.setForeground(true)
    }

    override fun onStop() {
        viewModel.setForeground(false)
        super.onStop()
    }

    private val viewModel: AgentViewModel by viewModels {
        AgentViewModelFactory(
            AndroidDeviceHealthRepository(applicationContext, BuildConfig.CONTROLLER_BASE_URL),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActionPollingScheduler.schedule(applicationContext)
        setContent {
            DeviceHealthTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                var mediaActionId by rememberSaveable { mutableStateOf<String?>(null) }
                val mediaRemovalLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult(),
                ) { result ->
                    val launchFailed = result.data?.hasExtra(ActivityResultContracts.StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION) == true
                    mediaActionId?.let { viewModel.handlePlatformFileRemovalResult(it, result.resultCode == RESULT_OK, launchFailed) }
                    mediaActionId = null
                }
                val removalLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    viewModel.handleApplicationRemovalConfirmation(result.resultCode == RESULT_OK)
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
                        viewModel.onApplicationRemovalConfirmationStarted(approval)
                    }
                }
                LaunchedEffect(state.platformFileRemovalRequest) {
                    state.platformFileRemovalRequest?.let { request ->
                        mediaActionId = request.actionId
                        try {
                            mediaRemovalLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                            viewModel.fileRemovalRequestLaunched()
                        } catch (_: android.content.IntentSender.SendIntentException) {
                            viewModel.handlePlatformFileRemovalResult(request.actionId, false, true)
                        }
                    }
                }
                AgentScreen(
                    state = state,
                    onPairingCodeChange = viewModel::updatePairingCode,
                    onControllerAddressChange = viewModel::updateControllerAddress,
                    onPair = viewModel::pair,
                    onExecuteAction = viewModel::executeAction,
                    onRequestApplicationRemoval = viewModel::requestApplicationRemoval,
                    onCancelApplicationRemoval = viewModel::cancelApplicationRemoval,
                    onRequestFileRemoval = viewModel::requestFileRemoval,
                    onCancelFileRemoval = viewModel::cancelFileRemoval,
                    onDismissFileRemoval = viewModel::dismissFileRemoval,
                    onRemoveApplication = viewModel::removeApplication,
                    onRemoveFile = viewModel::removeFile,
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
                            if (android.os.Build.VERSION.SDK_INT <= 28) {
                                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        mediaPermissionLauncher.launch(permissions)
                    },
                )
            }
        }
    }
}
