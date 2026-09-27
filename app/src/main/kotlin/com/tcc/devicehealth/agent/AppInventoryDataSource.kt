package com.tcc.devicehealth.agent

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

internal class AppInventoryDataSource(
    private val packageManager: PackageManager,
    private val agentPackageName: String,
) {
    fun collect(): ActionExecution {
        val applications = installedApplications()
            .filterNot { application -> application.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .mapNotNull(::readApp)
            .sortedBy { application -> application.getString("name").lowercase() }
        val result = JSONObject()
            .put("capturedAt", Instant.now().toString())
            .put("apps", JSONArray(applications))
        return ActionExecution("App inventory collected", result.toString())
    }

    private fun installedApplications(): List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(0)
    }

    private fun readApp(application: ApplicationInfo): JSONObject? {
        val packageInfo = try {
            packageManager.getPackageInfo(application.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return JSONObject()
            .put("applicationId", application.packageName)
            .put("name", packageManager.getApplicationLabel(application).toString().take(255))
            .put("canRemove", application.packageName != agentPackageName)
            .put("versionName", packageInfo.versionName.orEmpty().take(120))
            .put("versionCode", packageInfo.longVersionCodeValue())
            .put("installedAt", Instant.ofEpochMilli(packageInfo.firstInstallTime).toString())
            .put("updatedAt", Instant.ofEpochMilli(packageInfo.lastUpdateTime).toString())
    }

    private fun android.content.pm.PackageInfo.longVersionCodeValue(): Long = if (Build.VERSION.SDK_INT >= 28) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
}
