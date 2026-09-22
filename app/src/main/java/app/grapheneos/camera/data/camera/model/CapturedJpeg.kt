package app.grapheneos.camera.data.camera.model

class CapturedJpeg(
    val jpegBytes: ByteArray,
    val cropRect: CapturedJpegCropRect?,
    val orientationDegrees: Int,
    val shouldUseExifOrientation: Boolean,
)

data class CapturedJpegCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
