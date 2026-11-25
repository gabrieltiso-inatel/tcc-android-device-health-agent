package com.tcc.devicehealth.agent.data.android

import android.os.Environment
import android.os.StatFs
import com.tcc.devicehealth.agent.model.StorageSummary
import java.time.Instant

internal class StorageDataSource {
    fun collectSummary(): StorageSummary {
        val storage = StatFs(Environment.getDataDirectory().path)
        val totalBytes = storage.totalBytes
        val availableBytes = storage.availableBytes
        return StorageSummary(
            totalBytes = totalBytes,
            usedBytes = totalBytes - availableBytes,
            availableBytes = availableBytes,
            capturedAt = Instant.now().toString(),
        )
    }
}
