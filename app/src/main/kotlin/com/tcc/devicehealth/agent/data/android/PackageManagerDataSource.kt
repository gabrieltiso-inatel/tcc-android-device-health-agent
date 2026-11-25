package com.tcc.devicehealth.agent.data.android

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import java.time.Instant

internal class ApplicationNotFoundException : Exception()

internal class ApplicationChangedException : Exception()

internal class ApplicationNotAllowedException : Exception()

internal class PackageManagerDataSource(
    private val packageManager: PackageManager,
    private val agentPackageName: String,
) {
    companion object {
        fun createRemovalIntent(approval: PendingApplicationRemoval): Intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${approval.applicationId}"),
        ).putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }

    fun isRemoved(approval: PendingApplicationRemoval): Boolean = try {
        packageManager.getApplicationInfo(approval.applicationId, 0)
        false
    } catch (_: PackageManager.NameNotFoundException) {
        true
    }

    fun validateRemoval(approval: PendingApplicationRemoval) {
        validateRemoval(
            Action(
                id = approval.actionId,
                type = "removeApplication",
                applicationId = approval.applicationId,
                expectedVersionCode = approval.expectedVersionCode,
                origin = "controller",
                status = "awaiting_approval",
                requestedAt = "",
            ),
        )
    }

    fun validateRemoval(action: Action) {
        val applicationId = action.applicationId ?: throw ApplicationNotFoundException()
        if (applicationId == agentPackageName) {
            throw ApplicationNotAllowedException()
        }
        val applicationInfo = try {
            packageManager.getApplicationInfo(applicationId, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            throw ApplicationNotFoundException()
        }
        if (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            throw ApplicationNotAllowedException()
        }
        if (action.expectedVersionCode != null && versionCode(applicationId) != action.expectedVersionCode) {
            throw ApplicationChangedException()
        }
    }

    private fun versionCode(applicationId: String): Long = packageManager.getPackageInfo(applicationId, 0).longVersionCodeValue()

    fun collect(): ApplicationInventory {
        val applications = installedApplications()
            .filterNot { application -> application.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .mapNotNull(::readApp)
            .sortedBy { application -> application.name.lowercase() }
        return ApplicationInventory(Instant.now().toString(), applications)
    }

    private fun installedApplications(): List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(0)
    }

    private fun readApp(application: ApplicationInfo): InstalledApp? {
        val packageInfo = try {
            packageManager.getPackageInfo(application.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return InstalledApp(
            applicationId = application.packageName,
            name = packageManager.getApplicationLabel(application).toString().take(255),
            canRemove = application.packageName != agentPackageName,
            versionName = packageInfo.versionName.orEmpty().take(120),
            versionCode = packageInfo.longVersionCodeValue(),
            installedAt = Instant.ofEpochMilli(packageInfo.firstInstallTime).toString(),
            updatedAt = Instant.ofEpochMilli(packageInfo.lastUpdateTime).toString(),
        )
    }

    private fun PackageInfo.longVersionCodeValue(): Long = if (Build.VERSION.SDK_INT >= 28) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
}
