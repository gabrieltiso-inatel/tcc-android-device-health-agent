package com.tcc.devicehealth.agent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ActionPollingWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(applicationContext, workerParameters) {
    override suspend fun doWork(): Result {
        val dependencies = AgentDependencies(applicationContext)
        val deviceRepository = dependencies.deviceRepository
        val actionRepository = dependencies.actionRepository
        if (!deviceRepository.isPaired()) {
            return Result.success()
        }
        val telemetryResult = deviceRepository.sendTelemetry()
        val storageResult = deviceRepository.syncStorageSummary()
        val appResult = dependencies.applicationRepository.syncApplications()
        val pollingResult = actionRepository.processPendingActions()
        return if (telemetryResult.isSuccess && storageResult.isSuccess && appResult.isSuccess && pollingResult.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
