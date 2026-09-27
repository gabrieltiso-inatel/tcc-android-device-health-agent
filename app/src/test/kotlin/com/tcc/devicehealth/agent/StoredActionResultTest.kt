package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class StoredActionResultTest {
    @Test
    fun retryableControllerFailureIsNotStored() {
        val result = storedActionResult(Result.failure(RetryableControllerException()))

        assertEquals(null, result)
    }

    @Test
    fun unexpectedFailureIsStoredWithoutInternalDetails() {
        val result = storedActionResult(Result.failure(IllegalStateException("internal details")))

        assertEquals(false, result?.succeeded)
        assertEquals("execution_failed", result?.errorCode)
        assertEquals("Action execution failed", result?.message)
    }

    @Test
    fun unsupportedActionIsStoredWithAStableErrorCode() {
        val result = storedActionResult(Result.failure(UnsupportedActionException()))

        assertEquals(false, result?.succeeded)
        assertEquals("unsupported_action", result?.errorCode)
        assertEquals("Action is not supported", result?.message)
    }

    @Test
    fun successfulActionKeepsItsStructuredResult() {
        val resultJson = """{"totalBytes":100,"usedBytes":60,"availableBytes":40}"""

        val result = storedActionResult(Result.success(ActionExecution("Storage summary collected", resultJson)))

        assertEquals(true, result?.succeeded)
        assertEquals(resultJson, result?.resultJson)
    }

    @Test
    fun approvalRequiredActionIsNotReportedAsCompleted() {
        val result = storedActionResult(Result.success(ActionExecution("Application removal requires approval", approvalRequired = true)))

        assertEquals(false, result?.succeeded)
        assertEquals("awaiting_approval", result?.status)
        assertEquals("approval_required", result?.errorCode)
    }
}
