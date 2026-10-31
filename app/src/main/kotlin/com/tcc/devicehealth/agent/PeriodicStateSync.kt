package com.tcc.devicehealth.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
