package com.tcc.devicehealth.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tcc.devicehealth.agent.data.local.AgentPreferencesDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentPreferencesDataSourceTest {
    private val preferences = AgentPreferencesDataSource(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun returnsThePersistedDeviceIdentifier() = runBlocking {
        val firstIdentifier = preferences.getDeviceId()
        val secondIdentifier = preferences.getDeviceId()

        assertEquals(firstIdentifier, secondIdentifier)
    }
}
