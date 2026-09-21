package app.grapheneos.camera.domain.capture.model

import androidx.annotation.Px

data class CaptureImageRequest(
    val storageLocation: String,
    val includeLocation: Boolean,
    val saveAsPreviewed: Boolean,
    val removeExif: Boolean,
    @Px val targetThumbnailWidth: Int,
    @Px val targetThumbnailHeight: Int,
)
