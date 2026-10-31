package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
