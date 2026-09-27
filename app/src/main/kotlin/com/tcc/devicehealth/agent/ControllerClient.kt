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

    suspend fun sendStorageSummary(deviceId: String, resultJson: String, token: String) {
        request(
            method = "POST",
            path = "/api/devices/$deviceId/storage",
            body = resultJson,
            token = token,
        )
    }

    suspend fun sendAppInventory(deviceId: String, resultJson: String, token: String) {
        request(
            method = "POST",
            path = "/api/devices/$deviceId/apps",
            body = resultJson,
            token = token,
        )
    }

    suspend fun sendFileInventory(deviceId: String, resultJson: String, token: String) {
        request(
            method = "POST",
            path = "/api/devices/$deviceId/files",
            body = resultJson,
            token = token,
        )
    }

    suspend fun getPendingActions(deviceId: String, token: String): List<DeviceAction> {
        val response = request(method = "GET", path = "/api/devices/$deviceId/actions", token = token)
        val actions = JSONObject(response).getJSONArray("actions")
        return List(actions.length()) { index -> parseAction(actions.getJSONObject(index)) }
    }

    suspend fun createDeviceAction(
        deviceId: String,
        type: String,
        token: String,
        applicationId: String? = null,
        expectedVersionCode: Long? = null,
        fileId: String? = null,
        expectedRevision: String? = null,
    ): DeviceAction {
        val body = JSONObject().put("type", type)
        applicationId?.let { target ->
            body.put("payload", JSONObject().put("applicationId", target).apply {
                expectedVersionCode?.let { versionCode -> put("expectedVersionCode", versionCode) }
            })
        }
        fileId?.let { target ->
            body.put("payload", JSONObject().put("fileId", target).apply {
                expectedRevision?.let { revision -> put("expectedRevision", revision) }
            })
        }
        val response = request(
            method = "POST",
            path = "/api/devices/$deviceId/actions",
            body = body.toString(),
            token = token,
        )
        return parseAction(JSONObject(response).getJSONObject("action"))
    }

    suspend fun getActionHistory(deviceId: String, token: String): List<DeviceAction> {
        val response = request(method = "GET", path = "/api/devices/$deviceId/history", token = token)
        val actions = JSONObject(response).getJSONArray("actions")
        return List(actions.length()) { index -> parseAction(actions.getJSONObject(index)) }
    }

    private fun parseAction(action: JSONObject): DeviceAction = DeviceAction(
        id = action.getString("id"),
        type = action.getString("type"),
        applicationId = action.optJSONObject("payload")?.optString("applicationId")?.ifEmpty { null },
        expectedVersionCode = action.optJSONObject("payload")?.let { payload ->
            if (payload.has("expectedVersionCode")) payload.getLong("expectedVersionCode") else null
        },
        fileId = action.optJSONObject("payload")?.optString("fileId")?.ifEmpty { null },
        expectedRevision = action.optJSONObject("payload")?.optString("expectedRevision")?.ifEmpty { null },
        origin = action.getString("origin"),
        status = action.getString("status"),
        requestedAt = action.getString("requestedAt"),
        completedAt = action.optString("completedAt").ifEmpty { null },
        resultMessage = action.optString("resultMessage").ifEmpty { null },
    )

    suspend fun sendActionResult(actionId: String, result: StoredActionResult, token: String) {
        val body = JSONObject()
            .put("succeeded", result.succeeded)
            .put("status", result.status)
            .put("message", result.message)
        result.errorCode?.let { errorCode -> body.put("errorCode", errorCode) }
        result.resultJson?.let { resultJson -> body.put("result", JSONObject(resultJson)) }
        request(
            method = "POST",
            path = "/api/actions/$actionId/result",
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
