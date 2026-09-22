package app.grapheneos.camera.data.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import app.grapheneos.camera.VIDEO_NAME_PREFIX
import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.model.MEDIA_STORE_CAPTURE_PATH
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CaptureOutputRepositoryImpl
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureOutputRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun createImage_inMediaStore_insertsAPendingItemUnderTheCameraFolder() {
        runTest {
            val created = createImage()
            val values = requireNotNull(mediaProvider.inserted)

            assertEquals(CaptureOutputResult.Success(INSERTED_URI), created)
            assertEquals(FILE_NAME, values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            assertEquals(MIME_TYPE, values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
            assertEquals(
                MEDIA_STORE_CAPTURE_PATH,
                values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH),
            )
            assertEquals(1, values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        }
    }

    @Test
    fun createImage_whenMediaStoreRefusesTheInsert_reportsTheFailure() {
        runTest {
            mediaProvider.insertResult = null

            assertTrue(createImage() is CaptureOutputResult.Failure)
        }
    }

    @Test
    fun createVideo_inMediaStore_insertsIntoTheVideoCollection() {
        runTest {
            val created = repository.createVideo(
                storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
                fileName = VIDEO_FILE_NAME,
                mimeType = VIDEO_MIME_TYPE,
            )

            val values = requireNotNull(mediaProvider.inserted)
            assertEquals(CaptureOutputResult.Success(INSERTED_URI), created)
            assertEquals(VIDEO_FILE_NAME, values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            assertEquals(VIDEO_MIME_TYPE, values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
            assertEquals(1, values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        }
    }

    @Test
    fun openForWriting_handsOutADescriptorThatWritesToTheItem() {
        runTest {
            val file = temporaryFolder.newFile()
            mediaProvider.file = file

            val descriptor = requireNotNull(
                repository.openForWriting(INSERTED_URI).valueOrNull(),
            )
            FileOutputStream(descriptor.fileDescriptor).use { it.write(JPEG_BYTES) }
            descriptor.close()

            assertArrayEquals(JPEG_BYTES, file.readBytes())
        }
    }

    @Test
    fun openForWriting_whenTheProviderHasNoFile_reportsTheFailure() {
        runTest {
            assertTrue(repository.openForWriting(INSERTED_URI) is CaptureOutputResult.Failure)
        }
    }

    @Test
    fun publish_mediaStoreItem_clearsThePendingFlag() {
        runTest {
            repository.publish(INSERTED_URI)

            val values = requireNotNull(mediaProvider.updated)
            assertEquals(0, values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        }
    }

    @Test
    fun publish_documentItem_leavesMediaStoreUntouched() {
        runTest {
            repository.publish(Uri.parse(DOCUMENT_URI))

            assertNull(mediaProvider.updated)
        }
    }

    @Test
    fun delete_mediaStoreItem_removesTheRow() {
        runTest {
            repository.delete(INSERTED_URI)

            assertEquals(listOf(INSERTED_URI), mediaProvider.deletedUris)
        }
    }

    @Test
    fun delete_documentItem_goesThroughTheDocumentsProvider() {
        runTest {
            val documents = Robolectric
                .buildContentProvider(FakeMediaProvider::class.java)
                .create(DOCUMENT_AUTHORITY)
                .get()

            repository.delete(Uri.parse(DOCUMENT_URI))

            assertEquals(1, documents.calls)
            assertTrue(mediaProvider.deletedUris.isEmpty())
        }
    }

    @Test
    fun deleteStalePendingVideos_asksMediaStoreForOurOwnOldPendingRecordings() {
        runTest {
            repository.deleteStalePendingVideos(olderThan = 1.hours)

            val selection = requireNotNull(mediaProvider.deletionSelection)
            val arguments = mediaProvider.deletionArguments
            assertTrue(selection.contains(MediaStore.MediaColumns.IS_PENDING))
            assertEquals("$VIDEO_NAME_PREFIX%", arguments[0])
            assertTrue(arguments[1].toLong() < System.currentTimeMillis() / 1000L)
        }
    }

    @Test
    fun createImage_whenMediaStoreDeniesAccess_reportsTheFailure() {
        runTest {
            mediaProvider.insertFailure = SecurityException()

            val created = createImage()

            assertTrue((created as CaptureOutputResult.Failure).cause is SecurityException)
        }
    }

    private suspend fun createImage(): CaptureOutputResult<Uri> {
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
        var deletionSelection: String? = null
        var deletionArguments: List<String> = emptyList()
        val deletedUris = mutableListOf<Uri>()
        var calls = 0
        var file: File? = null

        override fun onCreate(): Boolean {
            return true
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            calls++

            return Bundle()
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val target = file ?: throw FileNotFoundException("no file for $uri")

            return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_WRITE)
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
            when (selection) {
                null -> deletedUris += uri

                else -> {
                    deletionSelection = selection
                    deletionArguments = selectionArgs?.toList().orEmpty()
                }
            }

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
        const val DOCUMENT_AUTHORITY = "com.example.documents"
        const val FILE_NAME = "IMG_20260724_153012_345.jpg"
        const val MIME_TYPE = "image/jpeg"
        const val VIDEO_FILE_NAME = "VID_20260724_153012.mp4"
        const val VIDEO_MIME_TYPE = "video/mp4"
        const val DOCUMENT_URI = "content://$DOCUMENT_AUTHORITY/document/1"

        val JPEG_BYTES = byteArrayOf(1, 2, 3)
        val INSERTED_URI: Uri = Uri.parse("content://media/external_primary/images/media/1")
    }
}
