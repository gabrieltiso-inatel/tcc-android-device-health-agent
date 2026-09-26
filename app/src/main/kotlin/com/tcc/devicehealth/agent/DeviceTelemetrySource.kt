package com.tcc.devicehealth.agent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import java.time.Instant

internal class DeviceTelemetrySource(
    private val context: Context,
    private val preferences: AgentPreferences,
) {
    suspend fun read(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return DeviceTelemetry(
            deviceId = preferences.getDeviceId(),
            deviceName = listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim(),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            agentVersion = BuildConfig.VERSION_NAME,
            capabilities = listOf("collectTelemetry", "collectStorageSummary", "collectAppInventory"),
            batteryPercentage = percentage,
            isCharging = isCharging,
            capturedAt = Instant.now().toString(),
        )
    }
}
