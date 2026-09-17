package app.grapheneos.camera.domain.capture.model

import java.util.Date

class CapturedImageExif(
    val jpegBytes: ByteArray,
    val uncroppedJpegBytes: ByteArray,
    val isCropped: Boolean,
    val orientationDegrees: Int,
    val shouldUseExifOrientation: Boolean,
    val metadata: CaptureMetadata,
    val removeExif: Boolean,
    val captureTime: Date,
)
