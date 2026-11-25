package com.tcc.devicehealth.agent

import com.tcc.devicehealth.agent.data.remote.ControllerApiJsonAdapter
import com.tcc.devicehealth.agent.model.ActionExecutionResult
import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.CollectedDeviceData
import com.tcc.devicehealth.agent.model.FileInventory
import com.tcc.devicehealth.agent.model.FileItem
import com.tcc.devicehealth.agent.model.FileSource
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.model.LatestDeviceSnapshots
import com.tcc.devicehealth.agent.model.StorageSummary
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerApiJsonAdapterTest {
    private val capturedAt = "2026-11-17T12:00:00Z"
    private val storage = StorageSummary(10_000_000_000L, 6_000_000_000L, 4_000_000_000L, capturedAt)
    private val apps = ApplicationInventory(
        capturedAt,
        listOf(
            InstalledApp(
                "com.example.notes", "Notes", true, "2.0", 4_000_000_000L,
                "2026-01-01T10:00:00Z", "2026-11-16T11:00:00Z",
            ),
            InstalledApp("com.tcc.devicehealth.agent", "Device Health Agent", false, "0.1", 1L),
        ),
    )
    private val files = FileInventory(
        capturedAt = capturedAt,
        sources = listOf(
            FileSource("folder", "Documents", "selected_folder", "granted", itemCount = 1),
            FileSource("media", "Images", "media", "granted"),
            FileSource("empty", "Empty folder", "selected_folder", "granted", itemCount = 0),
        ),
        files = listOf(
            FileItem(
                fileId = "document-id", sourceId = "folder", name = "Notes.txt", kind = "document",
                mimeType = "text/plain", sizeBytes = 5_000_000_000L,
                modifiedAt = "2026-11-16T10:00:00Z", displayPath = "Documents/Notes.txt",
                canRemove = true, revision = "document-revision",
            ),
            FileItem(
                fileId = "photo-id", sourceId = "media", name = "Photo.png", kind = "image",
                sizeBytes = 128L, canRemove = false, revision = "photo-revision",
            ),
        ),
    )

    @Test
    fun applicationPayloadPreservesDatesVersionsAndRemovalAvailability() {
        val json = ControllerApiJsonAdapter.encodeCollectedData(apps)
        val application = json.getJSONArray("apps").getJSONObject(0)
        val agent = json.getJSONArray("apps").getJSONObject(1)

        assertEquals(capturedAt, json.getString("capturedAt"))
        assertEquals("com.example.notes", application.getString("applicationId"))
        assertEquals("Notes", application.getString("name"))
        assertEquals("2.0", application.getString("versionName"))
        assertEquals(4_000_000_000L, application.getLong("versionCode"))
        assertEquals("2026-01-01T10:00:00Z", application.getString("installedAt"))
        assertEquals("2026-11-16T11:00:00Z", application.getString("updatedAt"))
        assertTrue(application.getBoolean("canRemove"))
        assertFalse(agent.getBoolean("canRemove"))
        assertEquals("", agent.getString("installedAt"))
        assertEquals("", agent.getString("updatedAt"))
    }

    @Test
    fun filePayloadPreservesSourceCountsAndFileMetadata() {
        val json = ControllerApiJsonAdapter.encodeCollectedData(files)
        val source = json.getJSONArray("sources").getJSONObject(0)
        val emptySource = json.getJSONArray("sources").getJSONObject(2)
        val file = json.getJSONArray("files").getJSONObject(0)

        assertEquals(capturedAt, json.getString("capturedAt"))
        assertEquals("folder", source.getString("sourceId"))
        assertEquals("Documents", source.getString("label"))
        assertEquals("selected_folder", source.getString("kind"))
        assertEquals("granted", source.getString("authorization"))
        assertEquals(1, source.getInt("itemCount"))
        assertEquals(0, emptySource.getInt("itemCount"))
        assertEquals("document-id", file.getString("fileId"))
        assertEquals("folder", file.getString("sourceId"))
        assertEquals("Notes.txt", file.getString("name"))
        assertEquals("document", file.getString("kind"))
        assertEquals("text/plain", file.getString("mimeType"))
        assertEquals(5_000_000_000L, file.getLong("sizeBytes"))
        assertEquals("2026-11-16T10:00:00Z", file.getString("modifiedAt"))
        assertEquals("Documents/Notes.txt", file.getString("displayPath"))
        assertEquals("document-revision", file.getString("revision"))
        assertTrue(file.getBoolean("canRemove"))
    }

    @Test
    fun filePayloadOmitsAbsentOptionalFields() {
        val json = ControllerApiJsonAdapter.encodeCollectedData(files)
        val source = json.getJSONArray("sources").getJSONObject(1)
        val file = json.getJSONArray("files").getJSONObject(1)

        assertFalse(source.has("itemCount"))
        assertFalse(file.has("mimeType"))
        assertFalse(file.has("modifiedAt"))
        assertFalse(file.has("displayPath"))
        assertEquals("photo-revision", file.getString("revision"))
        assertFalse(file.getBoolean("canRemove"))
    }

    @Test
    fun storageSummarySurvivesJsonRoundTrip() {
        val encoded = ControllerApiJsonAdapter.encodeCollectedData(storage).toString()

        assertEquals(storage, ControllerApiJsonAdapter.decodeCollectedData(JSONObject(encoded)))
    }

    @Test
    fun applicationInventorySurvivesJsonRoundTrip() {
        val encoded = ControllerApiJsonAdapter.encodeCollectedData(apps).toString()

        assertEquals(apps, ControllerApiJsonAdapter.decodeCollectedData(JSONObject(encoded)))
    }

    @Test
    fun fileInventorySurvivesJsonRoundTrip() {
        val encoded = ControllerApiJsonAdapter.encodeCollectedData(files).toString()

        assertEquals(files, ControllerApiJsonAdapter.decodeCollectedData(JSONObject(encoded)))
    }

    @Test
    fun parsesAllSnapshotTypes() {
        val response = JSONObject()
            .put("storage", ControllerApiJsonAdapter.encodeCollectedData(storage))
            .put("apps", ControllerApiJsonAdapter.encodeCollectedData(apps))
            .put("files", ControllerApiJsonAdapter.encodeCollectedData(files))
            .toString()

        assertEquals(LatestDeviceSnapshots(storage, apps, files), ControllerApiJsonAdapter.decodeSnapshots(response))
    }

    @Test
    fun parsesExplicitlyNullSnapshots() {
        val response = """{"storage":null,"apps":null,"files":null}"""

        assertEquals(LatestDeviceSnapshots(), ControllerApiJsonAdapter.decodeSnapshots(response))
    }

    @Test
    fun parsesMissingSnapshots() {
        assertEquals(LatestDeviceSnapshots(), ControllerApiJsonAdapter.decodeSnapshots("{}"))
    }

    @Test
    fun parsesAvailableSnapshotAlongsideNullSnapshots() {
        val response = JSONObject()
            .put("storage", JSONObject.NULL)
            .put("apps", ControllerApiJsonAdapter.encodeCollectedData(apps))
            .put("files", JSONObject.NULL)
            .toString()

        assertEquals(LatestDeviceSnapshots(apps = apps), ControllerApiJsonAdapter.decodeSnapshots(response))
    }

    @Test
    fun applicationRemovalRequestPreservesTargetAndExpectedVersion() {
        val request = ControllerApiJsonAdapter.encodeActionRequest(
            type = "removeApplication", applicationId = "com.example.notes",
            expectedVersionCode = 4_000_000_000L, fileId = null, expectedRevision = null,
        )
        val payload = request.getJSONObject("payload")

        assertEquals("removeApplication", request.getString("type"))
        assertEquals("com.example.notes", payload.getString("applicationId"))
        assertEquals(4_000_000_000L, payload.getLong("expectedVersionCode"))
        assertEquals(2, payload.length())
    }

    @Test
    fun fileRemovalRequestPreservesTargetAndExpectedRevision() {
        val request = ControllerApiJsonAdapter.encodeActionRequest(
            type = "removeFile", applicationId = null, expectedVersionCode = null,
            fileId = "document-id", expectedRevision = "document-revision",
        )
        val payload = request.getJSONObject("payload")

        assertEquals("removeFile", request.getString("type"))
        assertEquals("document-id", payload.getString("fileId"))
        assertEquals("document-revision", payload.getString("expectedRevision"))
        assertEquals(2, payload.length())
    }

    @Test
    fun collectionRequestOmitsPayload() {
        val request = ControllerApiJsonAdapter.encodeActionRequest("collectTelemetry", null, null, null, null)

        assertEquals("collectTelemetry", request.getString("type"))
        assertFalse(request.has("payload"))
    }

    @Test
    fun actionResultPreservesAwaitingApprovalStatusAndTypedPayload() {
        val result = ActionExecutionResult(
            succeeded = false, message = "Waiting for approval", status = "awaiting_approval",
            errorCode = "approval_required", collectedData = apps,
        )

        val json = ControllerApiJsonAdapter.encodeActionResult(result)

        assertFalse(json.getBoolean("succeeded"))
        assertEquals("awaiting_approval", json.getString("status"))
        assertEquals("Waiting for approval", json.getString("message"))
        assertEquals("approval_required", json.getString("errorCode"))
        assertTrue(json.get("result") is JSONObject)
        assertEquals(apps, ControllerApiJsonAdapter.decodeCollectedData(json.getJSONObject("result")))
    }

    @Test
    fun actionResultEncodesEachTypedPayloadAsAnObject() {
        val payloads: List<CollectedDeviceData> = listOf(storage, apps, files)

        payloads.forEach { payload ->
            val json = ControllerApiJsonAdapter.encodeActionResult(ActionExecutionResult(true, "Collected", collectedData = payload))

            assertTrue(json.getBoolean("succeeded"))
            assertEquals("completed", json.getString("status"))
            assertFalse(json.has("errorCode"))
            assertTrue(json.get("result") is JSONObject)
            assertEquals(payload, ControllerApiJsonAdapter.decodeCollectedData(json.getJSONObject("result")))
        }
    }

    @Test
    fun actionResultOmitsAbsentErrorAndPayload() {
        val json = ControllerApiJsonAdapter.encodeActionResult(ActionExecutionResult(true, "Telemetry sent"))

        assertEquals("Telemetry sent", json.getString("message"))
        assertFalse(json.has("errorCode"))
        assertFalse(json.has("result"))
    }
}
