package com.tcc.devicehealth.agent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

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
    fun readTelemetry(): DeviceTelemetry
    fun sendTelemetry(): Result<Unit>
    fun checkCommands(): Result<Int>
}

class AndroidDeviceHealthRepository(
    private val context: Context,
    private val controllerBaseUrl: String,
) : DeviceHealthRepository {
    private val preferences = context.getSharedPreferences("device_health_agent", Context.MODE_PRIVATE)

    override fun readTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return DeviceTelemetry(
            deviceId = getDeviceId(),
            deviceName = listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim(),
            batteryPercentage = percentage,
            isCharging = isCharging,
            capturedAt = Instant.now().toString(),
        )
    }

    override fun sendTelemetry(): Result<Unit> = runCatching {
        val telemetry = readTelemetry()
        val body = JSONObject()
            .put("deviceName", telemetry.deviceName)
            .put("batteryPercentage", telemetry.batteryPercentage)
            .put("isCharging", telemetry.isCharging)
            .put("capturedAt", telemetry.capturedAt)

        request(
            method = "POST",
            path = "/api/devices/${telemetry.deviceId}/telemetry",
            body = body.toString(),
        )
        Unit
    }

    override fun checkCommands(): Result<Int> = runCatching {
        val telemetry = readTelemetry()
        val commandResponse = request(
            method = "GET",
            path = "/api/devices/${telemetry.deviceId}/commands",
        )
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

    private fun executeCommand(command: ControllerCommand) {
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

        request(
            method = "POST",
            path = "/api/commands/${command.id}/result",
            body = body.toString(),
        )
    }

    private fun getDeviceId(): String {
        val storedDeviceId = preferences.getString("device_id", null)
        if (storedDeviceId != null) {
            return storedDeviceId
        }

        val deviceId = UUID.randomUUID().toString()
        preferences.edit().putString("device_id", deviceId).apply()
        return deviceId
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val connection = (URL(controllerBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("accept", "application/json")

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
