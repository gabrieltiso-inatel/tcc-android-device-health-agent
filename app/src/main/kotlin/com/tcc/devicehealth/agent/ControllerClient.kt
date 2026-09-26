package com.tcc.devicehealth.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class RetryableControllerException(cause: Throwable? = null) :
    Exception("Controller is temporarily unavailable", cause)

internal class ControllerResponseException(val statusCode: Int) :
    Exception("Controller rejected the request")

internal class ControllerClient(private val baseUrl: String) {
    suspend fun pair(code: String, telemetry: DeviceTelemetry): String {
        val body = JSONObject()
            .put("code", code)
            .put("deviceId", telemetry.deviceId)
            .put("deviceName", telemetry.deviceName)
        val response = request(method = "POST", path = "/api/devices/pair", body = body.toString())
        return JSONObject(response).getString("token")
    }

    suspend fun sendTelemetry(telemetry: DeviceTelemetry, token: String) {
        val body = JSONObject()
            .put("deviceName", telemetry.deviceName)
            .put("manufacturer", telemetry.manufacturer)
            .put("model", telemetry.model)
            .put("androidVersion", telemetry.androidVersion)
            .put("apiLevel", telemetry.apiLevel)
            .put("agentVersion", telemetry.agentVersion)
            .put("capabilities", JSONArray(telemetry.capabilities))
            .put("batteryPercentage", telemetry.batteryPercentage)
            .put("isCharging", telemetry.isCharging)
            .put("capturedAt", telemetry.capturedAt)
        request(
            method = "POST",
            path = "/api/devices/${telemetry.deviceId}/telemetry",
            body = body.toString(),
            token = token,
        )
    }

    suspend fun getCommands(deviceId: String, token: String): List<ControllerCommand> {
        val response = request(method = "GET", path = "/api/devices/$deviceId/commands", token = token)
        val commands = JSONObject(response).getJSONArray("commands")
        return List(commands.length()) { index ->
            val command = commands.getJSONObject(index)
            ControllerCommand(id = command.getString("id"), type = command.getString("type"))
        }
    }

    suspend fun sendCommandResult(commandId: String, result: StoredCommandResult, token: String) {
        val body = JSONObject()
            .put("succeeded", result.succeeded)
            .put("message", result.message)
        result.errorCode?.let { errorCode -> body.put("errorCode", errorCode) }
        result.resultJson?.let { resultJson -> body.put("result", JSONObject(resultJson)) }
        request(
            method = "POST",
            path = "/api/commands/$commandId/result",
            body = body.toString(),
            token = token,
        )
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
    ): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
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
            when {
                statusCode in 200..299 -> responseBody
                statusCode == 408 || statusCode == 429 || statusCode >= 500 ->
                    throw RetryableControllerException()
                else -> throw ControllerResponseException(statusCode)
            }
        } catch (error: RetryableControllerException) {
            throw error
        } catch (error: IOException) {
            throw RetryableControllerException(error)
        } finally {
            connection?.disconnect()
        }
    }
}
