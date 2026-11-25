package com.tcc.devicehealth.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.tcc.devicehealth.agent.data.local.LocalActionDataSource
import com.tcc.devicehealth.agent.model.ActionExecutionResult
import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.CollectedDeviceData
import com.tcc.devicehealth.agent.model.FileInventory
import com.tcc.devicehealth.agent.model.FileItem
import com.tcc.devicehealth.agent.model.FileSource
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import com.tcc.devicehealth.agent.model.StorageSummary
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalActionDataSourceTest {
    private val preferences = LocalActionDataSource(ApplicationProvider.getApplicationContext<Context>())

    @Suppress("UNCHECKED_CAST")
    private val dataStore = LocalActionDataSource::class.java.getDeclaredField("dataStore").let { field ->
        field.isAccessible = true
        field.get(preferences) as DataStore<Preferences>
    }

    @Test
    fun persistsMediaRemovalForProcessRestart() = runBlocking {
        val approval = PendingFileRemoval(
            actionId = UUID.randomUUID().toString(), fileId = "opaque-id", expectedRevision = "revision",
            name = "Photo.png", mediaUri = "content://media/external/images/media/1", platformRequestStarted = true,
        )

        try {
            preferences.savePendingFileRemoval(approval)

            assertEquals(approval, preferences.getPendingFileRemovals().first { it.actionId == approval.actionId })
        } finally {
            preferences.removePendingFileRemoval(approval.actionId)
        }
    }

    @Test
    fun storesAnActionResult() = runBlocking {
        val actionId = UUID.randomUUID().toString()
        val result = ActionExecutionResult(succeeded = true, message = "Telemetry sent")

        try {
            preferences.saveActionResult(actionId, result)

            assertEquals(result, preferences.getActionResult(actionId))
        } finally {
            removeStoredAction(actionId)
        }
    }

    @Test
    fun storesTypedResultAsJsonInTheOriginalPreferenceKey() = runBlocking {
        val actionId = UUID.randomUUID().toString()
        val storage = StorageSummary(1_000L, 600L, 400L, "2026-11-17T12:00:00Z")
        val result = ActionExecutionResult(true, "Storage collected", collectedData = storage)

        try {
            preferences.saveActionResult(actionId, result)

            val stored = dataStore.data.first()
            val json = JSONObject(requireNotNull(stored[stringPreferencesKey("action_${actionId}_result")]))
            assertEquals(1_000L, json.getLong("totalBytes"))
            assertEquals(600L, json.getLong("usedBytes"))
            assertEquals(400L, json.getLong("availableBytes"))
            assertEquals("2026-11-17T12:00:00Z", json.getString("capturedAt"))
            assertEquals(result, preferences.getActionResult(actionId))
        } finally {
            removeStoredAction(actionId)
        }
    }

    @Test
    fun loadsLegacyStorageJsonAsTypedActionResult() = runBlocking {
        assertLegacyPayload(
            legacyJson = """
                {"totalBytes":10000000000,"usedBytes":6000000000,"availableBytes":4000000000,
                 "capturedAt":"2026-11-17T12:00:00Z"}
            """.trimIndent(),
            expected = StorageSummary(10_000_000_000L, 6_000_000_000L, 4_000_000_000L, "2026-11-17T12:00:00Z"),
        )
    }

    @Test
    fun loadsLegacyApplicationJsonAsTypedActionResult() = runBlocking {
        assertLegacyPayload(
            legacyJson = """
                {"capturedAt":"2026-11-17T12:00:00Z","apps":[
                  {"applicationId":"com.example.notes","name":"Notes","canRemove":true,
                   "versionName":"2.0","versionCode":4000000000,
                   "installedAt":"2026-01-01T10:00:00Z","updatedAt":"2026-11-16T11:00:00Z"},
                  {"applicationId":"com.tcc.devicehealth.agent","name":"Device Health Agent",
                   "canRemove":false,"versionName":"0.1","versionCode":1}
                ]}
            """.trimIndent(),
            expected = ApplicationInventory(
                "2026-11-17T12:00:00Z",
                listOf(
                    InstalledApp(
                        "com.example.notes", "Notes", true, "2.0", 4_000_000_000L,
                        "2026-01-01T10:00:00Z", "2026-11-16T11:00:00Z",
                    ),
                    InstalledApp("com.tcc.devicehealth.agent", "Device Health Agent", false, "0.1", 1L),
                ),
            ),
        )
    }

    @Test
    fun loadsLegacyFileJsonAsTypedActionResult() = runBlocking {
        assertLegacyPayload(
            legacyJson = """
                {"capturedAt":"2026-11-17T12:00:00Z","sources":[
                  {"sourceId":"folder","label":"Documents","kind":"selected_folder",
                   "authorization":"granted","itemCount":1}
                ],"files":[
                  {"fileId":"opaque-id","sourceId":"folder","name":"Notes.txt","kind":"document",
                   "sizeBytes":128,"canRemove":true,"revision":"revision"}
                ]}
            """.trimIndent(),
            expected = FileInventory(
                capturedAt = "2026-11-17T12:00:00Z",
                sources = listOf(FileSource("folder", "Documents", "selected_folder", "granted", 1)),
                files = listOf(
                    FileItem(
                        fileId = "opaque-id", sourceId = "folder", name = "Notes.txt", kind = "document",
                        sizeBytes = 128L, canRemove = true, revision = "revision",
                    ),
                ),
            ),
        )
    }

    @Test
    fun loadsLegacyActionWithoutStatusOrPayload() = runBlocking {
        listOf(true, false).forEach { succeeded ->
            val actionId = UUID.randomUUID().toString()
            dataStore.edit { stored ->
                stored[booleanPreferencesKey("action_${actionId}_succeeded")] = succeeded
                stored[stringPreferencesKey("action_${actionId}_message")] = "Legacy result"
            }

            try {
                val result = preferences.getActionResult(actionId)

                assertEquals(
                    ActionExecutionResult(succeeded, "Legacy result", status = if (succeeded) "completed" else "failed"),
                    result,
                )
            } finally {
                removeStoredAction(actionId)
            }
        }
    }

    @Test
    fun loadsLegacyAwaitingApprovalStatusAndErrorCode() = runBlocking {
        val actionId = UUID.randomUUID().toString()
        dataStore.edit { stored ->
            stored[booleanPreferencesKey("action_${actionId}_succeeded")] = false
            stored[stringPreferencesKey("action_${actionId}_message")] = "Waiting for approval"
            stored[stringPreferencesKey("action_${actionId}_status")] = "awaiting_approval"
            stored[stringPreferencesKey("action_${actionId}_error_code")] = "approval_required"
        }

        try {
            assertEquals(
                ActionExecutionResult(false, "Waiting for approval", "awaiting_approval", "approval_required"),
                preferences.getActionResult(actionId),
            )
        } finally {
            removeStoredAction(actionId)
        }
    }

    @Test
    fun preservesLegacyApplicationRemovalWithoutExpectedVersion() = runBlocking {
        withLegacyApprovals(
            key = "pending_approvals",
            legacyJson = """[{"actionId":"legacy-app-removal","applicationId":"com.example.notes"}]""",
        ) {
            val expected = PendingApplicationRemoval("legacy-app-removal", "com.example.notes", null)

            assertEquals(listOf(expected), preferences.getPendingApplicationRemovals())
            preferences.savePendingApplicationRemoval(expected)
            assertEquals(listOf(expected), preferences.getPendingApplicationRemovals())
            val stored = dataStore.data.first()
            val json = JSONArray(requireNotNull(stored[stringPreferencesKey("pending_approvals")]))
            assertFalse(json.getJSONObject(0).has("expectedVersionCode"))
            preferences.removePendingApplicationRemoval(expected.actionId)
            assertEquals(emptyList<PendingApplicationRemoval>(), preferences.getPendingApplicationRemovals())
        }
    }

    @Test
    fun preservesLegacyFileRemovalWithoutOptionalFields() = runBlocking {
        withLegacyApprovals(
            key = "pending_file_approvals",
            legacyJson = """[{"actionId":"legacy-file-removal","fileId":"opaque-id"}]""",
        ) {
            val expected = PendingFileRemoval("legacy-file-removal", "opaque-id", null)

            assertEquals(listOf(expected), preferences.getPendingFileRemovals())
            preferences.savePendingFileRemoval(expected)
            assertEquals(listOf(expected), preferences.getPendingFileRemovals())
            val stored = dataStore.data.first()
            val json = JSONArray(requireNotNull(stored[stringPreferencesKey("pending_file_approvals")]))
                .getJSONObject(0)
            assertFalse(json.has("expectedRevision"))
            assertFalse(json.has("name"))
            assertFalse(json.has("mediaUri"))
            assertFalse(json.getBoolean("platformRequestStarted"))
            preferences.removePendingFileRemoval(expected.actionId)
            assertEquals(emptyList<PendingFileRemoval>(), preferences.getPendingFileRemovals())
        }
    }

    @Test
    fun concurrentApprovalWritesPreserveEveryAction() = runBlocking {
        val prefix = UUID.randomUUID().toString()
        val approvals = List(20) { PendingApplicationRemoval("$prefix-$it", "com.example.app$it", null) }
        val files = List(20) { PendingFileRemoval("$prefix-file-$it", "file-$it", null) }
        try {
            kotlinx.coroutines.coroutineScope {
                approvals.forEach { approval -> launch(Dispatchers.IO) { preferences.savePendingApplicationRemoval(approval) } }
                files.forEach { approval -> launch(Dispatchers.IO) { preferences.savePendingFileRemoval(approval) } }
            }
            assertEquals(approvals.toSet(), preferences.getPendingApplicationRemovals().filter { it.actionId.startsWith(prefix) }.toSet())
            assertEquals(files.toSet(), preferences.getPendingFileRemovals().filter { it.actionId.startsWith(prefix) }.toSet())
        } finally {
            approvals.forEach { preferences.removePendingApplicationRemoval(it.actionId) }
            files.forEach { preferences.removePendingFileRemoval(it.actionId) }
        }
    }

    @Test
    fun firstFinalResultSurvivesAConflictingRetry() = runBlocking {
        val id = UUID.randomUUID().toString()
        val completed = ActionExecutionResult(true, "Removed", "completed", "file_removed")
        try {
            preferences.saveActionResult(id, ActionExecutionResult(false, "Approval required", "awaiting_approval", "approval_required"))
            assertEquals(completed, preferences.saveActionResult(id, completed))
            assertEquals(completed, preferences.saveActionResult(id, ActionExecutionResult(false, "Cancelled", "cancelled")))
            assertEquals(completed, preferences.getActionResult(id))
        } finally {
            removeStoredAction(id)
        }
    }

    private suspend fun assertLegacyPayload(legacyJson: String, expected: CollectedDeviceData) {
        val actionId = UUID.randomUUID().toString()
        dataStore.edit { stored ->
            stored[booleanPreferencesKey("action_${actionId}_succeeded")] = true
            stored[stringPreferencesKey("action_${actionId}_message")] = "Collected"
            stored[stringPreferencesKey("action_${actionId}_status")] = "completed"
            stored[stringPreferencesKey("action_${actionId}_result")] = legacyJson
        }

        try {
            assertEquals(ActionExecutionResult(true, "Collected", collectedData = expected), preferences.getActionResult(actionId))
        } finally {
            removeStoredAction(actionId)
        }
    }

    private suspend fun removeStoredAction(actionId: String) {
        dataStore.edit { stored ->
            stored.remove(booleanPreferencesKey("action_${actionId}_succeeded"))
            stored.remove(stringPreferencesKey("action_${actionId}_message"))
            stored.remove(stringPreferencesKey("action_${actionId}_status"))
            stored.remove(stringPreferencesKey("action_${actionId}_error_code"))
            stored.remove(stringPreferencesKey("action_${actionId}_result"))
        }
    }

    private suspend fun withLegacyApprovals(key: String, legacyJson: String, verify: suspend () -> Unit) {
        val preferenceKey = stringPreferencesKey(key)
        var previousValue: String? = null
        dataStore.edit { stored ->
            previousValue = stored[preferenceKey]
            stored[preferenceKey] = legacyJson
        }

        try {
            verify()
        } finally {
            dataStore.edit { stored ->
                val previous = previousValue
                if (previous == null) stored.remove(preferenceKey) else stored[preferenceKey] = previous
            }
        }
    }
}
