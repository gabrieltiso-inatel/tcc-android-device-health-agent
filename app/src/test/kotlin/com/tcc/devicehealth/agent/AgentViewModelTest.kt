package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentViewModelTest {
    @Test
    fun telemetryContainsTheExpectedBatteryState() {
        val telemetry = DeviceTelemetry(
            deviceId = "device-1",
            deviceName = "Pixel",
            batteryPercentage = 75,
            isCharging = true,
            capturedAt = "2026-09-19T12:00:00Z",
        )

        assertEquals("Pixel", telemetry.deviceName)
        assertEquals(75, telemetry.batteryPercentage)
        assertEquals(true, telemetry.isCharging)
    }
}
