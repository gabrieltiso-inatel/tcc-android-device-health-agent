package com.tcc.devicehealth.agent.model

data class LatestDeviceSnapshots(
    val storage: StorageSummary? = null,
    val apps: ApplicationInventory? = null,
    val files: FileInventory? = null,
)
