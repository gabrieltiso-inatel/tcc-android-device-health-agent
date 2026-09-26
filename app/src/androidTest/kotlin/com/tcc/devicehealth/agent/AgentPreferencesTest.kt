package com.tcc.devicehealth.agent

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentPreferencesTest {
    @Test
    fun returnsThePersistedDeviceIdentifier() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = AgentPreferences(context)

        val firstIdentifier = preferences.getDeviceId()
        val secondIdentifier = preferences.getDeviceId()

        assertEquals(firstIdentifier, secondIdentifier)
    }

    @Test
    fun storesAnActionResult() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = AgentPreferences(context)
        val result = StoredActionResult(succeeded = true, message = "Telemetry sent")

        preferences.saveActionResult("test-action", result)

        assertEquals(result, preferences.getActionResult("test-action"))
    }

}
