package com.tcc.devicehealth.agent

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
        val repository = FakeDeviceHealthRepository()
        val viewModel = AgentViewModel(repository, dispatcher)
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
        val repository = FakeDeviceHealthRepository()
        val viewModel = AgentViewModel(repository, dispatcher)
        runCurrent()

        viewModel.cancelFileRemoval(PendingFileApproval("file-action", "file", "revision", "Notes.txt"))
        runCurrent()

        assertEquals("file-action" to false, repository.fileDecision)
        assertNull(viewModel.state.value.fileApprovalRequest)
    }

    @Test
    fun decliningAnApplicationTargetsTheSelectedApproval() = runTest(dispatcher) {
        val repository = FakeDeviceHealthRepository()
        val viewModel = AgentViewModel(repository, dispatcher)
        runCurrent()

        viewModel.cancelApplicationRemoval(PendingApproval("app-action", "app", 7))
        runCurrent()

        assertEquals("app-action" to ApplicationRemovalDecision.Declined, repository.applicationDecision)
    }

    @Test
    fun reviewingAValidApplicationOpensTheAndroidConfirmation() = runTest(dispatcher) {
        val repository = FakeDeviceHealthRepository()
        val viewModel = AgentViewModel(repository, dispatcher)
        runCurrent()
        val approval = PendingApproval("app-action", "app", 7)

        viewModel.requestApplicationRemoval(approval)
        runCurrent()

        assertEquals(approval, viewModel.state.value.approvalRequest)
        assertNull(repository.applicationDecision)
    }

    @Test
    fun reviewThatAlreadyFinishedTheRemovalDoesNotOpenTheAndroidConfirmation() = runTest(dispatcher) {
        val repository = FakeDeviceHealthRepository(review = ApplicationRemovalReview.Completed)
        val viewModel = AgentViewModel(repository, dispatcher)
        runCurrent()

        viewModel.requestApplicationRemoval(PendingApproval("app-action", "app", 7))
        runCurrent()

        assertNull(viewModel.state.value.approvalRequest)
        assertEquals("app-action", repository.reviewedActionId)
    }

    @Test
    fun androidConfirmationResultIsRecordedForTheStartedApproval() = runTest(dispatcher) {
        val repository = FakeDeviceHealthRepository()
        val viewModel = AgentViewModel(repository, dispatcher)
        runCurrent()
        val approval = PendingApproval("app-action", "app", 7)
        viewModel.requestApplicationRemoval(approval)
        runCurrent()

        viewModel.onApplicationRemovalConfirmationStarted(approval)
        viewModel.handleApplicationRemovalConfirmation(accepted = true)
        runCurrent()

        assertNull(viewModel.state.value.approvalRequest)
        assertEquals("app-action" to ApplicationRemovalDecision.ConfirmationAccepted, repository.applicationDecision)
    }

    @Test
    fun dismissingFileReviewDoesNotDeclineTheAction() = runTest(dispatcher) {
        val repository = FakeDeviceHealthRepository()
        val viewModel = AgentViewModel(repository, dispatcher)
        runCurrent()
        viewModel.requestFileRemoval(PendingFileApproval("file-action", "file", "revision", "Notes.txt"))

        viewModel.dismissFileRemoval()
        runCurrent()

        assertNull(repository.fileDecision)
        assertNull(viewModel.state.value.fileApprovalRequest)
    }

    @Test
    fun displayPreferencesAreLoadedAndSaved() = runTest(dispatcher) {
        val repository = FakeDeviceHealthRepository()
        repository.displayPreferences = DisplayPreferences(themeMode = ThemeMode.Dark)
        val viewModel = AgentViewModel(repository, dispatcher)
        runCurrent()

        viewModel.updateLanguage(AppLanguage.Portuguese)
        runCurrent()

        assertEquals(DisplayPreferences(ThemeMode.Dark, AppLanguage.Portuguese), viewModel.state.value.displayPreferences)
        assertEquals(DisplayPreferences(ThemeMode.Dark, AppLanguage.Portuguese), repository.displayPreferences)
    }

    @Test
    fun shownMessageIsCleared() = runTest(dispatcher) {
        val viewModel = AgentViewModel(FakeDeviceHealthRepository(), dispatcher)
        runCurrent()
        viewModel.pair()
        val message = checkNotNull(viewModel.state.value.message)

        viewModel.messageShown(message)

        assertNull(viewModel.state.value.message)
    }

    @Test
    fun localRemovalPreservesTheObservedVersionAndRevision() = runTest(dispatcher) {
        val repository = FakeDeviceHealthRepository()
        val viewModel = AgentViewModel(repository, dispatcher)
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
        val repository = FakeDeviceHealthRepository()
        val viewModel = AgentViewModel(repository, dispatcher)
        runCurrent()

        viewModel.setForeground(true)
        runCurrent()
        viewModel.setForeground(false)

        assertEquals(repository.snapshots, viewModel.state.value.snapshots)
        assertNull(repository.request)
    }
}

private class FakeDeviceHealthRepository(
    private val review: ApplicationRemovalReview = ApplicationRemovalReview.ConfirmationRequired,
) : DeviceHealthRepository {
    var platformDecision: Triple<String, Boolean, Boolean>? = null
    var fileDecision: Pair<String, Boolean>? = null
    var applicationDecision: Pair<String, ApplicationRemovalDecision>? = null
    var reviewedActionId: String? = null
    var displayPreferences = DisplayPreferences()
    var request: DeviceAction? = null
    val snapshots = DeviceSnapshots(storage = StorageSummary(100, 40, 60, "2026-10-31T12:00:00Z"))

    override suspend fun readTelemetry() = DeviceTelemetry("device", "Phone", "Google", "Pixel", "14", 34, "0.1", listOf("removeFile", "removeApplication"), 80, false, "2026-10-31T12:00:00Z")
    override suspend fun isPaired() = true
    override suspend fun pair(code: String) = Result.success(Unit)
    override suspend fun getControllerAddress() = "http://10.0.2.2:3000"
    override suspend fun configureController(address: String) = Result.success(Unit)
    override suspend fun sendTelemetry() = Result.success(Unit)
    override suspend fun syncStorageSummary() = Result.success(Unit)
    override suspend fun syncAppInventory() = Result.success(Unit)
    override suspend fun syncFileInventory() = Result.success(Unit)
    override suspend fun addFileTreeUri(uri: String) = Result.success(Unit)
    override suspend fun checkActions() = Result.success(0)
    override suspend fun getActionHistory() = Result.success(emptyList<DeviceAction>())
    override suspend fun getSnapshots() = Result.success(snapshots)
    override suspend fun getDisplayPreferences() = displayPreferences
    override suspend fun saveDisplayPreferences(displayPreferences: DisplayPreferences): Result<Unit> {
        this.displayPreferences = displayPreferences
        return Result.success(Unit)
    }
    override suspend fun getPendingApprovals() = Result.success(emptyList<PendingApproval>())
    override suspend fun getPendingFileApprovals() = Result.success(emptyList<PendingFileApproval>())

    override suspend fun executeDeviceAction(type: String, applicationId: String?, fileId: String?, expectedRevision: String?, expectedVersionCode: Long?): Result<Unit> {
        request = DeviceAction("action", type, applicationId, expectedVersionCode, fileId, expectedRevision, "device", "delivered", "2026-10-31T12:00:00Z")
        return Result.success(Unit)
    }

    override suspend fun completeFileRemoval(actionId: String, approved: Boolean): Result<FileRemovalRequest?> {
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
