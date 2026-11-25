package com.tcc.devicehealth.agent.data.remote

import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.ActionExecutionResult
import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.DeviceTelemetry
import com.tcc.devicehealth.agent.model.FileInventory
import com.tcc.devicehealth.agent.model.LatestDeviceSnapshots
import com.tcc.devicehealth.agent.model.StorageSummary
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class ControllerApiUnavailableException(cause: Throwable? = null) :
    Exception("Controller is temporarily unavailable", cause)

internal class ControllerApiResponseException(val statusCode: Int) :
    Exception("Controller rejected the request")

internal class ControllerApiClient(private val baseUrl: suspend () -> String) {
    suspend fun pair(code: String, telemetry: DeviceTelemetry): String {
        val response = request("POST", "/api/devices/pair", ControllerApiJsonAdapter.encodePairing(code, telemetry).toString())
        return JSONObject(response).getString("token")
    }

    suspend fun sendTelemetry(telemetry: DeviceTelemetry, token: String) {
        request("POST", "/api/devices/${telemetry.deviceId}/telemetry", ControllerApiJsonAdapter.encodeTelemetry(telemetry).toString(), token)
    }

    suspend fun sendStorageSummary(deviceId: String, summary: StorageSummary, token: String) {
        request("POST", "/api/devices/$deviceId/storage", ControllerApiJsonAdapter.encodeCollectedData(summary).toString(), token)
    }

    suspend fun sendAppInventory(deviceId: String, inventory: ApplicationInventory, token: String) {
        request("POST", "/api/devices/$deviceId/apps", ControllerApiJsonAdapter.encodeCollectedData(inventory).toString(), token)
    }

    suspend fun sendFileInventory(deviceId: String, inventory: FileInventory, token: String) {
        request("POST", "/api/devices/$deviceId/files", ControllerApiJsonAdapter.encodeCollectedData(inventory).toString(), token)
    }

    suspend fun getPendingActions(deviceId: String, token: String): List<Action> =
        ControllerApiJsonAdapter.decodeActions(request("GET", "/api/devices/$deviceId/actions", token = token))

    suspend fun createAction(
        deviceId: String,
        type: String,
        token: String,
        applicationId: String? = null,
        expectedVersionCode: Long? = null,
        fileId: String? = null,
        expectedRevision: String? = null,
    ): Action {
        val body = ControllerApiJsonAdapter.encodeActionRequest(type, applicationId, expectedVersionCode, fileId, expectedRevision)
        val response = request("POST", "/api/devices/$deviceId/actions", body.toString(), token)
        return ControllerApiJsonAdapter.decodeAction(JSONObject(response).getJSONObject("action"))
    }

    suspend fun getActionHistory(deviceId: String, token: String): List<Action> =
        ControllerApiJsonAdapter.decodeActions(request("GET", "/api/devices/$deviceId/history", token = token))

    suspend fun getSnapshots(deviceId: String, token: String): LatestDeviceSnapshots =
        ControllerApiJsonAdapter.decodeSnapshots(request("GET", "/api/devices/$deviceId/state", token = token))

    suspend fun sendActionResult(actionId: String, result: ActionExecutionResult, token: String) {
        request("POST", "/api/actions/$actionId/result", ControllerApiJsonAdapter.encodeActionResult(result).toString(), token)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
    ): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
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
                    throw ControllerApiUnavailableException()
                else -> throw ControllerApiResponseException(statusCode)
            }
        } catch (error: ControllerApiUnavailableException) {
            throw error
        } catch (error: IOException) {
            throw ControllerApiUnavailableException(error)
        } finally {
            connection?.disconnect()
        }
    }
}

fun normalizeControllerAddress(address: String): String {
    val uri = URI(address.trim())
    require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank())
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    require(uri.path.isNullOrEmpty() || uri.path == "/")
    require(uri.port == -1 || uri.port in 1..65535)
    val port = if (uri.port == -1 && uri.scheme == "http") 3000 else uri.port
    return URI(uri.scheme, null, uri.host, port, null, null, null).toString()
}
