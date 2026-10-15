package app.grapheneos.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.grapheneos.camera.data.camera.model.CapturedJpeg
import app.grapheneos.camera.data.camera.session.JpegExtractor
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.coordinator.CapturedImagePipeline
import app.grapheneos.camera.domain.capture.coordinator.ImageSaver
import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapperImpl
import app.grapheneos.camera.domain.capture.model.CaptureImageRequest
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedImage
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Some camera HALs hand over a JPEG that ends early. It is saved like any other capture, but the
 * thumbnail decode then threw on the thumbnail thread and took the process down with it.
 */
@RunWith(AndroidJUnit4::class)
class TruncatedJpegRegressionTest {

    private val pipelineThread = Executors.newSingleThreadExecutor()

    /** Decoding runs out of input part way through the image ("Input was incomplete."). */
    @Test
    fun jpegCutShortInItsScanData_isKeptWithoutAThumbnail() {
        truncatedJpegTest { jpeg -> jpeg.size / 2 }
    }

    /**
     * The decoder cannot even be created. The Exif rewrite copies everything from the SOS marker on
     * without looking at it, so a JPEG that ends right after the marker is still saved.
     */
    @Test
    fun jpegCutShortInItsScanHeader_isKeptWithoutAThumbnail() {
        truncatedJpegTest { jpeg -> startOfScan(jpeg) + 2 }
    }

    private fun truncatedJpegTest(cutAt: (ByteArray) -> Int) {
        val whole = noiseJpeg()
        val jpeg = whole.copyOf(cutAt(whole))

        val crash = AtomicReference<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + CoroutineExceptionHandler { _, e -> crash.set(e) },
        )
        val events = CopyOnWriteArrayList<CapturedImageEvent>()
        val thumbnailStageOver = CountDownLatch(1)
        val stored = slot<ByteArray>()

        val saver = imageSaver(jpeg = jpeg, scope = scope, stored = stored) { event ->
            events += event
            if (event is CapturedImageEvent.ThumbnailReady) {
                thumbnailStageOver.countDown()
            }
        }

        try {
            saver.onCaptureSuccess(mockk<ImageProxy>(relaxed = true))

            val isOver = thumbnailStageOver.await(TIMEOUT_S, TimeUnit.SECONDS)
            crash.get()?.let { throw AssertionError("the thumbnail stage crashed", it) }
            assertTrue("the thumbnail stage never finished", isOver)

            assertEquals(1, events.filterIsInstance<CapturedImageEvent.Saved>().size)
            val thumbnail = events.filterIsInstance<CapturedImageEvent.ThumbnailReady>().single()
            assertNull("a thumbnail was made for a capture that has none", thumbnail.thumbnail)

            // Everything from the SOS marker on is written out exactly as the HAL produced it
            val scan = jpeg.copyOfRange(startOfScan(jpeg), jpeg.size)
            val bytes = stored.captured
            assertArrayEquals(scan, bytes.copyOfRange(bytes.size - scan.size, bytes.size))
        } finally {
            scope.cancel()
            pipelineThread.shutdown()
        }
    }

    private fun imageSaver(
        jpeg: ByteArray,
        scope: CoroutineScope,
        stored: CapturingSlot<ByteArray>,
        onEvent: (CapturedImageEvent) -> Unit,
    ): ImageSaver {
        val jpegExtractor = mockk<JpegExtractor>()
        every { jpegExtractor.extract(any(), any()) } returns CapturedJpeg(
            jpegBytes = jpeg,
            cropRect = null,
            orientationDegrees = 0,
            shouldUseExifOrientation = false,
            isUltraHdr = false,
        )
        val storeCapturedImage = mockk<StoreCapturedImage>()
        coEvery { storeCapturedImage(capture(stored), any(), any(), any()) } returns
            StoreCapturedImageResult.Stored(Uri.EMPTY)

        return ImageSaver(
            request = CaptureImageRequest(
                storageLocation = CapturedItemRepository.MEDIA_STORE_LOCATION,
                includeLocation = false,
                saveAsPreviewed = false,
                removeExif = false,
                targetThumbnailWidth = THUMBNAIL_SIZE,
                targetThumbnailHeight = THUMBNAIL_SIZE,
            ),
            metadata = CaptureMetadata(),
            jpegQuality = JPEG_QUALITY,
            needsThumbnail = { true },
            onEvent = onEvent,
            storeCapturedImage = storeCapturedImage,
            exifMapper = CapturedImageExifMapperImpl(),
            jpegExtractor = jpegExtractor,
            pipeline = backgroundPipeline(scope),
            scope = scope,
            mainDispatcher = Dispatchers.Main.immediate,
        )
    }

    private fun backgroundPipeline(scope: CoroutineScope): CapturedImagePipeline {
        return object : CapturedImagePipeline {
            override fun enqueueExtraction(extraction: suspend () -> Unit) {
                scope.launch(pipelineThread.asCoroutineDispatcher()) { extraction() }
            }

            override fun enqueueWrite(write: suspend () -> Unit) {
                scope.launch(pipelineThread.asCoroutineDispatcher()) { write() }
            }

            override fun enqueueThumbnail(thumbnail: suspend () -> Unit) {
                scope.launch(pipelineThread.asCoroutineDispatcher()) { thumbnail() }
            }
        }
    }

    /** Noise keeps the scan data long, so that cutting it in half lands well inside it. */
    private fun noiseJpeg(): ByteArray {
        val random = Random(0)
        val pixels = IntArray(WIDTH * HEIGHT) { random.nextInt() or (0xff shl 24) }
        val bitmap = Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            it.toByteArray()
        }
    }

    /** The offset of the SOS marker, after which nothing but scan data follows. */
    private fun startOfScan(jpeg: ByteArray): Int {
        for (i in 0 until jpeg.size - 1) {
            if (jpeg[i] == 0xff.toByte() && jpeg[i + 1] == 0xda.toByte()) return i
        }
        throw AssertionError("no SOS marker")
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 480
        const val THUMBNAIL_SIZE = 64
        const val JPEG_QUALITY = 90
        const val TIMEOUT_S = 10L
    }
}
