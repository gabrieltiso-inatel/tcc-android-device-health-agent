package com.tcc.devicehealth.agent.model

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
