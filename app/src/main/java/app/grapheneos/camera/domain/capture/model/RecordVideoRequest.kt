package app.grapheneos.camera.domain.capture.model

import android.net.Uri

data class RecordVideoRequest(
    val foreignUri: Uri?,
    val includeLocation: Boolean,
)
