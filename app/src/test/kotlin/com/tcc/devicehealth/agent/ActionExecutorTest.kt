package com.tcc.devicehealth.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionExecutorTest {
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
