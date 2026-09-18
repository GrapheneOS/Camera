package app.grapheneos.camera.data.camera.model

import android.graphics.Rect

class CapturedJpeg(
    val jpegBytes: ByteArray,
    val cropRect: Rect?,
    val orientationDegrees: Int,
    val shouldUseExifOrientation: Boolean,
)
