package app.grapheneos.camera.domain.capture.model

import android.location.Location

data class CaptureMetadata(
    val reversedHorizontal: Boolean = false,
    val reversedVertical: Boolean = false,
    val location: Location? = null,
)
