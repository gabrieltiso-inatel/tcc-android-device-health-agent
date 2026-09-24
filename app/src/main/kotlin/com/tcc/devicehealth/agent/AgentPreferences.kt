package com.tcc.devicehealth.agent

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID

private const val PREFERENCES_NAME = "device_health_agent"
private val Context.agentDataStore by preferencesDataStore(
    name = PREFERENCES_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, PREFERENCES_NAME))
    },
)

class AgentPreferences(
    context: Context,
    private val createIdentifier: () -> String = { UUID.randomUUID().toString() },
) {
    private val dataStore = context.agentDataStore

    suspend fun getDeviceId(): String {
        var deviceId = ""
        dataStore.edit { preferences ->
            deviceId = preferences[deviceIdKey] ?: createIdentifier().also { generatedId ->
                preferences[deviceIdKey] = generatedId
            }
        }
        return deviceId
    }

    private companion object {
        val deviceIdKey = stringPreferencesKey("device_id")
    }
}
