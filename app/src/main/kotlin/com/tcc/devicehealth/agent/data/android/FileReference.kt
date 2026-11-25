package com.tcc.devicehealth.agent.data.android

import java.security.MessageDigest

internal fun fileReferenceId(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
