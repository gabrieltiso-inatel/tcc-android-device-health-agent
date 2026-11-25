package com.tcc.devicehealth.agent

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.tcc.devicehealth.agent.data.android.DocumentTreeDataSource
import com.tcc.devicehealth.agent.data.android.fileReferenceId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
class DocumentTreeDataSourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tree = Uri.parse("content://com.example.documents/tree/root")
    private val resolver = testContentResolver(context, "com.example.documents") { uri, projection ->
        MatrixCursor(checkNotNull(projection)).apply {
            if (uri.pathSegments[3] == "root") {
                addRow(arrayOf("folder", "Folder", DocumentsContract.Document.MIME_TYPE_DIR, 0L, 0L, 0))
                addRow(arrayOf("readonly", "Read only.txt", "text/plain", 200L, 2_000L, 0))
            } else {
                addRow(arrayOf("notes", "Notes.txt", "text/plain", 100L, 1_000L, DocumentsContract.Document.FLAG_SUPPORTS_DELETE))
            }
        }
    }
    private val source = DocumentTreeDataSource(context, resolver)

    @Test
    fun collectionAndResolutionPreserveTheSameOpaqueIdentityAndRevision() = runBlocking {
        val files = source.collect(tree.toString(), 5_000)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, "notes")
        val notes = files.first()
        val resolved = checkNotNull(source.find(tree.toString(), notes.fileId))

        assertEquals("Folder/Notes.txt", notes.displayPath)
        assertEquals(fileReferenceId(uri.toString()), notes.fileId)
        assertEquals(fileReferenceId("$uri|100|1000"), notes.revision)
        assertEquals(notes.revision, resolved.revision)
        assertTrue(resolved.canRemove)
        assertEquals(false, files.last().canRemove)
        assertEquals("1970-01-01T00:00:01Z", notes.modifiedAt)
    }

    @Test
    fun inventoryLimitStopsTraversalWithoutAddingExtraRows() = runBlocking {
        assertEquals(listOf("Notes.txt"), source.collect(tree.toString(), 1).map { it.name })
    }
}
