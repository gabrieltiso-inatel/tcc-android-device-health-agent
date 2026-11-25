package com.tcc.devicehealth.agent.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ForegroundSync(private val ioDispatcher: CoroutineDispatcher) {
    private var pollingJob: Job? = null
    private var synchronizationJob: Job? = null

    fun start(
        scope: CoroutineScope,
        processPendingActions: suspend () -> Result<Int>,
        onActionsProcessed: suspend (Int) -> Unit,
        syncTelemetry: suspend () -> Result<Unit>,
        syncStorage: suspend () -> Result<Unit>,
        syncApps: suspend () -> Result<Unit>,
        refreshScreen: suspend () -> Result<Unit>,
    ) {
        if (synchronizationJob == null) {
            synchronizationJob = scope.launch(ioDispatcher) {
                coroutineScope {
                    launch { syncPeriodically(SyncIntervals.telemetryMillis, syncTelemetry) }
                    launch { syncPeriodically(SyncIntervals.storageMillis, syncStorage) }
                    launch { syncPeriodically(SyncIntervals.appInventoryMillis, syncApps) }
                    launch { syncPeriodically(SyncIntervals.screenRefreshMillis, refreshScreen) }
                }
            }
        }
        if (pollingJob == null) {
            pollingJob = scope.launch {
                val schedule = ForegroundPollingSchedule()
                while (isActive) {
                    val result = withContext(ioDispatcher) { processPendingActions() }
                    val processedCount = result.getOrDefault(0)
                    if (processedCount > 0) onActionsProcessed(processedCount)
                    delay(schedule.nextDelayMillis(result.isSuccess))
                }
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        synchronizationJob?.cancel()
        synchronizationJob = null
    }
}

internal suspend fun syncPeriodically(
    intervalMillis: Long,
    sync: suspend () -> Result<Unit>,
    wait: suspend (Long) -> Unit = { delay(it) },
) {
    require(intervalMillis > 0)
    while (currentCoroutineContext().isActive) {
        val result = sync()
        val failure = result.exceptionOrNull()
        if (failure is CancellationException) throw failure
        wait(intervalMillis)
    }
}

class ForegroundPollingSchedule {
    private var delayMillis = SyncIntervals.actionPollingMillis

    fun nextDelayMillis(succeeded: Boolean): Long {
        delayMillis = if (succeeded) SyncIntervals.actionPollingMillis else
            (delayMillis * 2).coerceAtMost(SyncIntervals.actionPollingMaxBackoffMillis)
        return delayMillis
    }
}
