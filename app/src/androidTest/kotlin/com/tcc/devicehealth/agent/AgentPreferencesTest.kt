package com.tcc.devicehealth.agent

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentPreferencesTest {
    @Test
    fun persistsMediaApprovalForProcessRestart() = runBlocking {
        val preferences = AgentPreferences(ApplicationProvider.getApplicationContext<android.content.Context>())
        val approval = PendingFileApproval("media-test", "opaque-id", "revision", "Photo.png",
            "content://media/external/images/media/1", platformRequestStarted = true)

        preferences.savePendingFileApproval(approval)

        assertEquals(approval, preferences.getPendingFileApprovals().first { it.actionId == approval.actionId })
        preferences.removePendingFileApproval(approval.actionId)
    }

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
