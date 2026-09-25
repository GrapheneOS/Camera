package app.grapheneos.camera.domain.capture.usecase

import android.net.Uri
import android.os.ParcelFileDescriptor
import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CreateRecordingOutputTest {

    private val ownUri: Uri = Uri.parse("content://media/external_primary/video/media/1")
    private val foreignUri: Uri = Uri.parse("content://com.example.app/videos/1")

    private val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)

    private val repository = mockk<CaptureOutputRepository>(relaxed = true) {
        coEvery { createVideo(any(), any(), any()) } returns CaptureOutputResult.Success(ownUri)
        coEvery { openForWriting(any()) } returns CaptureOutputResult.Success(fileDescriptor)
    }

    private val createRecordingOutput = CreateRecordingOutputImpl(repository)

    @Test
    fun invoke_withoutAForeignUri_createsAVideoTheGalleryWillShow() {
        runTest {
            val output = requireNotNull(
                createRecordingOutput(
                    storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
                    foreignUri = null,
                ),
            )

            assertEquals(ownUri, output.uri)
            assertTrue(output.isOwnFile)
            assertTrue(output.isPendingMediaStoreUri)
            coVerify(exactly = 1) {
                repository.createVideo(
                    storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
                    fileName = "VID_${output.dateString}.mp4",
                    mimeType = "video/mp4",
                )
            }
        }
    }

    @Test
    fun invoke_withAForeignUri_writesIntoItAndLeavesTheGalleryAlone() {
        runTest {
            val output = requireNotNull(
                createRecordingOutput(
                    storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
                    foreignUri = foreignUri,
                ),
            )

            assertEquals(foreignUri, output.uri)
            assertFalse(output.isOwnFile)
            assertFalse(output.isPendingMediaStoreUri)
            coVerify(exactly = 0) { repository.createVideo(any(), any(), any()) }
        }
    }

    @Test
    fun invoke_withAnUnopenableFile_createsNoOutput() {
        runTest {
            coEvery { repository.openForWriting(any()) } returns
                CaptureOutputResult.Failure(IOException("no"))

            val output = createRecordingOutput(
                storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
                foreignUri = null,
            )

            assertNull(output)
        }
    }
}
