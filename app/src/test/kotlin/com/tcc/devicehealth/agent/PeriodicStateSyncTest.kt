package com.tcc.devicehealth.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PeriodicStateSyncTest {
    @Test
    fun syncsImmediatelyAndWaitsBetweenAttemptsEvenAfterFailure() {
        val events = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                syncPeriodically(
                    intervalMillis = 100,
                    sync = {
                        events.add("sync")
                        Result.failure(IllegalStateException("Unavailable"))
                    },
                    wait = { interval ->
                        events.add("wait:$interval")
                        if (events.size == 4) throw CancellationException()
                    },
                )
            }
        }
        assertEquals(listOf("sync", "wait:100", "sync", "wait:100"), events)
    }

    @Test
    fun cancellationReturnedByRepositoryStopsSynchronization() {
        var waits = 0
        assertThrows(CancellationException::class.java) {
            runBlocking {
                syncPeriodically(100, { Result.failure(CancellationException()) }, { waits++ })
            }
        }
        assertEquals(0, waits)
    }

    @Test
    fun successfulSyncWaitsBeforeNextCollection() {
        var calls = 0
        assertThrows(CancellationException::class.java) {
            runBlocking {
                syncPeriodically(200, {
                    calls++
                    Result.success(Unit)
                }, { interval ->
                    assertEquals(200L, interval)
                    assertEquals(1, calls)
                    throw CancellationException()
                })
            }
        }
    }
}
