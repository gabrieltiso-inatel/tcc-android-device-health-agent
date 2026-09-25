package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentViewModelTest {
    @Test
    fun telemetryContainsTheExpectedBatteryState() {
        val telemetry = DeviceTelemetry(
            deviceId = "device-1",
            deviceName = "Pixel",
            manufacturer = "Google",
            model = "Pixel 8",
            androidVersion = "14",
            apiLevel = 34,
            agentVersion = "0.1.0",
            capabilities = listOf("collectTelemetry", "collectStorageSummary"),
            batteryPercentage = 75,
            isCharging = true,
            capturedAt = "2026-09-19T12:00:00Z",
        )

        assertEquals("Pixel", telemetry.deviceName)
        assertEquals(75, telemetry.batteryPercentage)
        assertEquals(true, telemetry.isCharging)
        assertEquals(listOf("collectTelemetry", "collectStorageSummary"), telemetry.capabilities)
    }

    @Test
    fun retryableControllerFailureDoesNotCreateAStoredResult() {
        val result = storedCommandResult(Result.failure(RetryableControllerException()))

        assertEquals(null, result)
    }

    @Test
    fun unexpectedFailureCreatesASafeStoredResult() {
        val result = storedCommandResult(Result.failure(IllegalStateException("internal details")))

        assertEquals(false, result?.succeeded)
        assertEquals("execution_failed", result?.errorCode)
        assertEquals("Command execution failed", result?.message)
    }

    @Test
    fun successfulCommandKeepsItsStructuredResult() {
        val resultJson = """{"totalBytes":100,"usedBytes":60,"availableBytes":40}"""

        val result = storedCommandResult(Result.success(CommandExecution("Storage summary collected", resultJson)))

        assertEquals(true, result?.succeeded)
        assertEquals(resultJson, result?.resultJson)
    }
}
