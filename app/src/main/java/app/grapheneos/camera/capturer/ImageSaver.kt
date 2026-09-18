package app.grapheneos.camera.capturer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.ImageFormat
import android.graphics.Rect
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.Px
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.internal.compat.workaround.ExifRotationAvailability
import androidx.camera.core.internal.utils.ImageUtil
import app.grapheneos.camera.CapturedItem
import app.grapheneos.camera.IMAGE_NAME_PREFIX
import app.grapheneos.camera.ITEM_TYPE_IMAGE
import app.grapheneos.camera.capturer.ImageSaverException.Place
import app.grapheneos.camera.data.media.repository.CapturedItemRepository
import app.grapheneos.camera.domain.capture.mapper.CapturedImageExifMapper
import app.grapheneos.camera.domain.capture.model.CaptureMetadata
import app.grapheneos.camera.domain.capture.model.CapturedImageExif
import app.grapheneos.camera.domain.capture.model.StoreCapturedImageResult
import app.grapheneos.camera.domain.capture.usecase.StoreCapturedImage
import app.grapheneos.camera.util.ImageResizer
import app.grapheneos.camera.util.executeIfAlive
import java.io.IOException
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/*
Based on androidx.camera.core.ImageSaver

Main differences:
- image saving stages are pipelined: extractJpegBytes(), saveImage() and generateThumbnail() can
execute concurrently, each processing a different image
- image is written to storage only once, after all the processing is completed. androidx ImageSaver
writes and reads it back from storage multiple times
- generateThumbnail() stage which removes the need to do an expensive ContentResolver call to
open a Uri during the thumbnail generation
- ImageProxy isn't held open for the whole duration of storage IO, it's closed as soon as possible
 */
class ImageSaver(
    val imageCapturer: ImageCapturer,
    val appContext: Context,
    val storeCapturedImage: StoreCapturedImage,
    val exifMapper: CapturedImageExifMapper,
    val jpegQuality: Int,
    val storageLocation: String,
    val imageFileFormat: String,
    val imageCaptureMetadata: CaptureMetadata,
    val removeExifAfterCapture: Boolean,
    @Px val targetThumbnailWidth: Int,
    @Px val targetThumbnailHeight: Int,
) : ImageCapture.OnImageCapturedCallback()
{
    val captureTime: ZonedDateTime = ZonedDateTime.now()
    val mainThreadExecutor: Executor = appContext.mainExecutor

    private var isCancelled = false

    fun cancelCaptureRequest() {
        isCancelled = true
    }

    override fun onCaptureSuccess(image: ImageProxy) {
        mainThreadExecutor.execute(imageCapturer::onCaptureSuccess)

        try {
            extractJpegBytes(image)
        } catch (e: Exception) {
            handleError(ImageSaverException(Place.IMAGE_EXTRACTION, e))
            return
        }

        imageWriterExecutor.execute(this::saveImage)
    }

    // based on androidx.camera.core.ImageSaver#imageToJpegByteArray(),
    // optimized to avoid extracting uncropped image twice and to close ImageProxy sooner
    @SuppressLint("RestrictedApi")
    @Throws(ImageUtil.CodecFailedException::class)
    private fun extractJpegBytes(image: ImageProxy) {
        try {
            cropRect = if (ImageUtil.shouldCropImage(image)) image.cropRect else null
            val imageFormat = image.format

            origJpegBytes = if (imageFormat == ImageFormat.JPEG) {
                ImageUtil.jpegImageToJpegByteArray(image)
            } else if (imageFormat == ImageFormat.YUV_420_888) {
                ImageUtil.yuvImageToJpegByteArray(image, cropRect, jpegQuality, 0)
            } else {
                throw IllegalStateException("unknown imageFormat $imageFormat")
            }

            shouldUseExifOrientation = ExifRotationAvailability().shouldUseExifOrientation(image)
            orientation = image.imageInfo.rotationDegrees
        } finally {
            /*
             from javadoc of the Image class:
             Since Images are often directly produced or consumed by hardware components, they are
             a limited resource shared across the system, and should be closed as soon as
             they are no longer needed.
             */
            image.close()
        }
    }

    private fun saveImage() {
        try {
            saveImageInner()
        } catch (e: ImageSaverException) {
            handleError(e)
            return
        }

        imageCapturer.mActivity.thumbnailLoaderExecutor.executeIfAlive(this::generateThumbnail)
    }

    private var cropRect: Rect? = null
    private var origJpegBytes: ByteArray? = null
    private lateinit var processedJpegBytes: ByteArray
    private var shouldUseExifOrientation = false
    private var orientation = 0

    @Throws(ImageSaverException::class)
    private fun saveImageInner() {
        val uncroppedJpegBytes = origJpegBytes!!
        if (cropRect != null) {
            try {
                // cropJpegByteArray call is slow, overhead from reflection doesn't matter in this case
                // copying out cropJpegByteArray method isn't worth the maintenance burden
                val cropJpegByteArray = ImageUtil::class.java.getDeclaredMethod(
                    "cropJpegByteArray",
                    ByteArray::class.java, Rect::class.java, Int::class.javaPrimitiveType)
                cropJpegByteArray.isAccessible = true
                origJpegBytes = cropJpegByteArray.invoke(null, uncroppedJpegBytes, cropRect, jpegQuality) as ByteArray
            } catch (e: Exception) {
                throw ImageSaverException(Place.IMAGE_CROPPING, e)
            }
        }

        processedJpegBytes = processExif(uncroppedJpegBytes)

        val startOfWriting = timestamp()

        val result = runBlocking {
            storeCapturedImage(
                jpegBytes = processedJpegBytes,
                storageLocation = storageLocation,
                fileName = fileName(),
                mimeType = mimeType(),
            )
        }

        val uri = when (result) {
            is StoreCapturedImageResult.Stored -> result.uri

            is StoreCapturedImageResult.StorageLocationNotFound -> {
                mainThreadExecutor.execute(imageCapturer::onStorageLocationNotFound)
                skipErrorDialog = true
                throw ImageSaverException(Place.FILE_CREATION, result.cause)
            }

            is StoreCapturedImageResult.Failed -> {
                throw ImageSaverException(placeOf(result.stage), result.cause)
            }
        }
        logDuration(startOfWriting) {"image writing (saveToMediaStore: ${saveToMediaStore()})"}

        val capturedItem = CapturedItem(ITEM_TYPE_IMAGE, dateString(), uri)
        mainThreadExecutor.execute { imageCapturer.onImageSaverSuccess(capturedItem) }
    }

    @Throws(ImageSaverException::class)
    private fun processExif(uncroppedJpegBytes: ByteArray): ByteArray {
        val startOfExifProcessing = timestamp()

        val processed = try {
            exifMapper.map(
                CapturedImageExif(
                    jpegBytes = requireNotNull(origJpegBytes),
                    uncroppedJpegBytes = uncroppedJpegBytes,
                    isCropped = cropRect != null,
                    orientationDegrees = orientation,
                    shouldUseExifOrientation = shouldUseExifOrientation,
                    metadata = imageCaptureMetadata,
                    removeExif = removeExifAfterCapture,
                    captureTime = captureTime,
                ),
            )
        } catch (e: Exception) {
            throw ImageSaverException(Place.EXIF_PARSING, e)
        }

        // let GC collect this large buffer
        origJpegBytes = null

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
        mainThreadExecutor.execute { imageCapturer.onThumbnailGenerated(bitmap) }
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

    // implementation of ImageCapture.OnImageCapturedCallback.onError
    override fun onError(exception: ImageCaptureException) {
        mainThreadExecutor.execute {
            if (isCancelled) return@execute
            imageCapturer.onCaptureError(exception)
        }
    }

    private var skipErrorDialog = false

    private fun handleError(e: ImageSaverException) {
        mainThreadExecutor.execute { imageCapturer.onImageSaverError(e, skipErrorDialog) }
    }

    companion object {
        val imageCaptureCallbackExecutor = Executors.newSingleThreadExecutor()
        private val imageWriterExecutor = Executors.newSingleThreadExecutor()

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
