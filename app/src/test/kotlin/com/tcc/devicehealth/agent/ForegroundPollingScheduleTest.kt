package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundPollingScheduleTest {
    @Test
    fun successfulPollingUsesThreeSeconds() {
        assertEquals(3_000L, ForegroundPollingSchedule().nextDelayMillis(true))
    }

    @Test
    fun failuresBackOffUntilThirtySeconds() {
        val schedule = ForegroundPollingSchedule()

        assertEquals(
            listOf(6_000L, 12_000L, 24_000L, 30_000L, 30_000L),
            List(5) { schedule.nextDelayMillis(false) },
        )
    }

    @Test
    fun successRestoresFastPollingAfterFailures() {
        val schedule = ForegroundPollingSchedule()
        repeat(5) { schedule.nextDelayMillis(false) }

        assertEquals(3_000L, schedule.nextDelayMillis(true))
        assertEquals(6_000L, schedule.nextDelayMillis(false))
    }
}
