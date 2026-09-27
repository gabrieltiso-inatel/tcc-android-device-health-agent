package com.tcc.devicehealth.agent

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri

internal class ApplicationNotFoundException : Exception()

internal class ApplicationChangedException : Exception()

internal class ApplicationNotAllowedException : Exception()

internal class ApplicationRemovalDataSource(
    private val packageManager: PackageManager,
    private val agentPackageName: String,
) {
    fun prepare(action: DeviceAction): ActionExecution {
        validate(action)
        return ActionExecution(
            message = "Application removal requires approval",
            approvalRequired = true,
        )
    }

    companion object {
        fun createIntent(approval: PendingApproval): Intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:${approval.applicationId}"),
        ).putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }

    fun isRemoved(approval: PendingApproval): Boolean = try {
        packageManager.getApplicationInfo(approval.applicationId, 0)
        false
    } catch (_: PackageManager.NameNotFoundException) {
        true
    }

    fun validate(approval: PendingApproval) {
        validate(
            DeviceAction(
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

    private fun validate(action: DeviceAction) {
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

    private fun android.content.pm.PackageInfo.longVersionCodeValue(): Long = if (android.os.Build.VERSION.SDK_INT >= 28) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
}
