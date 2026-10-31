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

        assertEquals("app-action" to false, repository.applicationDecision)
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

private class FakeDeviceHealthRepository : DeviceHealthRepository {
    var fileDecision: Pair<String, Boolean>? = null
    var applicationDecision: Pair<String, Boolean>? = null
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
    override suspend fun getPendingApprovals() = Result.success(emptyList<PendingApproval>())
    override suspend fun getPendingFileApprovals() = Result.success(emptyList<PendingFileApproval>())

    override suspend fun executeDeviceAction(type: String, applicationId: String?, fileId: String?, expectedRevision: String?, expectedVersionCode: Long?): Result<Unit> {
        request = DeviceAction("action", type, applicationId, expectedVersionCode, fileId, expectedRevision, "device", "delivered", "2026-10-31T12:00:00Z")
        return Result.success(Unit)
    }

    override suspend fun completeFileRemoval(actionId: String, approved: Boolean): Result<Unit> {
        fileDecision = actionId to approved
        return Result.success(Unit)
    }

    override suspend fun completeApplicationRemoval(actionId: String, approved: Boolean): Result<Unit> {
        applicationDecision = actionId to approved
        return Result.success(Unit)
    }
}
