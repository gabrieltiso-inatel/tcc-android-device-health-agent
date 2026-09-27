package com.tcc.devicehealth.agent

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ActionPollingWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(applicationContext, workerParameters) {
    override suspend fun doWork(): Result {
        val repository = AndroidDeviceHealthRepository(
            context = applicationContext,
            controllerBaseUrl = BuildConfig.CONTROLLER_BASE_URL,
        )
        if (!repository.isPaired()) {
            return Result.success()
        }
        val storageResult = repository.syncStorageSummary()
        val actionResult = repository.checkActions()
        return if (storageResult.isSuccess && actionResult.isSuccess) Result.success() else Result.retry()
    }
}

object ActionPollingScheduler {
    private const val workName = "action-polling"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<ActionPollingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
