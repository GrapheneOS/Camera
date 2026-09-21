package app.grapheneos.camera.domain.capture

import android.net.Uri
import android.os.ParcelFileDescriptor
import app.grapheneos.camera.data.media.model.CaptureOutputResult
import app.grapheneos.camera.data.media.repository.CaptureOutputRepository
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.model.RecordingOutput
import app.grapheneos.camera.domain.capture.usecase.CreateRecordingOutputImpl
import app.grapheneos.camera.domain.capture.usecase.DiscardRecordingImpl
import app.grapheneos.camera.domain.capture.usecase.PublishRecordingImpl
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
class RecordingOutputTest {

    private val ownUri: Uri = Uri.parse("content://media/external_primary/video/media/1")
    private val foreignUri: Uri = Uri.parse("content://com.example.app/videos/1")

    private val fileDescriptor = mockk<ParcelFileDescriptor>(relaxed = true)

    private val repository = mockk<CaptureOutputRepository>(relaxed = true) {
        coEvery { createVideo(any(), any(), any()) } returns CaptureOutputResult.Success(ownUri)
        coEvery { openForWriting(any()) } returns CaptureOutputResult.Success(fileDescriptor)
    }

    private val createRecordingOutput = CreateRecordingOutputImpl(repository)
    private val publishRecording = PublishRecordingImpl(repository)
    private val discardRecording = DiscardRecordingImpl(repository)

    @Test
    fun invoke_withoutAForeignUri_createsAVideoTheGalleryWillShow() {
        runTest {
            val output = requireNotNull(
                createRecordingOutput(
                    storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
                    dateString = DATE_STRING,
                    foreignUri = null,
                ),
            )

            assertEquals(ownUri, output.uri)
            assertTrue(output.isOwnFile)
            assertTrue(output.isPendingMediaStoreUri)
            coVerify(exactly = 1) {
                repository.createVideo(
                    storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
                    fileName = "VID_$DATE_STRING.mp4",
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
                    dateString = DATE_STRING,
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
                dateString = DATE_STRING,
                foreignUri = null,
            )

            assertNull(output)
        }
    }

    @Test
    fun publishRecording_onlyPublishesAPendingRowAndReportsTheFailure() {
        runTest {
            assertTrue(publishRecording(output(isPendingMediaStoreUri = false)))
            coVerify(exactly = 0) { repository.publish(any()) }

            coEvery { repository.publish(ownUri) } returns CaptureOutputResult.Failure(
                IOException("gone"),
            )
            assertFalse(publishRecording(output(isPendingMediaStoreUri = true)))
        }
    }

    @Test
    fun discardRecording_leavesAnotherAppsFileAlone() {
        runTest {
            discardRecording(output(isOwnFile = false))
            coVerify(exactly = 0) { repository.delete(any()) }

            discardRecording(output(isOwnFile = true))
            coVerify(exactly = 1) { repository.delete(ownUri) }
        }
    }

    private fun output(
        isOwnFile: Boolean = true,
        isPendingMediaStoreUri: Boolean = false,
    ): RecordingOutput {
        return RecordingOutput(
            uri = ownUri,
            fileDescriptor = fileDescriptor,
            isOwnFile = isOwnFile,
            isPendingMediaStoreUri = isPendingMediaStoreUri,
        )
    }

    private companion object {
        const val DATE_STRING = "20260920_120000"
    }
}
