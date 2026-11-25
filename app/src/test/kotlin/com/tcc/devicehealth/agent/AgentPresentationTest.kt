package com.tcc.devicehealth.agent

import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.DeviceTelemetry
import com.tcc.devicehealth.agent.model.LatestDeviceSnapshots
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import com.tcc.devicehealth.agent.model.StorageSummary
import com.tcc.devicehealth.agent.ui.AgentUiState
import com.tcc.devicehealth.agent.ui.HealthVerdict
import com.tcc.devicehealth.agent.ui.RelativeTime
import com.tcc.devicehealth.agent.ui.emptyListMessage
import com.tcc.devicehealth.agent.ui.healthVerdict
import com.tcc.devicehealth.agent.ui.pendingApprovalCount
import com.tcc.devicehealth.agent.ui.relativeTime
import com.tcc.devicehealth.agent.ui.removalInProgress
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPresentationTest {
    @Test
    fun pendingCountIncludesFileAndApplicationApprovals() {
        val state = AgentUiState(
            pendingApplicationRemovals = listOf(PendingApplicationRemoval("app-action", "app", 1)),
            pendingFileRemovals = listOf(PendingFileRemoval("file-action", "file", "revision")),
        )
        assertEquals(2, pendingApprovalCount(state))
    }

    @Test
    fun onlyActiveRemovalsOfTheSameTargetDisableRemoval() {
        val pending = Action("action", "removeFile", fileId = "file", origin = "controller", status = "awaiting_approval", requestedAt = "2026-10-31T12:00:00Z")
        assertTrue(removalInProgress(listOf(pending), fileId = "file"))
        assertFalse(removalInProgress(listOf(pending), fileId = "other-file"))
        assertFalse(removalInProgress(listOf(pending.copy(status = "completed")), fileId = "file"))
    }

    @Test
    fun healthIsGoodWhenNothingNeedsAttention() {
        val state = AgentUiState(telemetry = telemetry(battery = 80, charging = false), snapshots = LatestDeviceSnapshots(storage = storage(used = 40)))
        assertEquals(HealthVerdict.Healthy, healthVerdict(state))
    }

    @Test
    fun pendingApprovalsComeBeforeOtherWarnings() {
        val state = AgentUiState(
            telemetry = telemetry(battery = 5, charging = false),
            pendingApplicationRemovals = listOf(PendingApplicationRemoval("app-action", "app", 1)),
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
        val state = AgentUiState(snapshots = LatestDeviceSnapshots(storage = storage(used = 95)))
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

    @Test
    fun emptyListExplainsWhyItIsEmpty() {
        assertEquals(R.string.files_waiting, emptyListMessage(true, "", R.string.files_waiting, R.string.files_empty_sources))
        assertEquals(R.string.search_no_results, emptyListMessage(false, "photo", R.string.files_waiting, R.string.files_empty_sources))
        assertEquals(R.string.files_empty_sources, emptyListMessage(false, " ", R.string.files_waiting, R.string.files_empty_sources))
    }

    private fun telemetry(battery: Int, charging: Boolean) =
        DeviceTelemetry("device", "Phone", "Google", "Pixel", "14", 34, "0.1", emptyList(), battery, charging, "2026-11-06T12:00:00Z")

    private fun storage(used: Long) = StorageSummary(100, used, 100 - used, "2026-11-06T12:00:00Z")
}
