package app.grapheneos.camera.domain.capture.model

import java.time.ZonedDateTime

class CapturedImageExif(
    val jpegBytes: ByteArray,
    val uncroppedJpegBytes: ByteArray,
    val isCropped: Boolean,
    val orientationDegrees: Int,
    val shouldUseExifOrientation: Boolean,
    val metadata: CaptureMetadata,
    val removeExif: Boolean,
    val captureTime: ZonedDateTime,
)
