package com.tcc.devicehealth.agent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tcc.devicehealth.agent.data.ActionRepository
import com.tcc.devicehealth.agent.data.ApplicationRepository
import com.tcc.devicehealth.agent.data.DeviceRepository
import com.tcc.devicehealth.agent.data.FileRepository
import com.tcc.devicehealth.agent.model.AppLanguage
import com.tcc.devicehealth.agent.model.ApplicationRemovalDecision
import com.tcc.devicehealth.agent.model.ApplicationRemovalReview
import com.tcc.devicehealth.agent.model.DisplayPreferences
import com.tcc.devicehealth.agent.model.FileItem
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import com.tcc.devicehealth.agent.model.ThemeMode
import com.tcc.devicehealth.agent.sync.ForegroundSync
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentViewModel(
    private val deviceRepository: DeviceRepository,
    private val actionRepository: ActionRepository,
    private val applicationRepository: ApplicationRepository,
    private val fileRepository: FileRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = mutableState.asStateFlow()
    private val foregroundSync = ForegroundSync(ioDispatcher)
    private var isForeground = false
    private var applicationRemovalInProgress: PendingApplicationRemoval? = null

    fun setForeground(isForeground: Boolean) {
        this.isForeground = isForeground
        if (isForeground) {
            startPolling()
        } else {
            foregroundSync.stop()
        }
    }

    init {
        initialize()
    }

    fun updatePairingCode(code: String) {
        mutableState.value = mutableState.value.copy(pairingCode = code.filter(Char::isDigit).take(6))
    }

    fun updateControllerAddress(address: String) {
        mutableState.value = mutableState.value.copy(controllerAddress = address)
    }

    fun pair() {
        val code = mutableState.value.pairingCode
        if (code.length != 6) {
            mutableState.value = mutableState.value.copy(message = AgentMessage.EnterPairingCode)
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            val result = withContext(ioDispatcher) {
                deviceRepository.configureController(mutableState.value.controllerAddress).fold(
                    onSuccess = { deviceRepository.pair(code) },
                    onFailure = { Result.failure(it) },
                )
            }
            if (result.isSuccess) {
                mutableState.value = mutableState.value.copy(isPaired = true, isLoading = false, message = AgentMessage.Paired)
                loadActionHistory()
                loadApplicationApprovals()
                loadFileApprovals()
                startPolling()
            } else {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    message = AgentMessage.PairingFailed,
                )
            }
        }
    }

    fun submitAction(type: String) {
        submitAction(type, null, null)
    }

    fun removeApplication(app: InstalledApp) {
        submitAction("removeApplication", app, null)
    }

    fun removeFile(file: FileItem) {
        submitAction("removeFile", null, file)
    }

    fun onMediaAccessResult(granted: Boolean) {
        if (granted) {
            submitAction("collectFileInventory", null, null, AgentMessage.MediaAccessGranted)
        } else {
            mutableState.value = mutableState.value.copy(message = AgentMessage.MediaAccessDenied)
        }
    }

    private fun submitAction(
        type: String,
        app: InstalledApp?,
        file: FileItem?,
        successMessage: AgentMessage = AgentMessage.ActionFinished(type),
    ) {
        if (state.value.isLoading) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            val result = withContext(ioDispatcher) {
                actionRepository.submitAction(type, app?.applicationId, file?.fileId, file?.revision, app?.versionCode)
            }
            val actions = if (result.isSuccess) {
                actionRepository.getActionHistory().getOrDefault(mutableState.value.actions)
            } else {
                mutableState.value.actions
            }
            mutableState.value = mutableState.value.copy(
                telemetry = deviceRepository.readTelemetry(),
                actions = actions,
                isLoading = false,
                message = result.fold({ successMessage }, { AgentMessage.RequestFailed }),
            )
            loadApplicationApprovals()
            loadFileApprovals()
            loadSnapshots()
        }
    }

    fun addFileTreeUri(uri: String) {
        viewModelScope.launch {
            val result = fileRepository.addFileTreeUri(uri)
            if (result.isSuccess) {
                submitAction("collectFileInventory", null, null, AgentMessage.FolderAdded)
            } else {
                mutableState.value = mutableState.value.copy(message = AgentMessage.FolderFailed)
            }
        }
    }

    fun reviewApplicationRemoval(approval: PendingApplicationRemoval) {
        if (state.value.isLoading) return
        mutableState.value = mutableState.value.copy(isLoading = true, message = null)
        viewModelScope.launch {
            val review = withContext(ioDispatcher) { actionRepository.reviewApplicationRemoval(approval.actionId) }
            if (review.getOrNull() == ApplicationRemovalReview.ConfirmationRequired) {
                mutableState.value = mutableState.value.copy(isLoading = false, applicationConfirmation = approval)
            } else {
                showApplicationRemovalOutcome(approval, review.isSuccess)
            }
        }
    }

    fun onApplicationRemovalConfirmationStarted(approval: PendingApplicationRemoval) {
        applicationRemovalInProgress = approval
        mutableState.value = mutableState.value.copy(applicationConfirmation = null)
    }

    fun handleApplicationRemovalConfirmation(accepted: Boolean) {
        val approval = applicationRemovalInProgress ?: return
        applicationRemovalInProgress = null
        val decision = if (accepted) {
            ApplicationRemovalDecision.ConfirmationAccepted
        } else {
            ApplicationRemovalDecision.ConfirmationDismissed
        }
        completeApplicationRemoval(approval, decision)
    }

    fun cancelApplicationRemoval(approval: PendingApplicationRemoval) {
        if (state.value.isLoading) return
        completeApplicationRemoval(approval, ApplicationRemovalDecision.Declined)
    }

    private fun completeApplicationRemoval(approval: PendingApplicationRemoval, decision: ApplicationRemovalDecision) {
        mutableState.value = mutableState.value.copy(isLoading = true, message = null)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { actionRepository.completeApplicationRemoval(approval.actionId, decision) }
            showApplicationRemovalOutcome(approval, result.isSuccess)
        }
    }

    fun reviewFileRemoval(approval: PendingFileRemoval) {
        val name = approval.name ?: state.value.snapshots.files?.files?.find { it.fileId == approval.fileId }?.name
        mutableState.value = mutableState.value.copy(fileConfirmation = approval.copy(name = name), message = null)
    }

    fun dismissFileRemoval() {
        mutableState.value = mutableState.value.copy(fileConfirmation = null)
    }

    fun cancelFileRemoval(approval: PendingFileRemoval) {
        completeFileRemoval(approval, false)
    }

    fun handleFileRemovalResult(approved: Boolean) {
        val approval = mutableState.value.fileConfirmation ?: return
        completeFileRemoval(approval, approved)
    }

    private fun completeFileRemoval(approval: PendingFileRemoval, approved: Boolean) {
        if (state.value.isLoading) return
        mutableState.value = mutableState.value.copy(fileConfirmation = null, isLoading = true)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                actionRepository.completeFileRemoval(approval.actionId, approved)
            }
            mutableState.value = mutableState.value.copy(platformFileConfirmation = result.getOrNull())
            loadActionHistory()
            loadFileApprovals()
            loadSnapshots()
            mutableState.value = mutableState.value.copy(
                telemetry = deviceRepository.readTelemetry(),
                isLoading = false,
                message = result.fold({ if (it != null) AgentMessage.ConfirmInAndroidDialog else AgentMessage.FileActionRecorded }, { AgentMessage.FileActionFailed }),
            )
        }
    }

    fun onFileConfirmationLaunched() {
        mutableState.value = mutableState.value.copy(platformFileConfirmation = null)
    }

    fun handlePlatformFileRemovalResult(actionId: String, approved: Boolean, launchFailed: Boolean = false) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, platformFileConfirmation = null)
            val result = withContext(ioDispatcher) { actionRepository.completePlatformFileRemoval(actionId, approved, launchFailed) }
            loadActionHistory()
            loadFileApprovals()
            loadSnapshots()
            mutableState.value = mutableState.value.copy(isLoading = false,
                message = result.fold({ AgentMessage.FileActionRecorded }, { AgentMessage.FileActionWillRetry }))
        }
    }

    private suspend fun showApplicationRemovalOutcome(approval: PendingApplicationRemoval, succeeded: Boolean) {
        val applicationName = state.value.snapshots.apps?.apps?.find { it.applicationId == approval.applicationId }?.name
            ?: approval.applicationId
        loadActionHistory()
        loadApplicationApprovals()
        loadSnapshots()
        val status = state.value.actions.find { it.id == approval.actionId }?.status
        mutableState.value = mutableState.value.copy(
            telemetry = deviceRepository.readTelemetry(),
            isLoading = false,
            message = when {
                !succeeded -> AgentMessage.ApplicationActionFailed
                status != null -> AgentMessage.ApplicationRemovalFinished(applicationName, status)
                else -> AgentMessage.ApplicationActionRecorded
            },
        )
    }

    fun messageShown(message: AgentMessage) {
        if (state.value.message == message) {
            mutableState.value = mutableState.value.copy(message = null)
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        saveDisplayPreferences(state.value.displayPreferences.copy(themeMode = themeMode))
    }

    fun updateLanguage(language: AppLanguage) {
        saveDisplayPreferences(state.value.displayPreferences.copy(language = language))
    }

    fun updatePermissions(permissions: PermissionStatus) {
        mutableState.value = mutableState.value.copy(permissions = permissions)
    }

    private fun saveDisplayPreferences(displayPreferences: DisplayPreferences) {
        val previous = state.value.displayPreferences
        mutableState.value = mutableState.value.copy(displayPreferences = displayPreferences)
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { deviceRepository.saveDisplayPreferences(displayPreferences) }
            if (result.isFailure) {
                mutableState.value = mutableState.value.copy(displayPreferences = previous, message = AgentMessage.PreferencesFailed)
            }
        }
    }

    private fun startPolling() {
        if (!isForeground || !state.value.isPaired) return
        foregroundSync.start(
            scope = viewModelScope,
            processPendingActions = actionRepository::processPendingActions,
            onActionsProcessed = { processedCount ->
                loadActionHistory()
                loadApplicationApprovals()
                loadFileApprovals()
                loadSnapshots()
                mutableState.value = mutableState.value.copy(
                    telemetry = deviceRepository.readTelemetry(),
                    message = AgentMessage.ActionsProcessed(processedCount),
                )
            },
            syncTelemetry = {
                val result = deviceRepository.sendTelemetry()
                val telemetry = deviceRepository.readTelemetry()
                withContext(Dispatchers.Main) {
                    mutableState.value = mutableState.value.copy(telemetry = telemetry)
                }
                result
            },
            syncStorage = deviceRepository::syncStorageSummary,
            syncApps = applicationRepository::syncApplications,
            refreshScreen = {
                withContext(Dispatchers.Main) {
                    loadActionHistory()
                    loadApplicationApprovals()
                    loadFileApprovals()
                    loadSnapshots()
                }
            },
        )
    }

    private fun initialize() {
        viewModelScope.launch {
            val telemetry = deviceRepository.readTelemetry()
            val isPaired = deviceRepository.isPaired()
            mutableState.value = mutableState.value.copy(
                displayPreferences = deviceRepository.getDisplayPreferences(),
                telemetry = telemetry,
                isReady = true,
                isPaired = isPaired,
                controllerAddress = deviceRepository.getControllerAddress(),
            )
            if (isPaired) {
                loadActionHistory()
                loadApplicationApprovals()
                loadFileApprovals()
                startPolling()
            }
        }
    }

    private suspend fun loadActionHistory() {
        actionRepository.getActionHistory().getOrNull()?.let { actions ->
            mutableState.value = mutableState.value.copy(actions = actions)
        }
    }

    private suspend fun loadSnapshots(): Result<Unit> {
        val result = deviceRepository.getSnapshots()
        mutableState.value = mutableState.value.copy(
            snapshots = result.getOrDefault(state.value.snapshots),
            isShowingSavedReadings = result.isFailure,
        )
        return result.map { Unit }
    }

    private suspend fun loadApplicationApprovals() {
        actionRepository.getPendingApplicationRemovals().getOrNull()?.let { approvals ->
            mutableState.value = mutableState.value.copy(pendingApplicationRemovals = approvals)
        }
    }

    private suspend fun loadFileApprovals() {
        actionRepository.getPendingFileRemovals().getOrNull()?.let { approvals ->
            mutableState.value = mutableState.value.copy(pendingFileRemovals = approvals)
        }
    }
}

class AgentViewModelFactory(
    private val deviceRepository: DeviceRepository,
    private val actionRepository: ActionRepository,
    private val applicationRepository: ApplicationRepository,
    private val fileRepository: FileRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AgentViewModel(deviceRepository, actionRepository, applicationRepository, fileRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
