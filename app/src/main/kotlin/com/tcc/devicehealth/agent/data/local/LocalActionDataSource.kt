package com.tcc.devicehealth.agent.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tcc.devicehealth.agent.model.ActionExecutionResult
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class LocalActionDataSource(context: Context) {
    private val dataStore = context.agentDataStore

    suspend fun getActionResult(actionId: String): ActionExecutionResult? {
        return readActionResult(dataStore.data.first(), actionId)
    }

    private fun readActionResult(preferences: Preferences, actionId: String): ActionExecutionResult? {
        val succeeded = preferences[actionSucceededKey(actionId)] ?: return null
        val message = preferences[actionMessageKey(actionId)] ?: return null
        val status = preferences[actionStatusKey(actionId)] ?: if (succeeded) "completed" else "failed"
        return ActionExecutionResult(
            succeeded, message, status, preferences[actionErrorCodeKey(actionId)],
            preferences[actionResultKey(actionId)]?.let { StoredCollectedDataJsonAdapter.decode(JSONObject(it)) },
        )
    }

    suspend fun saveActionResult(actionId: String, result: ActionExecutionResult): ActionExecutionResult {
        var accepted = result
        dataStore.edit { preferences ->
            val previous = readActionResult(preferences, actionId)
            if (previous != null && previous.status != "awaiting_approval") {
                accepted = previous
            } else {
                preferences[actionSucceededKey(actionId)] = result.succeeded
                preferences[actionMessageKey(actionId)] = result.message
                preferences[actionStatusKey(actionId)] = result.status
                if (result.errorCode == null) preferences.remove(actionErrorCodeKey(actionId))
                else preferences[actionErrorCodeKey(actionId)] = result.errorCode
                if (result.collectedData == null) preferences.remove(actionResultKey(actionId))
                else preferences[actionResultKey(actionId)] = StoredCollectedDataJsonAdapter.encode(result.collectedData).toString()
            }
        }
        return accepted
    }

    suspend fun getPendingApplicationRemovals(): List<PendingApplicationRemoval> {
        return applicationApprovals(dataStore.data.first()[pendingApprovalsKey])
    }

    private fun applicationApprovals(value: String?): List<PendingApplicationRemoval> {
        val approvals = JSONArray(value ?: "[]")
        return List(approvals.length()) { index ->
            val approval = approvals.getJSONObject(index)
            PendingApplicationRemoval(
                actionId = approval.getString("actionId"),
                applicationId = approval.getString("applicationId"),
                expectedVersionCode = if (approval.has("expectedVersionCode")) approval.getLong("expectedVersionCode") else null,
            )
        }
    }

    suspend fun savePendingApplicationRemoval(approval: PendingApplicationRemoval) {
        dataStore.edit { preferences ->
            val approvals = applicationApprovals(preferences[pendingApprovalsKey]).filterNot { it.actionId == approval.actionId } + approval
            preferences[pendingApprovalsKey] = approvalsJson(approvals)
        }
    }

    suspend fun removePendingApplicationRemoval(actionId: String) {
        dataStore.edit { preferences ->
            val approvals = applicationApprovals(preferences[pendingApprovalsKey]).filterNot { it.actionId == actionId }
            if (approvals.isEmpty()) preferences.remove(pendingApprovalsKey) else preferences[pendingApprovalsKey] = approvalsJson(approvals)
        }
    }

    suspend fun getPendingFileRemovals(): List<PendingFileRemoval> {
        return fileApprovals(dataStore.data.first()[pendingFileApprovalsKey])
    }

    private fun fileApprovals(value: String?): List<PendingFileRemoval> {
        val approvals = JSONArray(value ?: "[]")
        return List(approvals.length()) { index ->
            val approval = approvals.getJSONObject(index)
            PendingFileRemoval(
                actionId = approval.getString("actionId"),
                fileId = approval.getString("fileId"),
                expectedRevision = approval.optString("expectedRevision").ifEmpty { null },
                name = approval.optString("name").ifEmpty { null },
                mediaUri = approval.optString("mediaUri").ifEmpty { null },
                platformRequestStarted = approval.optBoolean("platformRequestStarted", false),
            )
        }
    }

    suspend fun savePendingFileRemoval(approval: PendingFileRemoval) {
        dataStore.edit { preferences ->
            val approvals = fileApprovals(preferences[pendingFileApprovalsKey]).filterNot { it.actionId == approval.actionId } + approval
            preferences[pendingFileApprovalsKey] = fileApprovalsJson(approvals)
        }
    }

    suspend fun removePendingFileRemoval(actionId: String) {
        dataStore.edit { preferences ->
            val approvals = fileApprovals(preferences[pendingFileApprovalsKey]).filterNot { it.actionId == actionId }
            if (approvals.isEmpty()) preferences.remove(pendingFileApprovalsKey) else preferences[pendingFileApprovalsKey] = fileApprovalsJson(approvals)
        }
    }

    private fun approvalsJson(approvals: List<PendingApplicationRemoval>): String = JSONArray().apply {
        approvals.forEach { approval ->
            put(JSONObject().apply {
                put("actionId", approval.actionId)
                put("applicationId", approval.applicationId)
                approval.expectedVersionCode?.let { versionCode -> put("expectedVersionCode", versionCode) }
            })
        }
    }.toString()

    private fun fileApprovalsJson(approvals: List<PendingFileRemoval>): String = JSONArray().apply {
        approvals.forEach { approval ->
            put(JSONObject().apply {
                put("actionId", approval.actionId)
                put("fileId", approval.fileId)
                approval.expectedRevision?.let { put("expectedRevision", it) }
                approval.name?.let { put("name", it) }
                approval.mediaUri?.let { put("mediaUri", it) }
                put("platformRequestStarted", approval.platformRequestStarted)
            })
        }
    }.toString()

    private companion object {
        fun actionSucceededKey(actionId: String) = booleanPreferencesKey("action_${actionId}_succeeded")

        fun actionStatusKey(actionId: String) = stringPreferencesKey("action_${actionId}_status")

        fun actionMessageKey(actionId: String) = stringPreferencesKey("action_${actionId}_message")

        fun actionErrorCodeKey(actionId: String) = stringPreferencesKey("action_${actionId}_error_code")

        fun actionResultKey(actionId: String) = stringPreferencesKey("action_${actionId}_result")

        val pendingApprovalsKey = stringPreferencesKey("pending_approvals")
        val pendingFileApprovalsKey = stringPreferencesKey("pending_file_approvals")
    }
}
