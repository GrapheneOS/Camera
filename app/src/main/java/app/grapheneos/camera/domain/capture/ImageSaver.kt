package app.grapheneos.camera.domain.capture

import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.Px
import androidx.camera.core.ImageProxy
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.IMAGE_NAME_PREFIX
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.data.camera.model.CapturedJpeg
import app.grapheneos.camera.data.camera.session.JpegExtractor
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapper
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.domain.capture.model.CapturedImageEvent
import app.grapheneos.camera.domain.capture.model.CapturedImageExif
import app.grapheneos.camera.domain.capture.model.ImageSaverException
import app.grapheneos.camera.domain.capture.model.ImageSaverException.Place
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedImage
import app.grapheneos.camera.util.ImageResizer
import java.io.IOException
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/*
Based on androidx.camera.core.ImageSaver

Main differences:
- image saving stages are pipelined: JpegExtractor.extract(), saveImage() and generateThumbnail() can
execute concurrently, each processing a different image
- image is written to storage only once, after all the processing is completed. androidx ImageSaver
writes and reads it back from storage multiple times
- generateThumbnail() stage which removes the need to do an expensive ContentResolver call to
open a Uri during the thumbnail generation
- ImageProxy isn't held open for the whole duration of storage IO, it's closed as soon as possible
 */
class ImageSaver(
    val scope: CoroutineScope,
    val mainDispatcher: CoroutineDispatcher,
    val storeCapturedImage: StoreCapturedImage,
    val exifMapper: CapturedImageExifMapper,
    val jpegExtractor: JpegExtractor,
    val jpegQuality: Int,
    val storageLocation: String,
    val imageFileFormat: String,
    val imageCaptureMetadata: CaptureMetadata,
    val removeExifAfterCapture: Boolean,
    @Px val targetThumbnailWidth: Int,
    @Px val targetThumbnailHeight: Int,
    val pipeline: CapturedImagePipeline,
    val needsThumbnail: () -> Boolean,
    val onEvent: (CapturedImageEvent) -> Unit,
) {
    val captureTime: ZonedDateTime = ZonedDateTime.now()

    fun onCaptureSuccess(image: ImageProxy) {
        onEvent(CapturedImageEvent.Captured)

        pipeline.enqueueExtraction {
            extractJpeg(image)
        }
    }

    private fun extractJpeg(image: ImageProxy) {
        capturedJpeg = try {
            jpegExtractor.extract(image, jpegQuality)
        } catch (e: Exception) {
            handleError(ImageSaverException(Place.IMAGE_EXTRACTION, e))
            return
        }

        pipeline.enqueueWrite(this::saveImage)
    }

    private suspend fun saveImage() {
        try {
            saveImageInner()
        } catch (e: ImageSaverException) {
            handleError(e)
            return
        }

        if (needsThumbnail()) {
            pipeline.enqueueThumbnail(this::generateThumbnail)
        }
    }

    private var capturedJpeg: CapturedJpeg? = null
    private lateinit var processedJpegBytes: ByteArray

    @Throws(ImageSaverException::class)
    private suspend fun saveImageInner() {
        val jpeg = requireNotNull(capturedJpeg)
        val jpegBytes = when (jpeg.cropRect) {
            null -> jpeg.jpegBytes

            else -> try {
                jpegExtractor.crop(jpeg, jpegQuality)
            } catch (e: Exception) {
                throw ImageSaverException(Place.IMAGE_CROPPING, e)
            }
        }

        processedJpegBytes = processExif(jpeg, jpegBytes)

        val startOfWriting = timestamp()

        val result = storeCapturedImage(
            jpegBytes = processedJpegBytes,
            storageLocation = storageLocation,
            fileName = fileName(),
            mimeType = mimeType(),
        )

        val uri = storedUri(result)
        logDuration(startOfWriting) {
            "image writing (saveToMediaStore: ${saveToMediaStore()})"
        }

        val capturedItem = CapturedItem(ITEM_TYPE_IMAGE, dateString(), uri)
        emitOnMainThread(CapturedImageEvent.Saved(item = capturedItem))
    }

    @Throws(ImageSaverException::class)
    private fun storedUri(result: StoreCapturedImageResult): Uri {
        return when (result) {
            is StoreCapturedImageResult.Stored -> result.uri

            is StoreCapturedImageResult.StorageLocationNotFound -> {
                emitOnMainThread(CapturedImageEvent.StorageLocationNotFound)
                skipErrorDialog = true
                throw ImageSaverException(Place.FILE_CREATION, result.cause)
            }

            is StoreCapturedImageResult.Failed -> {
                throw ImageSaverException(placeOf(result.stage), result.cause)
            }
        }
    }

    @Throws(ImageSaverException::class)
    private fun processExif(
        jpeg: CapturedJpeg,
        jpegBytes: ByteArray,
    ): ByteArray {
        val startOfExifProcessing = timestamp()

        val processed = try {
            exifMapper.map(
                CapturedImageExif(
                    jpegBytes = jpegBytes,
                    uncroppedJpegBytes = jpeg.jpegBytes,
                    isCropped = jpeg.cropRect != null,
                    orientationDegrees = jpeg.orientationDegrees,
                    shouldUseExifOrientation = jpeg.shouldUseExifOrientation,
                    metadata = imageCaptureMetadata,
                    removeExif = removeExifAfterCapture,
                    captureTime = captureTime,
                ),
            )
        } catch (e: Exception) {
            throw ImageSaverException(Place.EXIF_PARSING, e)
        }

        // let GC collect this large buffer
        capturedJpeg = null

        logDuration(startOfExifProcessing) {"exif processing"}

        return processed
    }

    private fun generateThumbnail() {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(processedJpegBytes))
        val bitmap = try {
            ImageDecoder.decodeBitmap(source, ImageResizer(targetThumbnailWidth, targetThumbnailHeight))
        } catch (e: IOException) {
            // reading from a ByteBuffer should never cause an IOException
            throw IllegalStateException("unable to generate a thumbnail", e)
        }
        emitOnMainThread(CapturedImageEvent.ThumbnailReady(thumbnail = bitmap))
    }

    fun saveToMediaStore() = storageLocation == CapturedItemRepository.MEDIA_STORE_LOCATION

    private fun dateString() =
        // it's important to include milliseconds (SSS), otherwise new image may overwrite the previous one
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US).format(captureTime)

    private fun fileName(): String {
        return IMAGE_NAME_PREFIX + dateString() + imageFileFormat
    }

    private fun mimeType() = MimeTypeMap.getSingleton().getMimeTypeFromExtension(imageFileFormat) ?: "image/*"

    private fun placeOf(stage: StoreCapturedImageResult.Stage): Place {
        return when (stage) {
            StoreCapturedImageResult.Stage.FILE_CREATION -> Place.FILE_CREATION
            StoreCapturedImageResult.Stage.FILE_WRITE -> Place.FILE_WRITE
            StoreCapturedImageResult.Stage.FILE_WRITE_COMPLETION -> Place.FILE_WRITE_COMPLETION
        }
    }

    private var skipErrorDialog = false

    private fun handleError(exception: ImageSaverException) {
        emitOnMainThread(
            CapturedImageEvent.Failed(
                cause = exception,
                alreadyReported = skipErrorDialog,
            ),
        )
    }

    private fun emitOnMainThread(event: CapturedImageEvent) {
        scope.launch(mainDispatcher) {
            onEvent(event)
        }
    }

    companion object {
        private const val TAG = "ImageSaver"
        private const val LOG_DURATION = false
    }

    private fun timestamp() = if (LOG_DURATION) System.nanoTime() else 0

    private fun logDuration(start: Long, lazyMessage: () -> String) {
        if (LOG_DURATION) {
            val now = timestamp()
            val us = (now - start) / 1000
            val durationStr = if (us < 10_000) "$us us" else "${us / 1000} ms"
            Log.d(TAG, "${lazyMessage()}: $durationStr")
        }
    }
}
