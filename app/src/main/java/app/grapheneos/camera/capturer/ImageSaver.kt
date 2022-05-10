package app.grapheneos.camera.capturer

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.ImageFormat
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.system.Os
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.Px
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.internal.compat.workaround.ExifRotationAvailability
import androidx.camera.core.internal.utils.ImageUtil
import androidx.documentfile.provider.DocumentFile
import androidxc.camera.core.impl.utils.Exif
import app.grapheneos.camera.CamConfig
import app.grapheneos.camera.capturer.ImageSaverException.Place
import app.grapheneos.camera.clearExif
import app.grapheneos.camera.fixExif
import app.grapheneos.camera.util.ImageResizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

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
    val jpegQuality: Int,
    val storageLocation: String,
    val imageFileFormat: String,
    val imageCaptureMetadata: ImageCapture.Metadata,
    val removeExifAfterCapture: Boolean,
    @Px val targetThumbnailWidth: Int,
    @Px val targetThumbnailHeight: Int,
) : ImageCapture.OnImageCapturedCallback()
{
    val captureTime = Date()
    val contentResolver = appContext.contentResolver
    val mainThreadExecutor = appContext.mainExecutor

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
                ImageUtil.yuvImageToJpegByteArray(image, cropRect, jpegQuality)
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
        thumbnailGeneratorExecutor.execute(this::generateThumbnail)
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

        val uri = try {
            obtainOutputUri()!!
        } catch (e: Exception) {
            throw ImageSaverException(Place.FILE_CREATION, e)
        }

        try {
            contentResolver.openFileDescriptor(uri, "w")!!.use {
                val fd = it.fileDescriptor
                val bytes = processedJpegBytes
                var off = 0
                val len = bytes.size
                do {
                    // "-1" is never returned to indicate an error, ErrnoException is thrown instead
                    off += Os.write(fd, bytes, off, len - off)
                } while (off != len)

                Os.fsync(fd)
            }
        } catch (e: Exception) {
            deleteIncompleteImage(uri)
            throw ImageSaverException(Place.FILE_WRITE, e)
        }

        if (saveToMediaStore()) {
            try {
                removePendingFlagFromUri(uri)
            } catch (e: Exception) {
                // don't delete the image in this case, since it's already fully written out
                throw ImageSaverException(Place.FILE_WRITE_COMPLETION, e)
            }
        }
        logDuration(startOfWriting) {"image writing (saveToMediaStore: ${saveToMediaStore()})"}

        mainThreadExecutor.execute { imageCapturer.onImageSaverSuccess(uri) }
    }

    // based on EXIF update sequence in androidx.camera.core.ImageSaver#saveImageToTempFile(),
    // optimized to skip writing of the unfinished image to storage
    @Throws(ImageSaverException::class)
    private fun processExif(uncroppedJpegBytes: ByteArray): ByteArray {
        val startOfExifProcessing = timestamp()

        val exif: Exif
        try {
            exif = Exif.createFromInputStream(ByteArrayInputStream(origJpegBytes))
            if (cropRect != null) {
                val orig = Exif.createFromInputStream(ByteArrayInputStream(uncroppedJpegBytes))
                orig.copyToCroppedImage(exif)
            }
        } catch (e: Exception) {
            throw ImageSaverException(Place.EXIF_PARSING, e)
        }

        // Overwrite the original orientation if the quirk exists.
        if (!shouldUseExifOrientation) {
            exif.rotate(orientation)
        }
        val metadata = imageCaptureMetadata
        if (metadata.isReversedHorizontal) {
            exif.flipHorizontally()
        }
        if (metadata.isReversedVertical) {
            exif.flipVertically()
        }

        val exifInterface = exif.exifInterface

        if (removeExifAfterCapture) {
            // TODO improve clearExif() by moving it into ExifInterface
            exifInterface.clearExif()
        } else {
            exifInterface.fixExif(captureTime)
        }

        // location metadata setting intentionally ignores the "clear EXIF after capture" setting
        val location = metadata.location
        if (location != null) {
            exif.attachLocation(location)
        }

        val baos = ByteArrayOutputStream(origJpegBytes!!.size +
                // make sure buffer doesn't need to be resized due to additional EXIF attributes
                (100 * 1024))

        try {
            exifInterface.saveAttributes(ByteArrayInputStream(origJpegBytes), baos)
        } catch (e: Exception) {
            throw ImageSaverException(Place.EXIF_PARSING, e)
        }

        // let GC collect this large buffer
        origJpegBytes = null

        val res = baos.toByteArray()

        logDuration(startOfExifProcessing) {"exif processing"}

        return res
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

    fun saveToMediaStore() = storageLocation.isEmpty()

    private fun fileName(): String {
        // it's important to include milliseconds (SSS), otherwise new image may overwrite the previous one
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        return "IMG_${sdf.format(captureTime)}$imageFileFormat"
    }

    private fun mimeType() = MimeTypeMap.getSingleton().getMimeTypeFromExtension(imageFileFormat) ?: "image/*"

    @Throws(FileNotFoundException::class)
    fun obtainOutputUri(): Uri? {
        if (saveToMediaStore()) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName())
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType())
                put(MediaStore.MediaColumns.RELATIVE_PATH,"DCIM/Camera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            return contentResolver.insert(CamConfig.imageCollectionUri, cv)
        } else {
            try {
                val parent = DocumentFile.fromTreeUri(appContext, Uri.parse(storageLocation))!!
                return parent.createFile(mimeType(), fileName())!!.uri
            } catch (npe: NullPointerException) {
                appContext.mainExecutor.execute(imageCapturer::onStorageLocationNotFound)
                skipErrorDialog = true
                throw FileNotFoundException("The default storage location seems to have been deleted")
            }
        }
    }

    @Throws(IOException::class)
    fun removePendingFlagFromUri(uri: Uri) {
        val cv = ContentValues()
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
        if (contentResolver.update(uri, cv, null, null) != 1) {
            throw IOException("unable to remove IS_PENDING flag")
        }
    }

    private fun deleteIncompleteImage(uri: Uri) {
        try {
            val num = contentResolver.delete(uri, null, null)
            check(num == 1) { "unexpected number of deleted rows: $num" }
        } catch (deleteException: Exception) {
            Log.w(TAG, "unable to delete an incomplete image $uri", deleteException)
        }
    }

    // implementation of ImageCapture.OnImageCapturedCallback.onError
    override fun onError(exception: ImageCaptureException) {
        mainThreadExecutor.execute{ imageCapturer.onCaptureError(exception) }
    }

    private var skipErrorDialog = false

    private fun handleError(e: ImageSaverException) {
        mainThreadExecutor.execute { imageCapturer.onImageSaverError(e, skipErrorDialog) }
    }

    companion object {
        val imageCaptureCallbackExecutor = Executors.newSingleThreadExecutor()
        private val imageWriterExecutor = Executors.newSingleThreadExecutor()
        private val thumbnailGeneratorExecutor = Executors.newSingleThreadExecutor()

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
