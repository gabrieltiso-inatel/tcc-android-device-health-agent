package com.tcc.devicehealth.agent

class ForegroundPollingSchedule {
    private var delayMillis = SyncIntervals.actionPollingMillis

    fun nextDelayMillis(succeeded: Boolean): Long {
        delayMillis = if (succeeded) SyncIntervals.actionPollingMillis else
            (delayMillis * 2).coerceAtMost(SyncIntervals.actionPollingMaxBackoffMillis)
        return delayMillis
    }
}
