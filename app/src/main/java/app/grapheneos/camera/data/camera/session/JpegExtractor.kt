package app.grapheneos.camera.data.camera.session

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.camera.core.internal.compat.workaround.ExifRotationAvailability
import androidx.camera.core.internal.utils.ImageUtil
import app.grapheneos.camera.data.camera.model.CapturedJpeg
import javax.inject.Inject

interface JpegExtractor {

    fun extract(image: ImageProxy, jpegQuality: Int): CapturedJpeg

    fun crop(jpeg: CapturedJpeg, jpegQuality: Int): ByteArray
}

internal class JpegExtractorImpl @Inject constructor() : JpegExtractor {

    // based on androidx.camera.core.ImageSaver#imageToJpegByteArray(),
    // optimized to avoid extracting uncropped image twice and to close ImageProxy sooner
    @SuppressLint("RestrictedApi")
    override fun extract(
        image: ImageProxy,
        jpegQuality: Int,
    ): CapturedJpeg {
        /*
         from javadoc of the Image class:
         Since Images are often directly produced or consumed by hardware components, they are
         a limited resource shared across the system, and should be closed as soon as
         they are no longer needed.
         */
        return image.use {
            val cropRect = when {
                ImageUtil.shouldCropImage(image) -> image.cropRect
                else -> null
            }

            val jpegBytes = when (val imageFormat = image.format) {
                ImageFormat.JPEG -> {
                    ImageUtil.jpegImageToJpegByteArray(image)
                }

                ImageFormat.YUV_420_888 -> {
                    ImageUtil.yuvImageToJpegByteArray(image, cropRect, jpegQuality, 0)
                }

                else -> error("unknown imageFormat $imageFormat")
            }

            CapturedJpeg(
                jpegBytes = jpegBytes,
                cropRect = cropRect,
                orientationDegrees = image.imageInfo.rotationDegrees,
                shouldUseExifOrientation = ExifRotationAvailability()
                    .shouldUseExifOrientation(image),
            )
        }
    }

    @SuppressLint("RestrictedApi")
    override fun crop(
        jpeg: CapturedJpeg,
        jpegQuality: Int,
    ): ByteArray {
        // cropJpegByteArray call is slow, overhead from reflection doesn't matter in this case
        // copying out cropJpegByteArray method isn't worth the maintenance burden
        val cropJpegByteArray = ImageUtil::class.java.getDeclaredMethod(
            "cropJpegByteArray",
            ByteArray::class.java,
            Rect::class.java,
            Int::class.javaPrimitiveType,
        )
        cropJpegByteArray.isAccessible = true

        val cropped = cropJpegByteArray.invoke(
            null,
            jpeg.jpegBytes,
            jpeg.cropRect,
            jpegQuality,
        )

        return cropped as ByteArray
    }
}
