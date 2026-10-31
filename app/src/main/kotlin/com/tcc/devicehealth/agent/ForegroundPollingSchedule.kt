package com.tcc.devicehealth.agent

class ForegroundPollingSchedule {
    private var delayMillis = 3_000L

    fun nextDelayMillis(succeeded: Boolean): Long {
        delayMillis = if (succeeded) 3_000L else (delayMillis * 2).coerceAtMost(30_000L)
        return delayMillis
    }
}
