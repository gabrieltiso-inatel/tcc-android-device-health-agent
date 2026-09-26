package com.tcc.devicehealth.agent

import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.time.Instant

internal class StorageDataSource {
    fun collectSummary(): CommandExecution {
        val storage = StatFs(Environment.getDataDirectory().path)
        val totalBytes = storage.totalBytes
        val availableBytes = storage.availableBytes
        val result = JSONObject()
            .put("totalBytes", totalBytes)
            .put("usedBytes", totalBytes - availableBytes)
            .put("availableBytes", availableBytes)
            .put("capturedAt", Instant.now().toString())

        return CommandExecution("Storage summary collected", result.toString())
    }
}
