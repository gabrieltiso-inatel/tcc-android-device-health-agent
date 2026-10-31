package com.tcc.devicehealth.agent

object SyncIntervals {
    const val actionPollingMillis = 3_000L
    const val actionPollingMaxBackoffMillis = 30_000L
    const val telemetryMillis = 30_000L
    const val storageMillis = 60_000L
    const val appInventoryMillis = 300_000L
    const val screenRefreshMillis = 15_000L
    const val backgroundMinutes = 15L
    const val backgroundBackoffSeconds = 30L
}
