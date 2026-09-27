package com.tcc.devicehealth.agent

import java.security.MessageDigest

internal fun fileReferenceId(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
