package com.tcc.devicehealth.agent

import com.tcc.devicehealth.agent.data.repositoryOperation
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RepositoryOperationTest {
    @Test
    fun blockingOperationUsesTheInjectedDispatcher() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val result = repositoryOperation(dispatcher) {
            assertEquals(dispatcher, currentCoroutineContext()[ContinuationInterceptor])
            "Collected"
        }
        assertEquals("Collected", result.getOrThrow())
    }

    @Test
    fun cancellationEscapesTheResultBoundary() {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                repositoryOperation(Dispatchers.Unconfined) { throw CancellationException() }
            }
        }
    }
}
