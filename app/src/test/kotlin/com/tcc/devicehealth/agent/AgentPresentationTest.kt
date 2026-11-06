package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AgentPresentationTest {
    @Test
    fun pendingCountIncludesFileAndApplicationApprovals() {
        val state = AgentUiState(
            pendingApprovals = listOf(PendingApproval("app-action", "app", 1)),
            pendingFileApprovals = listOf(PendingFileApproval("file-action", "file", "revision")),
        )
        assertEquals(2, pendingApprovalCount(state))
    }

    @Test
    fun onlyActiveRemovalsOfTheSameTargetDisableRemoval() {
        val pending = DeviceAction("action", "removeFile", fileId = "file", origin = "controller", status = "awaiting_approval", requestedAt = "2026-10-31T12:00:00Z")
        assertTrue(removalInProgress(listOf(pending), fileId = "file"))
        assertFalse(removalInProgress(listOf(pending), fileId = "other-file"))
        assertFalse(removalInProgress(listOf(pending.copy(status = "completed")), fileId = "file"))
    }

    @Test
    fun healthIsGoodWhenNothingNeedsAttention() {
        val state = AgentUiState(telemetry = telemetry(battery = 80, charging = false), snapshots = DeviceSnapshots(storage = storage(used = 40)))
        assertEquals(HealthVerdict.Healthy, healthVerdict(state))
    }

    @Test
    fun pendingApprovalsComeBeforeOtherWarnings() {
        val state = AgentUiState(
            telemetry = telemetry(battery = 5, charging = false),
            pendingApprovals = listOf(PendingApproval("app-action", "app", 1)),
        )
        assertEquals(HealthVerdict.ApprovalsWaiting(1), healthVerdict(state))
    }

    @Test
    fun lowBatteryIsReportedOnlyWhenNotCharging() {
        assertEquals(HealthVerdict.LowBattery, healthVerdict(AgentUiState(telemetry = telemetry(battery = 15, charging = false))))
        assertEquals(HealthVerdict.Healthy, healthVerdict(AgentUiState(telemetry = telemetry(battery = 15, charging = true))))
    }

    @Test
    fun almostFullStorageIsReported() {
        val state = AgentUiState(snapshots = DeviceSnapshots(storage = storage(used = 95)))
        assertEquals(HealthVerdict.StorageAlmostFull, healthVerdict(state))
    }

    @Test
    fun savedReadingsAreReportedWhenTheControllerIsUnreachable() {
        assertEquals(HealthVerdict.SavedReadings, healthVerdict(AgentUiState(isShowingSavedReadings = true)))
    }

    @Test
    fun relativeTimeUsesTheLargestElapsedUnit() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        assertEquals(RelativeTime.JustNow, relativeTime("2026-11-06T11:59:30Z", now))
        assertEquals(RelativeTime.MinutesAgo(5), relativeTime("2026-11-06T11:55:00Z", now))
        assertEquals(RelativeTime.HoursAgo(2), relativeTime("2026-11-06T10:00:00Z", now))
        assertEquals(RelativeTime.DaysAgo(3), relativeTime("2026-11-03T12:00:00Z", now))
        assertEquals(null, relativeTime("not a date", now))
    }

    private fun telemetry(battery: Int, charging: Boolean) =
        DeviceTelemetry("device", "Phone", "Google", "Pixel", "14", 34, "0.1", emptyList(), battery, charging, "2026-11-06T12:00:00Z")

    private fun storage(used: Long) = StorageSummary(100, used, 100 - used, "2026-11-06T12:00:00Z")
}
