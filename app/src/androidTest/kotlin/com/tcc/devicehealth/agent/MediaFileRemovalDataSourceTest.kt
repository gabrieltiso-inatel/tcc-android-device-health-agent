package com.tcc.devicehealth.agent

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.UUID

@SdkSuppress(minSdkVersion = 30)
class MediaFileRemovalDataSourceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val source = MediaFileRemovalDataSource(context)
    private var fixture: Uri? = null

    @Before
    fun createFixture() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "device-health-test-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DeviceHealthTests")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create media fixture")
        fixture = uri
        resolver.openOutputStream(uri)?.use { output ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    @After
    fun removeFixture() {
        fixture?.let { uri ->
            if (source.find(fileReferenceId(uri.toString())) != null) resolver.delete(uri, null, null)
        }
    }

    @Test
    fun accessibleMediaIsAdvertisedAsRemovable() = runBlocking {
        val execution = FileInventoryDataSource(context, AgentPreferences(context)).collect()
        val files = JSONObject(execution.resultJson ?: error("Missing inventory")).getJSONArray("files")
        val target = approval().fileId
        val file = (0 until files.length()).map { files.getJSONObject(it) }.first { it.getString("fileId") == target }

        assertTrue(file.getBoolean("canRemove"))
    }

    @Test
    fun preparingSystemConfirmationDoesNotDeleteTheFile() {
        val approval = approval()

        val intentSender = source.remove(approval)

        assertNotNull(intentSender)
        assertFalse(source.isAbsent(approval))
        assertThrows(FileNotAllowedException::class.java) { source.finish(approval) }
    }

    @Test
    fun staleRevisionIsRejectedBeforeSystemConfirmation() {
        assertThrows(FileChangedException::class.java) { source.remove(approval().copy(expectedRevision = "stale")) }
    }

    @Test
    fun completedSystemDeletionIsVerifiedWithoutRepeatingDeletion() {
        val approval = approval()
        resolver.delete(fixture ?: error("Missing fixture"), null, null)

        source.finish(approval)

        assertTrue(source.isAbsent(approval))
    }

    private fun approval(): PendingFileApproval {
        val uri = fixture ?: error("Missing fixture")
        val reference = source.find(fileReferenceId(uri.toString())) ?: error("Fixture was not resolved")
        return PendingFileApproval("media-action", fileReferenceId(uri.toString()), reference.revision,
            reference.name, uri.toString(), platformRequestStarted = true)
    }
}
