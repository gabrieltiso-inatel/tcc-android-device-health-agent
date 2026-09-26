package com.tcc.devicehealth.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandExecutorTest {
    @Test
    fun appInventoryCommandReturnsTheCollectedInventory() = runBlocking {
        val expected = CommandExecution("App inventory collected", """{"apps":[]}""")
        val executor = CommandExecutor(
            sendTelemetry = {},
            collectStorageSummary = { CommandExecution("Storage summary collected") },
            collectAppInventory = { expected },
        )

        val result = executor.execute(ControllerCommand("command-1", "collectAppInventory"))

        assertEquals(expected, result)
    }
}
