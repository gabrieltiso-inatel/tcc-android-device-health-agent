package com.tcc.devicehealth.agent.data

import com.tcc.devicehealth.agent.data.android.DeviceTelemetryDataSource
import com.tcc.devicehealth.agent.data.android.StorageDataSource
import com.tcc.devicehealth.agent.data.local.AgentPreferencesDataSource
import com.tcc.devicehealth.agent.data.remote.ControllerApiClient
import com.tcc.devicehealth.agent.data.remote.ControllerApiResponseException
import com.tcc.devicehealth.agent.data.remote.normalizeControllerAddress
import com.tcc.devicehealth.agent.model.DeviceTelemetry
import com.tcc.devicehealth.agent.model.DisplayPreferences
import com.tcc.devicehealth.agent.model.LatestDeviceSnapshots
import com.tcc.devicehealth.agent.model.StorageSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DeviceRepository {
    suspend fun readTelemetry(): DeviceTelemetry
    suspend fun isPaired(): Boolean
    suspend fun pair(code: String): Result<Unit>
    suspend fun getControllerAddress(): String
    suspend fun configureController(address: String): Result<Unit>
    suspend fun sendTelemetry(): Result<Unit>
    suspend fun collectStorage(): StorageSummary
    suspend fun syncStorageSummary(): Result<Unit>
    suspend fun getSnapshots(): Result<LatestDeviceSnapshots>
    suspend fun getDisplayPreferences(): DisplayPreferences
    suspend fun saveDisplayPreferences(displayPreferences: DisplayPreferences): Result<Unit>
}

internal class AndroidDeviceRepository(
    private val controllerBaseUrl: String,
    private val preferences: AgentPreferencesDataSource,
    private val controllerClient: ControllerApiClient,
    private val telemetrySource: DeviceTelemetryDataSource,
    private val storageDataSource: StorageDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeviceRepository {
    override suspend fun readTelemetry(): DeviceTelemetry = withContext(ioDispatcher) { telemetrySource.read() }

    override suspend fun isPaired(): Boolean = preferences.getToken() != null

    override suspend fun getDisplayPreferences(): DisplayPreferences = preferences.getDisplayPreferences()

    override suspend fun saveDisplayPreferences(displayPreferences: DisplayPreferences): Result<Unit> = repositoryOperation(ioDispatcher) {
        preferences.saveDisplayPreferences(displayPreferences)
    }

    override suspend fun getControllerAddress(): String = preferences.getControllerAddress() ?: controllerBaseUrl

    override suspend fun configureController(address: String): Result<Unit> = repositoryOperation(ioDispatcher) {
        val normalized = normalizeControllerAddress(address)
        check(!isPaired() || normalized == getControllerAddress())
        preferences.saveControllerAddress(normalized)
    }

    override suspend fun pair(code: String): Result<Unit> = repositoryOperation(ioDispatcher) {
        val token = try {
            controllerClient.pair(code, readTelemetry())
        } catch (error: ControllerApiResponseException) {
            if (error.statusCode == 400) {
                throw IllegalStateException("Pairing code is invalid or expired")
            }
            throw error
        }
        preferences.saveToken(token)
    }

    override suspend fun sendTelemetry(): Result<Unit> = repositoryOperation(ioDispatcher) {
        controllerClient.sendTelemetry(readTelemetry(), requireToken())
    }

    override suspend fun collectStorage(): StorageSummary =
        withContext(ioDispatcher) { storageDataSource.collectSummary() }

    override suspend fun syncStorageSummary(): Result<Unit> = repositoryOperation(ioDispatcher) {
        val telemetry = readTelemetry()
        val summary = collectStorage()
        controllerClient.sendStorageSummary(telemetry.deviceId, summary, requireToken())
    }

    override suspend fun getSnapshots(): Result<LatestDeviceSnapshots> = repositoryOperation(ioDispatcher) {
        controllerClient.getSnapshots(preferences.getDeviceId(), requireToken())
    }

    private suspend fun requireToken(): String = checkNotNull(preferences.getToken()) {
        "Device is not paired"
    }
}
