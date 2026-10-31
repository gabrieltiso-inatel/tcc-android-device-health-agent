package com.tcc.devicehealth.agent

import java.net.URI

fun normalizeControllerAddress(address: String): String {
    val uri = URI(address.trim())
    require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank())
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    require(uri.path.isNullOrEmpty() || uri.path == "/")
    require(uri.port == -1 || uri.port in 1..65535)
    val port = if (uri.port == -1 && uri.scheme == "http") 3000 else uri.port
    return URI(uri.scheme, null, uri.host, port, null, null, null).toString()
}
