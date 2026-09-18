package app.grapheneos.camera.domain.capture

import android.net.Uri
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult.Stage
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedImageImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoreCapturedImageTest {

    private val uri: Uri = Uri.parse("content://media/external_primary/images/media/1")

    private val repository = mockk<CaptureOutputRepository>(relaxed = true) {
        coEvery { createImage(any(), any(), any()) } returns uri
    }

    private val storeCapturedImage = StoreCapturedImageImpl(
        captureOutputRepository = repository,
    )

    @Test
    fun invoke_whenEveryStepSucceeds_publishesTheWrittenImage() = runTest {
        val result = store()

        assertEquals(StoreCapturedImageResult.Stored(uri = uri), result)
        coVerify { repository.write(uri, JPEG_BYTES) }
        coVerify { repository.publish(uri) }
    }

    @Test
    fun invoke_whenMediaStoreRefusesTheFile_failsAtFileCreation() = runTest {
        coEvery { repository.createImage(any(), any(), any()) } throws IOException()

        val result = store(storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION)

        assertEquals(Stage.FILE_CREATION, (result as StoreCapturedImageResult.Failed).stage)
        coVerify(exactly = 0) { repository.write(any(), any()) }
    }

    @Test
    fun invoke_whenTheDocumentTreeRefusesTheFile_reportsTheStorageLocationMissing() = runTest {
        coEvery { repository.createImage(any(), any(), any()) } throws IOException()

        val result = store(storageLocation = DOCUMENT_TREE)

        assertTrue(result is StoreCapturedImageResult.StorageLocationNotFound)
    }

    @Test
    fun invoke_whenWritingFails_deletesTheIncompleteImage() = runTest {
        coEvery { repository.write(any(), any()) } throws IOException()

        val result = store()

        assertEquals(Stage.FILE_WRITE, (result as StoreCapturedImageResult.Failed).stage)
        coVerify { repository.delete(uri) }
        coVerify(exactly = 0) { repository.publish(any()) }
    }

    @Test
    fun invoke_whenTheIncompleteImageCannotBeDeleted_stillFailsAtWriting() = runTest {
        coEvery { repository.write(any(), any()) } throws IOException()
        coEvery { repository.delete(any()) } throws IOException()

        val result = store()

        assertEquals(Stage.FILE_WRITE, (result as StoreCapturedImageResult.Failed).stage)
    }

    @Test
    fun invoke_whenPublishingFails_keepsTheWrittenImage() = runTest {
        coEvery { repository.publish(any()) } throws IOException()

        val result = store()

        assertEquals(
            Stage.FILE_WRITE_COMPLETION,
            (result as StoreCapturedImageResult.Failed).stage,
        )
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    private suspend fun store(
        storageLocation: String = CapturedItemRepository.MEDIA_STORE_LOCATION,
    ): StoreCapturedImageResult {
        return storeCapturedImage(
            jpegBytes = JPEG_BYTES,
            storageLocation = storageLocation,
            fileName = FILE_NAME,
            mimeType = MIME_TYPE,
        )
    }

    private companion object {
        const val FILE_NAME = "IMG_20260724_153012_345.jpg"
        const val MIME_TYPE = "image/jpeg"
        const val DOCUMENT_TREE = "content://com.example.documents/tree/primary%3ADCIM"

        val JPEG_BYTES = byteArrayOf(1, 2, 3)
    }
}
