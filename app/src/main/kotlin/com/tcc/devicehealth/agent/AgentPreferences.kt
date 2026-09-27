package com.tcc.devicehealth.agent

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val PREFERENCES_NAME = "device_health_agent"
private val Context.agentDataStore by preferencesDataStore(name = PREFERENCES_NAME)

data class StoredActionResult(
    val succeeded: Boolean,
    val message: String,
    val status: String = if (succeeded) "completed" else "failed",
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
        val status = preferences[actionStatusKey(actionId)] ?: if (succeeded) "completed" else "failed"
        val errorCode = preferences[actionErrorCodeKey(actionId)]
        val resultJson = preferences[actionResultKey(actionId)]
        return StoredActionResult(succeeded, message, status, errorCode, resultJson)
    }

    suspend fun saveActionResult(actionId: String, result: StoredActionResult) {
        dataStore.edit { preferences ->
            preferences[actionSucceededKey(actionId)] = result.succeeded
            preferences[actionMessageKey(actionId)] = result.message
            preferences[actionStatusKey(actionId)] = result.status
            result.errorCode?.let { errorCode ->
                preferences[actionErrorCodeKey(actionId)] = errorCode
            }
            result.resultJson?.let { resultJson ->
                preferences[actionResultKey(actionId)] = resultJson
            }
        }
    }

    suspend fun getPendingApprovals(): List<PendingApproval> {
        val value = dataStore.data.first()[pendingApprovalsKey] ?: return emptyList()
        val approvals = JSONArray(value)
        return List(approvals.length()) { index ->
            val approval = approvals.getJSONObject(index)
            PendingApproval(
                actionId = approval.getString("actionId"),
                applicationId = approval.getString("applicationId"),
                expectedVersionCode = if (approval.has("expectedVersionCode")) approval.getLong("expectedVersionCode") else null,
            )
        }
    }

    suspend fun savePendingApproval(approval: PendingApproval) {
        val approvals = getPendingApprovals().filterNot { it.actionId == approval.actionId } + approval
        dataStore.edit { preferences -> preferences[pendingApprovalsKey] = approvalsJson(approvals) }
    }

    suspend fun removePendingApproval(actionId: String) {
        val approvals = getPendingApprovals().filterNot { it.actionId == actionId }
        dataStore.edit { preferences ->
            if (approvals.isEmpty()) preferences.remove(pendingApprovalsKey) else preferences[pendingApprovalsKey] = approvalsJson(approvals)
        }
    }

    private fun approvalsJson(approvals: List<PendingApproval>): String = JSONArray().apply {
        approvals.forEach { approval ->
            put(JSONObject().apply {
                put("actionId", approval.actionId)
                put("applicationId", approval.applicationId)
                approval.expectedVersionCode?.let { versionCode -> put("expectedVersionCode", versionCode) }
            })
        }
    }.toString()

    private companion object {
        val deviceIdKey = stringPreferencesKey("device_id")
        val tokenKey = stringPreferencesKey("controller_token")

        fun actionSucceededKey(actionId: String) = booleanPreferencesKey("action_${actionId}_succeeded")

        fun actionStatusKey(actionId: String) = stringPreferencesKey("action_${actionId}_status")

        fun actionMessageKey(actionId: String) = stringPreferencesKey("action_${actionId}_message")

        fun actionErrorCodeKey(actionId: String) = stringPreferencesKey("action_${actionId}_error_code")

        fun actionResultKey(actionId: String) = stringPreferencesKey("action_${actionId}_result")

        val pendingApprovalsKey = stringPreferencesKey("pending_approvals")
    }
}
