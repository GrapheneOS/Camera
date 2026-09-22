package app.grapheneos.camera.domain.capture.model

import android.graphics.Bitmap
import app.grapheneos.camera.CapturedItem

sealed interface CapturedImageEvent {

    data object Captured : CapturedImageEvent

    data object LocationUnavailable : CapturedImageEvent

    data object PreviewFailed : CapturedImageEvent

    data object StorageLocationNotFound : CapturedImageEvent

    data class CaptureFailed(
        val errorCode: Int,
        val cause: Throwable,
    ) : CapturedImageEvent

    data class Saved(
        val item: CapturedItem,
    ) : CapturedImageEvent

    data class PreviewCaptured(
        val bitmap: Bitmap,
    ) : CapturedImageEvent

    data class ThumbnailReady(
        val thumbnail: Bitmap,
    ) : CapturedImageEvent

    data class Failed(
        val cause: ImageSaverException,
        val alreadyReported: Boolean,
    ) : CapturedImageEvent
}
