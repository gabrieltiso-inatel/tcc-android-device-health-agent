package com.tcc.devicehealth.agent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

internal class ApprovalNotificationDataSource(context: Context) {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

    fun notify(approval: PendingApproval) {
        val name = runCatching {
            val packageManager = applicationContext.packageManager
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(approval.applicationId, 0)).toString()
        }.getOrDefault(approval.applicationId)
        show(approval.actionId, applicationContext.getString(R.string.notification_review_removal, name))
    }

    fun notifyFile(approval: PendingFileApproval) {
        val name = approval.name ?: applicationContext.getString(R.string.file_previously_selected)
        show(approval.actionId, applicationContext.getString(R.string.notification_review_removal, name))
    }

    private fun show(actionId: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(channelId, applicationContext.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId(actionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId(actionId), notification)
    }

    fun cancel(actionId: String) {
        notificationManager.cancel(notificationId(actionId))
    }

    private fun notificationId(actionId: String): Int = actionId.hashCode()

    private companion object {
        const val channelId = "action-approvals"
    }
}
