package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class StoredCommandResultTest {
    @Test
    fun retryableControllerFailureIsNotStored() {
        val result = storedCommandResult(Result.failure(RetryableControllerException()))

        assertEquals(null, result)
    }

    @Test
    fun unexpectedFailureIsStoredWithoutInternalDetails() {
        val result = storedCommandResult(Result.failure(IllegalStateException("internal details")))

        assertEquals(false, result?.succeeded)
        assertEquals("execution_failed", result?.errorCode)
        assertEquals("Command execution failed", result?.message)
    }

    @Test
    fun unsupportedCommandIsStoredWithAStableErrorCode() {
        val result = storedCommandResult(Result.failure(UnsupportedCommandException()))

        assertEquals(false, result?.succeeded)
        assertEquals("unsupported_command", result?.errorCode)
        assertEquals("Command is not supported", result?.message)
    }

    @Test
    fun successfulCommandKeepsItsStructuredResult() {
        val resultJson = """{"totalBytes":100,"usedBytes":60,"availableBytes":40}"""

        val result = storedCommandResult(Result.success(CommandExecution("Storage summary collected", resultJson)))

        assertEquals(true, result?.succeeded)
        assertEquals(resultJson, result?.resultJson)
    }
}
