package com.tcc.devicehealth.agent.data.local

import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.CollectedDeviceData
import com.tcc.devicehealth.agent.model.FileInventory
import com.tcc.devicehealth.agent.model.FileItem
import com.tcc.devicehealth.agent.model.FileSource
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.model.StorageSummary
import org.json.JSONArray
import org.json.JSONObject

internal object StoredCollectedDataJsonAdapter {
    fun encode(result: CollectedDeviceData): JSONObject = when (result) {
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

    fun decode(inventory: JSONObject): CollectedDeviceData = when {
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
