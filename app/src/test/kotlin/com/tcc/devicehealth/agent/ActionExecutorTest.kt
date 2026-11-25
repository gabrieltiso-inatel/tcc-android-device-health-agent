package com.tcc.devicehealth.agent

import com.tcc.devicehealth.agent.actions.ActionExecutor
import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.ActionExecutionResult
import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.FileInventory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionExecutorTest {
    @Test
    fun fileRemovalActionRequiresApproval() = runBlocking {
        val repositories = FakeAgentRepositories()
        val executor = ActionExecutor(repositories, repositories, repositories)

        val result = executor.execute(
            Action(
                id = "action-file-removal-1",
                type = "removeFile",
                fileId = "opaque-file-id",
                expectedRevision = "opaque-revision",
                origin = "controller",
                status = "delivered",
                requestedAt = "2026-09-27T12:00:00Z",
            ),
        )

        assertEquals("awaiting_approval", result.status)
    }

    @Test
    fun fileInventoryActionReturnsTheCollectedInventory() = runBlocking {
        val inventory = FileInventory("2026-10-31T12:00:00Z", emptyList(), emptyList())
        val expected = ActionExecutionResult(succeeded = true, message = "File inventory collected", collectedData = inventory)
        val repositories = FakeAgentRepositories().apply { fileInventory = inventory }
        val executor = ActionExecutor(repositories, repositories, repositories)

        val result = executor.execute(
            Action(
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
        val inventory = ApplicationInventory("2026-10-31T12:00:00Z", emptyList())
        val expected = ActionExecutionResult(succeeded = true, message = "App inventory collected", collectedData = inventory)
        val repositories = FakeAgentRepositories().apply { applicationInventory = inventory }
        val executor = ActionExecutor(repositories, repositories, repositories)

        val result = executor.execute(
            Action(
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
