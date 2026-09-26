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

data class StoredActionResult(
    val succeeded: Boolean,
    val message: String,
    val errorCode: String? = null,
    val resultJson: String? = null,
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

    suspend fun getActionResult(actionId: String): StoredActionResult? {
        val preferences = dataStore.data.first()
        val succeeded = preferences[actionSucceededKey(actionId)] ?: return null
        val message = preferences[actionMessageKey(actionId)] ?: return null
        val errorCode = preferences[actionErrorCodeKey(actionId)]
        val resultJson = preferences[actionResultKey(actionId)]
        return StoredActionResult(succeeded, message, errorCode, resultJson)
    }

    suspend fun saveActionResult(actionId: String, result: StoredActionResult) {
        dataStore.edit { preferences ->
            preferences[actionSucceededKey(actionId)] = result.succeeded
            preferences[actionMessageKey(actionId)] = result.message
            result.errorCode?.let { errorCode ->
                preferences[actionErrorCodeKey(actionId)] = errorCode
            }
            result.resultJson?.let { resultJson ->
                preferences[actionResultKey(actionId)] = resultJson
            }
        }
    }

    private companion object {
        val deviceIdKey = stringPreferencesKey("device_id")
        val tokenKey = stringPreferencesKey("controller_token")

        fun actionSucceededKey(actionId: String) = booleanPreferencesKey("action_${actionId}_succeeded")

        fun actionMessageKey(actionId: String) = stringPreferencesKey("action_${actionId}_message")

        fun actionErrorCodeKey(actionId: String) = stringPreferencesKey("action_${actionId}_error_code")

        fun actionResultKey(actionId: String) = stringPreferencesKey("action_${actionId}_result")
    }
}
