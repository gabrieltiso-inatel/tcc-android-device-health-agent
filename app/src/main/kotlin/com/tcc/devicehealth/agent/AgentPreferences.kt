package com.tcc.devicehealth.agent

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
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

    suspend fun getToken(): String? = dataStore.data.first()[tokenKey]

    suspend fun saveToken(token: String) {
        dataStore.edit { preferences -> preferences[tokenKey] = token }
    }

    private companion object {
        val deviceIdKey = stringPreferencesKey("device_id")
        val tokenKey = stringPreferencesKey("controller_token")
    }
}
