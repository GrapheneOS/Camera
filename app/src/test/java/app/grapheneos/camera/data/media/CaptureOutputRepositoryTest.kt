package app.grapheneos.camera.data.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CaptureOutputRepositoryImpl
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureOutputRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var mediaProvider: FakeMediaProvider

    private val repository = CaptureOutputRepositoryImpl(
        contentResolver = context.contentResolver,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Before
    fun registerMediaProvider() {
        mediaProvider = Robolectric
            .buildContentProvider(FakeMediaProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
    }

    @Test
    fun createImage_inMediaStore_insertsAPendingItemUnderTheCameraFolder() = runTest {
        val uri = createImage()
        val values = requireNotNull(mediaProvider.inserted)

        assertEquals(INSERTED_URI, uri)
        assertEquals(FILE_NAME, values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals(MIME_TYPE, values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertEquals(
            CaptureOutputRepository.DEFAULT_MEDIA_STORE_CAPTURE_PATH,
            values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH),
        )
        assertEquals(1, values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
    }

    @Test
    fun createImage_whenMediaStoreRefusesTheInsert_throws() = runTest {
        mediaProvider.insertResult = null

        val failure = runCatching {
            createImage()
        }.exceptionOrNull()

        assertTrue(failure is IOException)
    }

    @Test
    fun publish_mediaStoreItem_clearsThePendingFlag() = runTest {
        repository.publish(INSERTED_URI)

        val values = requireNotNull(mediaProvider.updated)
        assertEquals(0, values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
    }

    @Test
    fun publish_documentItem_leavesMediaStoreUntouched() = runTest {
        repository.publish(Uri.parse(DOCUMENT_URI))

        assertNull(mediaProvider.updated)
    }

    @Test
    fun delete_whenNoRowIsDeleted_throws() = runTest {
        mediaProvider.deletedRows = 0

        val failure = runCatching {
            repository.delete(INSERTED_URI)
        }.exceptionOrNull()

        assertTrue(failure is IOException)
    }

    @Test
    fun createImage_whenMediaStoreDeniesAccess_throwsAnIOException() = runTest {
        mediaProvider.insertFailure = SecurityException()

        val failure = runCatching {
            createImage()
        }.exceptionOrNull()

        assertTrue(failure is IOException)
    }

    private suspend fun createImage(): Uri {
        return repository.createImage(
            storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
            fileName = FILE_NAME,
            mimeType = MIME_TYPE,
        )
    }

    private class FakeMediaProvider : ContentProvider() {

        var insertResult: Uri? = INSERTED_URI
        var insertFailure: RuntimeException? = null
        var deletedRows = 1

        var inserted: ContentValues? = null
        var updated: ContentValues? = null

        override fun onCreate(): Boolean {
            return true
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            return null
        }

        override fun getType(uri: Uri): String? {
            return null
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            insertFailure?.let { throw it }
            inserted = values
            return insertResult
        }

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            return deletedRows
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int {
            updated = values
            return 1
        }
    }

    private companion object {
        const val FILE_NAME = "IMG_20260724_153012_345.jpg"
        const val MIME_TYPE = "image/jpeg"
        const val DOCUMENT_URI = "content://com.example.documents/document/1"

        val INSERTED_URI: Uri = Uri.parse("content://media/external_primary/images/media/1")
    }
}
