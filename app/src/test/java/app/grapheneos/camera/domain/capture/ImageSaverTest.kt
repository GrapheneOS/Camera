package app.grapheneos.camera.domain.capture

import android.net.Uri
import androidx.camera.core.ImageProxy
import app.grapheneos.camera.data.camera.model.CapturedJpeg
import app.grapheneos.camera.data.camera.session.JpegExtractor
import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapper
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.ImageSaverException.Place
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedImage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImageSaverTest {

    private val jpegExtractor = mockk<JpegExtractor>()
    private val exifMapper = mockk<CapturedImageExifMapper>()
    private val storeCapturedImage = mockk<StoreCapturedImage>()

    private val events = mutableListOf<CapturedImageEvent>()

    @Before
    fun setUp() {
        every { jpegExtractor.extract(any(), any()) } returns JPEG
        every { exifMapper.map(any()) } returns JPEG_BYTES
        coEvery { storeCapturedImage(any(), any(), any(), any()) } returns
            StoreCapturedImageResult.Stored(Uri.EMPTY)
    }

    @Test
    fun onCaptureSuccess_reportsTheCaptureBeforeTouchingTheImage() {
        runTest {
            captureImage()

            assertEquals(CapturedImageEvent.Captured, events.first())
        }
    }

    @Test
    fun onCaptureSuccess_withTheImageStored_reportsTheSavedItem() {
        runTest {
            captureImage()

            val saved = events.filterIsInstance<CapturedImageEvent.Saved>().single()
            assertEquals(Uri.EMPTY, saved.item.uri)
        }
    }

    @Test
    fun onCaptureSuccess_withAnUnreadableImage_reportsTheStageThatFailed() {
        runTest {
            every { jpegExtractor.extract(any(), any()) } throws IOException("unreadable")

            captureImage()

            val failed = events.filterIsInstance<CapturedImageEvent.SaveFailed>().single()
            assertEquals(Place.IMAGE_EXTRACTION, failed.cause.place)
            assertFalse(failed.alreadyReported)
        }
    }

    @Test
    fun onCaptureSuccess_withoutAStorageLocation_reportsItBeforeTheFailure() {
        runTest {
            coEvery { storeCapturedImage(any(), any(), any(), any()) } returns
                StoreCapturedImageResult.StorageLocationNotFound(IOException("gone"))

            captureImage()

            val failed = events.filterIsInstance<CapturedImageEvent.SaveFailed>().single()
            assertEquals(Place.FILE_CREATION, failed.cause.place)
            assertEquals(
                listOf(
                    CapturedImageEvent.Captured,
                    CapturedImageEvent.StorageLocationNotFound,
                    CapturedImageEvent.SaveFailed(cause = failed.cause, alreadyReported = true),
                ),
                events,
            )
        }
    }

    private fun TestScope.captureImage() {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val imageSaver = imageSaver(
            scope = backgroundScope,
            dispatcher = dispatcher,
        )

        imageSaver.onCaptureSuccess(mockk<ImageProxy>(relaxed = true))
    }

    private fun imageSaver(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
    ): ImageSaver {
        return ImageSaver(
            request = CaptureImageRequest(
                storageLocation = STORAGE_LOCATION,
                includeLocation = false,
                saveAsPreviewed = false,
                removeExif = false,
                targetThumbnailWidth = 1,
                targetThumbnailHeight = 1,
            ),
            metadata = CaptureMetadata(
                reversedHorizontal = false,
                location = null,
            ),
            jpegQuality = JPEG_QUALITY,
            needsThumbnail = { false },
            onEvent = { events += it },
            storeCapturedImage = storeCapturedImage,
            exifMapper = exifMapper,
            jpegExtractor = jpegExtractor,
            pipeline = inlinePipeline(scope, dispatcher),
            scope = scope,
            mainDispatcher = dispatcher,
        )
    }

    private fun inlinePipeline(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
    ): CapturedImagePipeline {
        return object : CapturedImagePipeline {
            override fun enqueueExtraction(extraction: suspend () -> Unit) {
                run(extraction)
            }

            override fun enqueueWrite(write: suspend () -> Unit) {
                run(write)
            }

            override fun enqueueThumbnail(thumbnail: suspend () -> Unit) {
                run(thumbnail)
            }

            private fun run(work: suspend () -> Unit) {
                scope.launch(dispatcher) {
                    work()
                }
            }
        }
    }

    private companion object {
        const val JPEG_QUALITY = 90
        const val STORAGE_LOCATION = "MediaStore"

        val JPEG_BYTES = ByteArray(1)

        val JPEG = CapturedJpeg(
            jpegBytes = JPEG_BYTES,
            cropRect = null,
            orientationDegrees = 0,
            shouldUseExifOrientation = false,
        )
    }
}
