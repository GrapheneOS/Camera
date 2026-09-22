package app.grapheneos.camera.data.camera.session

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.camera.core.internal.compat.workaround.ExifRotationAvailability
import androidx.camera.core.internal.utils.ImageUtil
import app.grapheneos.camera.data.camera.model.CapturedJpeg
import app.grapheneos.camera.data.camera.model.CapturedJpegCropRect
import java.io.ByteArrayOutputStream
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

                else -> throw IllegalArgumentException("unknown imageFormat $imageFormat")
            }

            val capturedCropRect = cropRect?.let { rect ->
                CapturedJpegCropRect(
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom,
                )
            }

            CapturedJpeg(
                jpegBytes = jpegBytes,
                cropRect = capturedCropRect,
                orientationDegrees = image.imageInfo.rotationDegrees,
                shouldUseExifOrientation = ExifRotationAvailability()
                    .shouldUseExifOrientation(image),
            )
        }
    }

    override fun crop(
        jpeg: CapturedJpeg,
        jpegQuality: Int,
    ): ByteArray {
        val cropRect = requireNotNull(jpeg.cropRect) {
            "the captured JPEG has nothing to crop"
        }
        val regionRect = Rect(
            cropRect.left,
            cropRect.top,
            cropRect.right,
            cropRect.bottom,
        )

        val decoder = regionDecoder(jpeg.jpegBytes)
        val bitmap = try {
            val region = decoder.decodeRegion(regionRect, BitmapFactory.Options())
            checkNotNull(region) { "unable to decode the cropped region" }
        } finally {
            decoder.recycle()
        }

        val output = ByteArrayOutputStream()
        try {
            val isEncoded = bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
            check(isEncoded) { "unable to encode the cropped JPEG" }
        } finally {
            bitmap.recycle()
        }

        return output.toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun regionDecoder(jpegBytes: ByteArray): BitmapRegionDecoder {
        return BitmapRegionDecoder.newInstance(jpegBytes, 0, jpegBytes.size, false)
    }
}
