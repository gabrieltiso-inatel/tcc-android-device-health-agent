package com.tcc.devicehealth.agent

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
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

data class StoredCommandResult(
    val succeeded: Boolean,
    val message: String,
    val errorCode: String? = null,
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

    suspend fun getCommandResult(commandId: String): StoredCommandResult? {
        val preferences = dataStore.data.first()
        val succeeded = preferences[commandSucceededKey(commandId)] ?: return null
        val message = preferences[commandMessageKey(commandId)] ?: return null
        val errorCode = preferences[commandErrorCodeKey(commandId)]
        return StoredCommandResult(succeeded, message, errorCode)
    }

    suspend fun saveCommandResult(commandId: String, result: StoredCommandResult) {
        dataStore.edit { preferences ->
            preferences[commandSucceededKey(commandId)] = result.succeeded
            preferences[commandMessageKey(commandId)] = result.message
            result.errorCode?.let { errorCode ->
                preferences[commandErrorCodeKey(commandId)] = errorCode
            }
        }
    }

    private companion object {
        val deviceIdKey = stringPreferencesKey("device_id")
        val tokenKey = stringPreferencesKey("controller_token")

        fun commandSucceededKey(commandId: String) = booleanPreferencesKey("command_${commandId}_succeeded")

        fun commandMessageKey(commandId: String) = stringPreferencesKey("command_${commandId}_message")

        fun commandErrorCodeKey(commandId: String) = stringPreferencesKey("command_${commandId}_error_code")
    }
}
