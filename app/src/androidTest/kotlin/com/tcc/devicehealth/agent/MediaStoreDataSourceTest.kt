package com.tcc.devicehealth.agent

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.tcc.devicehealth.agent.data.android.FileChangedException
import com.tcc.devicehealth.agent.data.android.FileNotAllowedException
import com.tcc.devicehealth.agent.data.android.MediaStoreDataSource
import com.tcc.devicehealth.agent.data.android.fileReferenceId
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@SdkSuppress(minSdkVersion = 30)
class MediaStoreDataSourceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val source = MediaStoreDataSource(context)
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
    fun removeFixture(): Unit = runBlocking {
        fixture?.let { uri ->
            if (source.find(fileReferenceId(uri.toString())) != null) resolver.delete(uri, null, null)
            Unit
        }
    }

    @Test
    fun accessibleMediaIsAdvertisedAsRemovable() = runBlocking {
        val inventory = MediaStoreDataSource(context).collect(5_000)
        val target = approval().fileId
        val file = inventory.files.first { it.fileId == target }

        assertTrue(file.canRemove)
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

    @Test
    fun failedMediaQueryDiscardsPartialRowsAndMarksTheSourceUnavailable() = runBlocking {
        val contentResolver = testContentResolver(context, MediaStore.AUTHORITY) { _, projection ->
            object : android.database.MatrixCursor(checkNotNull(projection)) {
                init {
                    addRow(arrayOf(1L, "First.png", 100L, 1L, "image/png"))
                    addRow(arrayOf(2L, "Second.png", 100L, 1L, "image/png"))
                }
                override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
                    if (newPosition == 1) throw IllegalStateException("Provider unavailable")
                    return true
                }
            }
        }
        val inventory = MediaStoreDataSource(context, contentResolver) { true }.collect(5_000)
        assertTrue(inventory.files.isEmpty())
        assertTrue(inventory.sources.all { it.authorization == "unavailable" })
    }

    private fun approval(): PendingFileRemoval = runBlocking {
        val uri = fixture ?: error("Missing fixture")
        val reference = source.find(fileReferenceId(uri.toString())) ?: error("Fixture was not resolved")
        PendingFileRemoval("media-action", fileReferenceId(uri.toString()), reference.revision,
            reference.name, uri.toString(), platformRequestStarted = true)
    }
}
