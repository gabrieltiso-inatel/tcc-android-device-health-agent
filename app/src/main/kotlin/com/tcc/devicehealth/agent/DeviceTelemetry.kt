package com.tcc.devicehealth.agent

data class DeviceTelemetry(
    val deviceId: String,
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val agentVersion: String,
    val capabilities: List<String>,
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val capturedAt: String,
)

data class DeviceAction(
    val id: String,
    val type: String,
    val origin: String,
    val status: String,
    val requestedAt: String,
    val completedAt: String? = null,
    val resultMessage: String? = null,
)

data class ActionExecution(
    val message: String,
    val resultJson: String? = null,
)
