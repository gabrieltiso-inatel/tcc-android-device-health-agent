package com.tcc.devicehealth.agent

import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.time.Instant

internal class StorageDataSource {
    fun collectSummary(): ActionExecution {
        val storage = StatFs(Environment.getDataDirectory().path)
        val totalBytes = storage.totalBytes
        val availableBytes = storage.availableBytes
        val result = JSONObject()
            .put("totalBytes", totalBytes)
            .put("usedBytes", totalBytes - availableBytes)
            .put("availableBytes", availableBytes)
            .put("capturedAt", Instant.now().toString())

        return ActionExecution("Storage summary collected", result.toString())
    }
}
