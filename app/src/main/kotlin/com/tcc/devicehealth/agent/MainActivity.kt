package com.tcc.devicehealth.agent

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.DisposableEffect
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

    override fun onResume() {
        super.onResume()
        refreshPermissions()
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
            val state by viewModel.state.collectAsStateWithLifecycle()
            val isDark = state.displayPreferences.themeMode.isDark()
            DisposableEffect(isDark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            DeviceHealthTheme(state.displayPreferences.themeMode) {
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
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    refreshPermissions()
                    if (!granted && !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                        openNotificationSettings()
                    }
                }
                LaunchedEffect(state.isPaired) {
                    if (state.isPaired && needsNotificationPermission()) {
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
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
                ) { results ->
                    refreshPermissions()
                    viewModel.onMediaAccessResult(results.values.any { it })
                }
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
                LocalizedContent(state.displayPreferences.language) {
                    AgentScreen(
                        state = state,
                        onThemeModeChange = viewModel::updateThemeMode,
                        onLanguageChange = viewModel::updateLanguage,
                        onMessageShown = viewModel::messageShown,
                        onRequestNotificationPermission = {
                            if (needsNotificationPermission()) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openNotificationSettings()
                            }
                        },
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

    private fun refreshPermissions() {
        val notificationsEnabled = getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled() &&
            !needsNotificationPermission()
        val mediaPermission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        viewModel.updatePermissions(
            PermissionStatus(
                notificationsGranted = notificationsEnabled,
                mediaGranted = checkSelfPermission(mediaPermission) == PackageManager.PERMISSION_GRANTED,
            ),
        )
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun needsNotificationPermission(): Boolean = android.os.Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
}
