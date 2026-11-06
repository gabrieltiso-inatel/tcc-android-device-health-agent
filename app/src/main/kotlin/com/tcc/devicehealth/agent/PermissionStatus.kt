package com.tcc.devicehealth.agent

data class PermissionStatus(
    val notificationsGranted: Boolean = true,
    val mediaGranted: Boolean = false,
)
