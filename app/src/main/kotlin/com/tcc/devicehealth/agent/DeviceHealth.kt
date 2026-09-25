package com.tcc.devicehealth.agent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.io.IOException

data class DeviceTelemetry(
    val deviceId: String,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val agentVersion: String,
    val capabilities: List<String>,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val capturedAt: String,
)

data class ControllerCommand(
    val id: String,
    val type: String,
)

internal class RetryableControllerException(cause: Throwable? = null) :
    Exception("Controller is temporarily unavailable", cause)

private class ControllerResponseException(val statusCode: Int) :
    Exception("Controller rejected the request")

private class UnsupportedCommandException : Exception("Command is not supported")

internal fun storedCommandResult(execution: Result<String>): StoredCommandResult? {
    val failure = execution.exceptionOrNull()
    if (failure is RetryableControllerException) {
        return null
    }
    return if (failure == null) {
        StoredCommandResult(succeeded = true, message = execution.getOrThrow())
    } else if (failure is UnsupportedCommandException) {
        StoredCommandResult(
            succeeded = false,
            message = "Command is not supported",
            errorCode = "unsupported_command",
        )
    } else {
        StoredCommandResult(
            succeeded = false,
            message = "Command execution failed",
            errorCode = "execution_failed",
        )
    }
}

interface DeviceHealthRepository {
    suspend fun readTelemetry(): DeviceTelemetry
    suspend fun isPaired(): Boolean
    suspend fun pair(code: String): Result<Unit>
    suspend fun sendTelemetry(): Result<Unit>
    suspend fun checkCommands(): Result<Int>
}

class AndroidDeviceHealthRepository(
    private val context: Context,
    private val controllerBaseUrl: String,
    private val preferences: AgentPreferences = AgentPreferences(context),
) : DeviceHealthRepository {
    override suspend fun readTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return DeviceTelemetry(
            deviceId = preferences.getDeviceId(),
            deviceName = listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim(),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            agentVersion = BuildConfig.VERSION_NAME,
            capabilities = listOf("collectTelemetry"),
            batteryPercentage = percentage,
            isCharging = isCharging,
            capturedAt = Instant.now().toString(),
        )
    }

    override suspend fun isPaired(): Boolean = preferences.getToken() != null

    override suspend fun pair(code: String): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val body = JSONObject()
            .put("code", code)
            .put("deviceId", telemetry.deviceId)
            .put("deviceName", telemetry.deviceName)
        val response = try {
            withContext(Dispatchers.IO) {
                request(method = "POST", path = "/api/devices/pair", body = body.toString())
            }
        } catch (error: ControllerResponseException) {
            if (error.statusCode == 400) {
                throw IllegalStateException("Pairing code is invalid or expired")
            }
            throw error
        }
        preferences.saveToken(JSONObject(response).getString("token"))
    }

    override suspend fun sendTelemetry(): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val token = requireToken()
        val body = JSONObject()
            .put("deviceName", telemetry.deviceName)
            .put("manufacturer", telemetry.manufacturer)
            .put("model", telemetry.model)
            .put("androidVersion", telemetry.androidVersion)
            .put("apiLevel", telemetry.apiLevel)
            .put("agentVersion", telemetry.agentVersion)
            .put("capabilities", org.json.JSONArray(telemetry.capabilities))
            .put("batteryPercentage", telemetry.batteryPercentage)
            .put("isCharging", telemetry.isCharging)
            .put("capturedAt", telemetry.capturedAt)

        withContext(Dispatchers.IO) {
            request(
                method = "POST",
                path = "/api/devices/${telemetry.deviceId}/telemetry",
                body = body.toString(),
                token = token,
            )
        }
        Unit
    }

    override suspend fun checkCommands(): Result<Int> = runCatching {
        val telemetry = readTelemetry()
        val token = requireToken()
        val commandResponse = withContext(Dispatchers.IO) {
            request(
                method = "GET",
                path = "/api/devices/${telemetry.deviceId}/commands",
                token = token,
            )
        }
        val commands = JSONObject(commandResponse).getJSONArray("commands")
        var processedCount = 0

        for (index in 0 until commands.length()) {
            val command = commands.getJSONObject(index)
            val controllerCommand = ControllerCommand(
                id = command.getString("id"),
                type = command.getString("type"),
            )
            executeCommand(controllerCommand)
            processedCount += 1
        }

        processedCount
    }

    private suspend fun executeCommand(command: ControllerCommand) {
        val token = requireToken()
        val result = preferences.getCommandResult(command.id) ?: run {
            val execution = runCatching {
                if (command.type != "collectTelemetry") {
                    throw UnsupportedCommandException()
                }
                sendTelemetry().getOrThrow()
                "Telemetry sent"
            }
            val storedResult = storedCommandResult(execution) ?: throw execution.exceptionOrNull()!!
            storedResult.also { preferences.saveCommandResult(command.id, it) }
        }
        val body = JSONObject()
            .put("succeeded", result.succeeded)
            .put("message", result.message)
        result.errorCode?.let { errorCode -> body.put("errorCode", errorCode) }

        withContext(Dispatchers.IO) {
            request(
                method = "POST",
                path = "/api/commands/${command.id}/result",
                body = body.toString(),
                token = token,
            )
        }
    }

    private suspend fun requireToken(): String = checkNotNull(preferences.getToken()) {
        "Device is not paired"
    }

    private fun request(method: String, path: String, body: String? = null, token: String? = null): String {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(controllerBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("accept", "application/json")
            token?.let { connection.setRequestProperty("authorization", "Bearer $it") }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("content-type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter().use { writer -> writer.write(body) }
            }

            val statusCode = connection.responseCode
            val responseBody = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { reader -> reader.readText() }
                .orEmpty()
            if (statusCode in 200..299) {
                return responseBody
            }
            if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
                throw RetryableControllerException()
            }
            throw ControllerResponseException(statusCode)
        } catch (error: RetryableControllerException) {
            throw error
        } catch (error: IOException) {
            throw RetryableControllerException(error)
        } finally {
            connection?.disconnect()
        }
    }
}
