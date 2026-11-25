package com.tcc.devicehealth.agent.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tcc.devicehealth.agent.model.AppLanguage
import com.tcc.devicehealth.agent.model.DisplayPreferences
import com.tcc.devicehealth.agent.model.ThemeMode
import com.tcc.devicehealth.agent.model.enumValueOrDefault
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.json.JSONArray

private const val PREFERENCES_NAME = "device_health_agent"
internal val Context.agentDataStore by preferencesDataStore(name = PREFERENCES_NAME)

class AgentPreferencesDataSource(
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

    suspend fun getControllerAddress(): String? = dataStore.data.first()[controllerAddressKey]

    suspend fun saveControllerAddress(address: String) {
        dataStore.edit { preferences -> preferences[controllerAddressKey] = address }
    }

    suspend fun getDisplayPreferences(): DisplayPreferences {
        val preferences = dataStore.data.first()
        return DisplayPreferences(
            themeMode = enumValueOrDefault(ThemeMode.values(), preferences[themeModeKey], ThemeMode.System),
            language = enumValueOrDefault(AppLanguage.values(), preferences[languageKey], AppLanguage.System),
        )
    }

    suspend fun saveDisplayPreferences(displayPreferences: DisplayPreferences) {
        dataStore.edit { preferences ->
            preferences[themeModeKey] = displayPreferences.themeMode.name
            preferences[languageKey] = displayPreferences.language.name
        }
    }

    suspend fun saveToken(token: String) {
        dataStore.edit { preferences -> preferences[tokenKey] = token }
    }

    suspend fun getFileTreeUris(): List<String> {
        val value = dataStore.data.first()[fileTreeUrisKey] ?: return emptyList()
        val uris = JSONArray(value)
        return List(uris.length()) { index -> uris.getString(index) }
    }

    suspend fun addFileTreeUri(uri: String) {
        dataStore.edit { preferences ->
            val stored = JSONArray(preferences[fileTreeUrisKey] ?: "[]")
            val uris = (List(stored.length()) { stored.getString(it) } + uri).distinct()
            preferences[fileTreeUrisKey] = JSONArray(uris).toString()
        }
    }

    private companion object {
        val deviceIdKey = stringPreferencesKey("device_id")
        val tokenKey = stringPreferencesKey("controller_token")
        val controllerAddressKey = stringPreferencesKey("controller_address")
        val themeModeKey = stringPreferencesKey("theme_mode")
        val languageKey = stringPreferencesKey("language")

        val fileTreeUrisKey = stringPreferencesKey("file_tree_uris")
    }
}
