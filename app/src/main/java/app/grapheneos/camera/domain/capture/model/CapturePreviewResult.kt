package app.grapheneos.camera.domain.capture.model

import android.graphics.Bitmap

sealed interface CapturePreviewResult {

    data object Unavailable : CapturePreviewResult

    data object Failed : CapturePreviewResult

    data class Captured(
        val bitmap: Bitmap,
    ) : CapturePreviewResult
}
