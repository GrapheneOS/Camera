package app.grapheneos.camera

import android.content.ContentValues
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grapheneos.camera.util.isForeignOutputUriAllowed
import app.grapheneos.camera.util.isInsideAnyTree
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The capture intents wrote MediaStore.EXTRA_OUTPUT with the app's own access, so any app could
 * have its photo or video written over the user's own captures, into the storage location the
 * user picked, or over the app's private files, none of which it could write itself.
 */
@RunWith(AndroidJUnit4::class)
class ForeignOutputUriRegressionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val externalStorage = "com.android.externalstorage.documents"

    private fun tree(id: String) = DocumentsContract.buildTreeDocumentUri(externalStorage, id)

    @Test
    fun aFileUri_isRefused() {
        val prefs = File(context.dataDir, "shared_prefs/${CamConfig.COMMON_SHARED_PREFS_NAME}.xml")
        assertFalse(isForeignOutputUriAllowed(context, Uri.fromFile(prefs)))
    }

    @Test
    fun anAuthorityWithAUserId_isRefused() {
        val uri = Uri.parse("content://0@${MediaStore.AUTHORITY}/external/images/media/1")
        assertFalse(isForeignOutputUriAllowed(context, uri))
    }

    @Test
    fun mediaThisAppSaved_isRefused() {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "foreign_output_test.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        val uri = context.contentResolver.insert(CamConfig.imageCollectionUri, values)
        assertNotNull(uri)
        try {
            assertFalse(isForeignOutputUriAllowed(context, uri!!))
        } finally {
            context.contentResolver.delete(uri!!, null, null)
        }
    }

    /** Can't be told apart from media this app saved that it can no longer see. */
    @Test
    fun mediaThatCannotBeLookedUp_isRefused() {
        val uri = Uri.parse("content://${MediaStore.AUTHORITY}/external/images/media/${Long.MAX_VALUE}")
        assertFalse(isForeignOutputUriAllowed(context, uri))
    }

    @Test
    fun anotherAppsProvider_isAllowed() {
        assertTrue(isForeignOutputUriAllowed(context, Uri.parse("content://com.example.output/1")))
    }

    @Test
    fun aDocumentUnderAGrantedTree_isInside() {
        val granted = tree("primary:DCIM")
        val document = DocumentsContract.buildDocumentUriUsingTree(granted, "primary:DCIM/a/b.jpg")
        assertTrue(isInsideAnyTree(document, listOf(granted)))
        assertTrue(isInsideAnyTree(granted, listOf(granted)))
    }

    /** The grant matches decoded segments, so a differently encoded tree id still reaches it. */
    @Test
    fun aDifferentlyEncodedTreeId_isInside() {
        val granted = tree("primary:DCIM")
        val document = Uri.parse("content://$externalStorage/tree/primary:DCIM/document/primary:x")
        assertTrue(isInsideAnyTree(document, listOf(granted)))
    }

    @Test
    fun documentsElsewhere_areNotInside() {
        val granted = tree("primary:DCIM")
        val otherTree = DocumentsContract.buildDocumentUriUsingTree(tree("primary:Download"), "primary:Download/x")
        val otherProvider = DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri("com.example.documents", "primary:DCIM"), "primary:DCIM/x"
        )
        val notATree = DocumentsContract.buildDocumentUri(externalStorage, "primary:DCIM/x")

        assertFalse(isInsideAnyTree(otherTree, listOf(granted)))
        assertFalse(isInsideAnyTree(otherProvider, listOf(granted)))
        assertFalse(isInsideAnyTree(notATree, listOf(granted)))
    }
}
