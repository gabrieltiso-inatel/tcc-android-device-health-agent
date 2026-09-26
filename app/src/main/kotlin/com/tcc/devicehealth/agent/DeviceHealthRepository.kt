package com.tcc.devicehealth.agent

import android.content.Context

interface DeviceHealthRepository {
    suspend fun readTelemetry(): DeviceTelemetry
    suspend fun isPaired(): Boolean
    suspend fun pair(code: String): Result<Unit>
    suspend fun sendTelemetry(): Result<Unit>
    suspend fun checkCommands(): Result<Int>
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
    private val commandExecutor = CommandExecutor(
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

    override suspend fun checkCommands(): Result<Int> = runCatching {
        val telemetry = readTelemetry()
        val token = requireToken()
        val commands = controllerClient.getCommands(telemetry.deviceId, token)
        commands.forEach { command -> executeCommand(command, token) }
        commands.size
    }

    private suspend fun executeCommand(command: ControllerCommand, token: String) {
        val result = preferences.getCommandResult(command.id) ?: run {
            val execution = runCatching { commandExecutor.execute(command) }
            val storedResult = storedCommandResult(execution) ?: throw execution.exceptionOrNull()!!
            storedResult.also { preferences.saveCommandResult(command.id, it) }
        }
        controllerClient.sendCommandResult(command.id, result, token)
    }

    private suspend fun requireToken(): String = checkNotNull(preferences.getToken()) {
        "Device is not paired"
    }
}
