package com.tcc.devicehealth.agent

import com.tcc.devicehealth.agent.actions.UnsupportedActionException
import com.tcc.devicehealth.agent.actions.mapExecutionResult
import com.tcc.devicehealth.agent.data.remote.ControllerApiUnavailableException
import com.tcc.devicehealth.agent.model.ActionExecutionResult
import com.tcc.devicehealth.agent.model.StorageSummary
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionResultMapperTest {
    @Test(expected = CancellationException::class)
    fun cancellationIsNeverConvertedIntoAFailedAction() {
        mapExecutionResult(Result.failure(CancellationException()))
    }

    @Test
    fun retryableControllerFailureIsNotStored() {
        val result = mapExecutionResult(Result.failure(ControllerApiUnavailableException()))

        assertEquals(null, result)
    }

    @Test
    fun unexpectedFailureIsStoredWithoutInternalDetails() {
        val result = mapExecutionResult(Result.failure(IllegalStateException("internal details")))

        assertEquals(false, result?.succeeded)
        assertEquals("execution_failed", result?.errorCode)
        assertEquals("Action execution failed", result?.message)
    }

    @Test
    fun unsupportedActionIsStoredWithAStableErrorCode() {
        val result = mapExecutionResult(Result.failure(UnsupportedActionException()))

        assertEquals(false, result?.succeeded)
        assertEquals("unsupported_action", result?.errorCode)
        assertEquals("Action is not supported", result?.message)
    }

    @Test
    fun successfulActionKeepsItsStructuredResult() {
        val summary = StorageSummary(100, 60, 40, "2026-10-31T12:00:00Z")

        val result = mapExecutionResult(Result.success(ActionExecutionResult(succeeded = true, message = "Storage summary collected", collectedData = summary)))

        assertEquals(true, result?.succeeded)
        assertEquals(summary, result?.collectedData)
    }

    @Test
    fun approvalResultKeepsItsPendingStateAndCode() {
        val result = mapExecutionResult(Result.success(ActionExecutionResult(succeeded = false, message = "Application removal requires approval", status = "awaiting_approval", errorCode = "approval_required")))

        assertEquals(false, result?.succeeded)
        assertEquals("awaiting_approval", result?.status)
        assertEquals("approval_required", result?.errorCode)
    }
}
