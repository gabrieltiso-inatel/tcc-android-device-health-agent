package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ControllerAddressTest {
    @Test
    fun localAddressUsesDefaultPort() {
        assertEquals("http://192.168.1.10:3000", normalizeControllerAddress(" http://192.168.1.10/ "))
    }

    @Test
    fun remoteAddressUsesDefaultHttpsPort() {
        assertEquals("https://controller.example.com", normalizeControllerAddress("https://controller.example.com/"))
    }

    @Test
    fun explicitPortIsPreserved() {
        assertEquals("http://10.0.2.2:8080", normalizeControllerAddress("http://10.0.2.2:8080"))
    }

    @Test
    fun invalidAddressesAreRejected() {
        listOf("192.168.1.10", "ftp://example.com", "https://user:secret@example.com",
            "https://example.com/path", "https://example.com?token=secret", "https://example.com#fragment",
            "http://example.com:0", "http://example.com:65536").forEach { address ->
            assertThrows(IllegalArgumentException::class.java) { normalizeControllerAddress(address) }
        }
    }
}
