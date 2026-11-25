package com.tcc.devicehealth.agent.data.remote

import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.ActionExecutionResult
import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.CollectedDeviceData
import com.tcc.devicehealth.agent.model.DeviceTelemetry
import com.tcc.devicehealth.agent.model.FileInventory
import com.tcc.devicehealth.agent.model.FileItem
import com.tcc.devicehealth.agent.model.FileSource
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.model.LatestDeviceSnapshots
import com.tcc.devicehealth.agent.model.StorageSummary
import org.json.JSONArray
import org.json.JSONObject

internal object ControllerApiJsonAdapter {
    fun encodePairing(code: String, telemetry: DeviceTelemetry): JSONObject = JSONObject()
        .put("code", code)
        .put("deviceId", telemetry.deviceId)
        .put("deviceName", telemetry.deviceName)

    fun encodeTelemetry(telemetry: DeviceTelemetry): JSONObject = JSONObject()
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

    fun encodeActionRequest(
        type: String,
        applicationId: String?,
        expectedVersionCode: Long?,
        fileId: String?,
        expectedRevision: String?,
    ): JSONObject = JSONObject().put("type", type).apply {
        applicationId?.let { target ->
            put("payload", JSONObject().put("applicationId", target).apply {
                expectedVersionCode?.let { put("expectedVersionCode", it) }
            })
        }
        fileId?.let { target ->
            put("payload", JSONObject().put("fileId", target).apply {
                expectedRevision?.let { put("expectedRevision", it) }
            })
        }
    }

    fun encodeActionResult(result: ActionExecutionResult): JSONObject = JSONObject()
        .put("succeeded", result.succeeded)
        .put("status", result.status)
        .put("message", result.message)
        .apply {
            result.errorCode?.let { put("errorCode", it) }
            result.collectedData?.let { put("result", encodeCollectedData(it)) }
        }

    fun encodeCollectedData(result: CollectedDeviceData): JSONObject = when (result) {
        is StorageSummary -> JSONObject()
            .put("totalBytes", result.totalBytes)
            .put("usedBytes", result.usedBytes)
            .put("availableBytes", result.availableBytes)
            .put("capturedAt", result.capturedAt)
        is ApplicationInventory -> JSONObject()
            .put("capturedAt", result.capturedAt)
            .put("apps", JSONArray().apply { result.apps.forEach { put(application(it)) } })
        is FileInventory -> JSONObject()
            .put("capturedAt", result.capturedAt)
            .put("sources", JSONArray().apply { result.sources.forEach { put(source(it)) } })
            .put("files", JSONArray().apply { result.files.forEach { put(file(it)) } })
    }

    private fun application(app: InstalledApp): JSONObject = JSONObject()
        .put("applicationId", app.applicationId)
        .put("name", app.name)
        .put("canRemove", app.canRemove)
        .put("versionName", app.versionName)
        .put("versionCode", app.versionCode)
        .put("installedAt", app.installedAt)
        .put("updatedAt", app.updatedAt)

    private fun source(source: FileSource): JSONObject = JSONObject()
        .put("sourceId", source.sourceId)
        .put("label", source.label)
        .put("kind", source.kind)
        .put("authorization", source.authorization)
        .apply { source.itemCount?.let { put("itemCount", it) } }

    private fun file(file: FileItem): JSONObject = JSONObject()
        .put("fileId", file.fileId)
        .put("sourceId", file.sourceId)
        .put("name", file.name)
        .put("kind", file.kind)
        .put("sizeBytes", file.sizeBytes)
        .put("canRemove", file.canRemove)
        .put("revision", file.revision)
        .apply {
            file.mimeType?.let { put("mimeType", it) }
            file.modifiedAt?.let { put("modifiedAt", it) }
            file.displayPath?.let { put("displayPath", it) }
        }

    fun decodeActions(response: String): List<Action> {
        val actions = JSONObject(response).getJSONArray("actions")
        return List(actions.length()) { index -> decodeAction(actions.getJSONObject(index)) }
    }

    fun decodeAction(action: JSONObject): Action = Action(
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

    fun decodeSnapshots(response: String): LatestDeviceSnapshots {
        val snapshots = JSONObject(response)
        return LatestDeviceSnapshots(
            storage = snapshots.optJSONObject("storage")?.let { decodeCollectedData(it) as StorageSummary },
            apps = snapshots.optJSONObject("apps")?.let { decodeCollectedData(it) as ApplicationInventory },
            files = snapshots.optJSONObject("files")?.let { decodeCollectedData(it) as FileInventory },
        )
    }

    fun decodeCollectedData(inventory: JSONObject): CollectedDeviceData = when {
        inventory.has("totalBytes") -> StorageSummary(
            inventory.getLong("totalBytes"), inventory.getLong("usedBytes"),
            inventory.getLong("availableBytes"), inventory.getString("capturedAt"),
        )
        inventory.has("apps") -> {
            val apps = inventory.getJSONArray("apps")
            ApplicationInventory(inventory.getString("capturedAt"), List(apps.length()) { index ->
                val app = apps.getJSONObject(index)
                InstalledApp(
                    app.getString("applicationId"), app.getString("name"),
                    app.getBoolean("canRemove"), app.getString("versionName"), app.getLong("versionCode"),
                    app.optString("installedAt"), app.optString("updatedAt"),
                )
            })
        }
        else -> {
            val sources = inventory.getJSONArray("sources")
            val files = inventory.getJSONArray("files")
            FileInventory(
                capturedAt = inventory.getString("capturedAt"),
                sources = List(sources.length()) { index ->
                    val source = sources.getJSONObject(index)
                    FileSource(
                        source.getString("sourceId"), source.getString("label"),
                        source.getString("kind"), source.getString("authorization"),
                        if (source.has("itemCount")) source.getInt("itemCount") else null,
                    )
                },
                files = List(files.length()) { index ->
                    val file = files.getJSONObject(index)
                    FileItem(
                        fileId = file.getString("fileId"), sourceId = file.getString("sourceId"),
                        name = file.getString("name"), kind = file.getString("kind"),
                        mimeType = file.optString("mimeType").ifEmpty { null },
                        sizeBytes = file.getLong("sizeBytes"),
                        modifiedAt = file.optString("modifiedAt").ifEmpty { null },
                        displayPath = file.optString("displayPath").ifEmpty { null },
                        canRemove = file.getBoolean("canRemove"), revision = file.getString("revision"),
                    )
                },
            )
        }
    }
}
