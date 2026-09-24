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

data class DeviceTelemetry(
    val deviceId: String,
    val deviceName: String,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val capturedAt: String,
)

data class ControllerCommand(
    val id: String,
    val type: String,
)

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
        val response = withContext(Dispatchers.IO) {
            request(method = "POST", path = "/api/devices/pair", body = body.toString())
        }
        preferences.saveToken(JSONObject(response).getString("token"))
    }

    override suspend fun sendTelemetry(): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val token = requireToken()
        val body = JSONObject()
            .put("deviceName", telemetry.deviceName)
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
        val result = runCatching {
            if (command.type != "collectTelemetry") {
                error("Unsupported command type")
            }
            sendTelemetry().getOrThrow()
            "Telemetry sent"
        }
        val body = JSONObject()
            .put("succeeded", result.isSuccess)
            .put("message", result.getOrElse { it.message ?: "Command failed" })

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
        val connection = (URL(controllerBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
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
        connection.disconnect()

        check(statusCode in 200..299) { "Controller returned HTTP $statusCode: $responseBody" }
        return responseBody
    }
}
