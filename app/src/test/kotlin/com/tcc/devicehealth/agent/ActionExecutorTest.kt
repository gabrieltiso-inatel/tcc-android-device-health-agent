package com.tcc.devicehealth.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionExecutorTest {
    @Test
    fun fileRemovalActionRequiresApproval() = runBlocking {
        val executor = ActionExecutor(
            sendTelemetry = {},
            collectStorageSummary = { ActionExecution("Storage summary collected") },
            collectAppInventory = { ActionExecution("App inventory collected") },
            prepareFileRemoval = { ActionExecution("File removal requires approval", approvalRequired = true) },
        )

        val result = executor.execute(
            DeviceAction(
                id = "action-file-removal-1",
                type = "removeFile",
                fileId = "opaque-file-id",
                expectedRevision = "opaque-revision",
                origin = "controller",
                status = "delivered",
                requestedAt = "2026-09-27T12:00:00Z",
            ),
        )

        assertEquals(true, result.approvalRequired)
    }

    @Test
    fun fileInventoryActionReturnsTheCollectedInventory() = runBlocking {
        val expected = ActionExecution("File inventory collected", """{"files":[],"sources":[]}""")
        val executor = ActionExecutor(
            sendTelemetry = {},
            collectStorageSummary = { ActionExecution("Storage summary collected") },
            collectAppInventory = { ActionExecution("App inventory collected") },
            collectFileInventory = { expected },
        )

        val result = executor.execute(
            DeviceAction(
                id = "action-file-1",
                type = "collectFileInventory",
                origin = "device",
                status = "delivered",
                requestedAt = "2026-09-27T12:00:00Z",
            ),
        )

        assertEquals(expected, result)
    }

    @Test
    fun appInventoryActionReturnsTheCollectedInventory() = runBlocking {
        val expected = ActionExecution("App inventory collected", """{"apps":[]}""")
        val executor = ActionExecutor(
            sendTelemetry = {},
            collectStorageSummary = { ActionExecution("Storage summary collected") },
            collectAppInventory = { expected },
        )

        val result = executor.execute(
            DeviceAction(
                id = "action-1",
                type = "collectAppInventory",
                origin = "device",
                status = "delivered",
                requestedAt = "2026-09-26T12:00:00Z",
            ),
        )

        assertEquals(expected, result)
    }
}
