package com.tcc.devicehealth.agent

import android.content.IntentSender
import com.tcc.devicehealth.agent.data.ActionRepository
import com.tcc.devicehealth.agent.data.ApplicationRepository
import com.tcc.devicehealth.agent.data.DeviceRepository
import com.tcc.devicehealth.agent.data.FileRepository
import com.tcc.devicehealth.agent.data.android.AndroidFileRemovalConfirmation
import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.AppLanguage
import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.ApplicationRemovalDecision
import com.tcc.devicehealth.agent.model.ApplicationRemovalReview
import com.tcc.devicehealth.agent.model.DeviceTelemetry
import com.tcc.devicehealth.agent.model.DisplayPreferences
import com.tcc.devicehealth.agent.model.FileInventory
import com.tcc.devicehealth.agent.model.FileItem
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.model.LatestDeviceSnapshots
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import com.tcc.devicehealth.agent.model.StorageSummary
import com.tcc.devicehealth.agent.model.ThemeMode
import com.tcc.devicehealth.agent.ui.AgentMessage
import com.tcc.devicehealth.agent.ui.AgentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun platformCancellationAndLaunchFailureAreForwardedWithTheirActionId() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()

        viewModel.handlePlatformFileRemovalResult("media-action", false)
        runCurrent()
        assertEquals(Triple("media-action", false, false), repository.platformDecision)

        viewModel.handlePlatformFileRemovalResult("failed-launch", false, true)
        runCurrent()
        assertEquals(Triple("failed-launch", false, true), repository.platformDecision)
    }

    @Test
    fun decliningAFileDoesNotRequireOpeningItsReview() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()

        viewModel.cancelFileRemoval(PendingFileRemoval("file-action", "file", "revision", "Notes.txt"))
        runCurrent()

        assertEquals("file-action" to false, repository.fileDecision)
        assertNull(viewModel.state.value.fileConfirmation)
    }

    @Test
    fun decliningAnApplicationTargetsTheSelectedApproval() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()

        viewModel.cancelApplicationRemoval(PendingApplicationRemoval("app-action", "app", 7))
        runCurrent()

        assertEquals("app-action" to ApplicationRemovalDecision.Declined, repository.applicationDecision)
    }

    @Test
    fun reviewingAValidApplicationOpensTheAndroidConfirmation() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()
        val approval = PendingApplicationRemoval("app-action", "app", 7)

        viewModel.reviewApplicationRemoval(approval)
        runCurrent()

        assertEquals(approval, viewModel.state.value.applicationConfirmation)
        assertNull(repository.applicationDecision)
    }

    @Test
    fun reviewThatAlreadyFinishedTheRemovalDoesNotOpenTheAndroidConfirmation() = runTest(dispatcher) {
        val repository = FakeAgentRepositories(review = ApplicationRemovalReview.Completed)
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()

        viewModel.reviewApplicationRemoval(PendingApplicationRemoval("app-action", "app", 7))
        runCurrent()

        assertNull(viewModel.state.value.applicationConfirmation)
        assertEquals("app-action", repository.reviewedActionId)
    }

    @Test
    fun androidConfirmationResultIsRecordedForTheStartedApproval() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()
        val approval = PendingApplicationRemoval("app-action", "app", 7)
        viewModel.reviewApplicationRemoval(approval)
        runCurrent()

        viewModel.onApplicationRemovalConfirmationStarted(approval)
        viewModel.handleApplicationRemovalConfirmation(accepted = true)
        runCurrent()

        assertNull(viewModel.state.value.applicationConfirmation)
        assertEquals("app-action" to ApplicationRemovalDecision.ConfirmationAccepted, repository.applicationDecision)
    }

    @Test
    fun dismissingFileReviewDoesNotDeclineTheAction() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()
        viewModel.reviewFileRemoval(PendingFileRemoval("file-action", "file", "revision", "Notes.txt"))

        viewModel.dismissFileRemoval()
        runCurrent()

        assertNull(repository.fileDecision)
        assertNull(viewModel.state.value.fileConfirmation)
    }

    @Test
    fun displayPreferencesAreLoadedAndSaved() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        repository.displayPreferences = DisplayPreferences(themeMode = ThemeMode.Dark)
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()

        viewModel.updateLanguage(AppLanguage.Portuguese)
        runCurrent()

        assertEquals(DisplayPreferences(ThemeMode.Dark, AppLanguage.Portuguese), viewModel.state.value.displayPreferences)
        assertEquals(DisplayPreferences(ThemeMode.Dark, AppLanguage.Portuguese), repository.displayPreferences)
    }

    @Test
    fun shownMessageIsCleared() = runTest(dispatcher) {
        val viewModel = FakeAgentRepositories().let { AgentViewModel(it, it, it, it, dispatcher) }
        runCurrent()
        viewModel.pair()
        val message = checkNotNull(viewModel.state.value.message)

        viewModel.messageShown(message)

        assertNull(viewModel.state.value.message)
    }

    @Test
    fun deniedMediaAccessDoesNotRequestAFileUpdate() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()

        viewModel.onMediaAccessResult(granted = false)
        runCurrent()

        assertNull(repository.request)
        assertEquals(AgentMessage.MediaAccessDenied, viewModel.state.value.message)
    }

    @Test
    fun grantedMediaAccessUpdatesTheFileList() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()

        viewModel.onMediaAccessResult(granted = true)
        runCurrent()

        assertEquals("collectFileInventory", repository.request?.type)
        assertEquals(AgentMessage.MediaAccessGranted, viewModel.state.value.message)
    }

    @Test
    fun addingAFolderUpdatesTheFileList() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()

        viewModel.addFileTreeUri("content://folder")
        runCurrent()

        assertEquals("collectFileInventory", repository.request?.type)
        assertEquals(AgentMessage.FolderAdded, viewModel.state.value.message)
    }

    @Test
    fun updateRequestsReportWhatWasUpdated() = runTest(dispatcher) {
        val viewModel = FakeAgentRepositories().let { AgentViewModel(it, it, it, it, dispatcher) }
        runCurrent()

        viewModel.submitAction("collectAppInventory")
        runCurrent()

        assertEquals(AgentMessage.ActionFinished("collectAppInventory"), viewModel.state.value.message)
    }

    @Test
    fun localRemovalPreservesTheObservedVersionAndRevision() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()
        viewModel.removeApplication(InstalledApp("app", "Notes", true, "1.0", 7))
        runCurrent()
        assertEquals("removeApplication", repository.request?.type)
        assertEquals("app", repository.request?.applicationId)
        assertEquals(7L, repository.request?.expectedVersionCode)

        viewModel.removeFile(FileItem("file", "source", "Notes.txt", "document", sizeBytes = 10, canRemove = true, revision = "revision"))
        runCurrent()
        assertEquals("removeFile", repository.request?.type)
        assertEquals("file", repository.request?.fileId)
        assertEquals("revision", repository.request?.expectedRevision)
    }

    @Test
    fun automaticScreenRefreshDoesNotCreateActions() = runTest(dispatcher) {
        val repository = FakeAgentRepositories()
        val viewModel = AgentViewModel(repository, repository, repository, repository, dispatcher)
        runCurrent()

        viewModel.setForeground(true)
        runCurrent()
        viewModel.setForeground(false)

        assertEquals(repository.snapshots, viewModel.state.value.snapshots)
        assertNull(repository.request)
    }
}

internal class FakeAgentRepositories(
    private val review: ApplicationRemovalReview = ApplicationRemovalReview.ConfirmationRequired,
) : DeviceRepository, ActionRepository, ApplicationRepository, FileRepository {
    var platformDecision: Triple<String, Boolean, Boolean>? = null
    var fileDecision: Pair<String, Boolean>? = null
    var applicationDecision: Pair<String, ApplicationRemovalDecision>? = null
    var reviewedActionId: String? = null
    var displayPreferences = DisplayPreferences()
    var request: Action? = null
    val snapshots = LatestDeviceSnapshots(storage = StorageSummary(100, 40, 60, "2026-10-31T12:00:00Z"))

    override suspend fun readTelemetry() = DeviceTelemetry("device", "Phone", "Google", "Pixel", "14", 34, "0.1", listOf("removeFile", "removeApplication"), 80, false, "2026-10-31T12:00:00Z")
    override suspend fun isPaired() = true
    override suspend fun pair(code: String) = Result.success(Unit)
    override suspend fun getControllerAddress() = "http://10.0.2.2:3000"
    override suspend fun configureController(address: String) = Result.success(Unit)
    override suspend fun sendTelemetry() = Result.success(Unit)
    var applicationInventory = ApplicationInventory("2026-10-31T12:00:00Z", emptyList())
    var fileInventory = FileInventory("2026-10-31T12:00:00Z", emptyList(), emptyList())
    override suspend fun collectStorage() = checkNotNull(snapshots.storage)
    override suspend fun collectApplications() = applicationInventory
    override suspend fun collectFiles() = fileInventory
    override suspend fun validateApplicationRemoval(action: Action) = Unit
    override suspend fun validateApplicationRemoval(approval: PendingApplicationRemoval) = Unit
    override suspend fun isApplicationRemoved(approval: PendingApplicationRemoval) = false
    override suspend fun validateFileRemoval(action: Action) = Unit
    override suspend fun createFileApproval(action: Action) = PendingFileRemoval(action.id, checkNotNull(action.fileId), action.expectedRevision)
    override suspend fun removeFile(approval: PendingFileRemoval): IntentSender? = null
    override suspend fun finishMediaRemoval(approval: PendingFileRemoval) = Unit
    override suspend fun isMediaAbsent(approval: PendingFileRemoval) = false
    override suspend fun syncStorageSummary() = Result.success(Unit)
    override suspend fun syncApplications() = Result.success(Unit)
    override suspend fun syncFiles() = Result.success(Unit)
    override suspend fun addFileTreeUri(uri: String) = Result.success(Unit)
    override suspend fun processPendingActions() = Result.success(0)
    override suspend fun getActionHistory() = Result.success(emptyList<Action>())
    override suspend fun getSnapshots() = Result.success(snapshots)
    override suspend fun getDisplayPreferences() = displayPreferences
    override suspend fun saveDisplayPreferences(displayPreferences: DisplayPreferences): Result<Unit> {
        this.displayPreferences = displayPreferences
        return Result.success(Unit)
    }
    override suspend fun getPendingApplicationRemovals() = Result.success(emptyList<PendingApplicationRemoval>())
    override suspend fun getPendingFileRemovals() = Result.success(emptyList<PendingFileRemoval>())

    override suspend fun submitAction(type: String, applicationId: String?, fileId: String?, expectedRevision: String?, expectedVersionCode: Long?): Result<Unit> {
        request = Action("action", type, applicationId, expectedVersionCode, fileId, expectedRevision, "device", "delivered", "2026-10-31T12:00:00Z")
        return Result.success(Unit)
    }

    override suspend fun completeFileRemoval(actionId: String, approved: Boolean): Result<AndroidFileRemovalConfirmation?> {
        fileDecision = actionId to approved
        return Result.success(null)
    }

    override suspend fun completePlatformFileRemoval(actionId: String, approved: Boolean, launchFailed: Boolean): Result<Unit> {
        platformDecision = Triple(actionId, approved, launchFailed)
        return Result.success(Unit)
    }

    override suspend fun reviewApplicationRemoval(actionId: String): Result<ApplicationRemovalReview> {
        reviewedActionId = actionId
        return Result.success(review)
    }

    override suspend fun completeApplicationRemoval(actionId: String, decision: ApplicationRemovalDecision): Result<Unit> {
        applicationDecision = actionId to decision
        return Result.success(Unit)
    }
}
