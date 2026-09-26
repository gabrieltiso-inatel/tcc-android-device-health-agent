package com.tcc.devicehealth.agent

import android.content.Context

interface DeviceHealthRepository {
    suspend fun readTelemetry(): DeviceTelemetry
    suspend fun isPaired(): Boolean
    suspend fun pair(code: String): Result<Unit>
    suspend fun sendTelemetry(): Result<Unit>
    suspend fun checkActions(): Result<Int>
    suspend fun executeDeviceAction(type: String): Result<Unit>
    suspend fun getActionHistory(): Result<List<DeviceAction>>
}

class AndroidDeviceHealthRepository(
    context: Context,
    controllerBaseUrl: String,
    private val preferences: AgentPreferences = AgentPreferences(context.applicationContext),
) : DeviceHealthRepository {
    private val telemetrySource = DeviceTelemetrySource(context.applicationContext, preferences)
    private val controllerClient = ControllerClient(controllerBaseUrl)
    private val storageDataSource = StorageDataSource()
    private val appInventoryDataSource = AppInventoryDataSource(context.applicationContext.packageManager)
    private val actionExecutor = ActionExecutor(
        sendTelemetry = { sendTelemetry().getOrThrow() },
        collectStorageSummary = storageDataSource::collectSummary,
        collectAppInventory = appInventoryDataSource::collect,
    )

    override suspend fun readTelemetry(): DeviceTelemetry = telemetrySource.read()

    override suspend fun isPaired(): Boolean = preferences.getToken() != null

    override suspend fun pair(code: String): Result<Unit> = runCatching {
        val token = try {
            controllerClient.pair(code, readTelemetry())
        } catch (error: ControllerResponseException) {
            if (error.statusCode == 400) {
                throw IllegalStateException("Pairing code is invalid or expired")
            }
            throw error
        }
        preferences.saveToken(token)
    }

    override suspend fun sendTelemetry(): Result<Unit> = runCatching {
        controllerClient.sendTelemetry(readTelemetry(), requireToken())
    }

    override suspend fun checkActions(): Result<Int> = runCatching {
        val telemetry = readTelemetry()
        val token = requireToken()
        val actions = controllerClient.getPendingActions(telemetry.deviceId, token)
        actions.forEach { action -> executeAction(action, token) }
        actions.size
    }

    override suspend fun executeDeviceAction(type: String): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val token = requireToken()
        val action = controllerClient.createDeviceAction(telemetry.deviceId, type, token)
        executeAction(action, token)
    }

    override suspend fun getActionHistory(): Result<List<DeviceAction>> = runCatching {
        val telemetry = readTelemetry()
        controllerClient.getActionHistory(telemetry.deviceId, requireToken())
    }

    private suspend fun executeAction(action: DeviceAction, token: String) {
        val result = preferences.getActionResult(action.id) ?: run {
            val execution = runCatching { actionExecutor.execute(action) }
            val failure = execution.exceptionOrNull()
            if (failure != null) {
                val storedResult = storedActionResult(execution)
                if (storedResult != null) {
                    storedResult.also { preferences.saveActionResult(action.id, it) }
                } else {
                    throw failure
                }
            } else {
                val storedResult = storedActionResult(execution)
                    ?: error("Action execution did not produce a result")
                storedResult.also { preferences.saveActionResult(action.id, it) }
            }
        }
        controllerClient.sendActionResult(action.id, result, token)
    }

    private suspend fun requireToken(): String = checkNotNull(preferences.getToken()) {
        "Device is not paired"
    }
}
