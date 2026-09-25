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
    fun storesACommandResult() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = AgentPreferences(context)
        val result = StoredCommandResult(succeeded = true, message = "Telemetry sent")

        preferences.saveCommandResult("test-command", result)

        assertEquals(result, preferences.getCommandResult("test-command"))
    }

}
