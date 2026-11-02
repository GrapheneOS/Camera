package app.grapheneos.camera.ui.viewfinder.screen.model.delegate

import android.graphics.Bitmap
import app.grapheneos.camera.data.media.model.CapturedItem

sealed interface ViewfinderCaptureEvent {

    data object StorageLocationNotFound : ViewfinderCaptureEvent

    data object SelfTimerFinished : ViewfinderCaptureEvent

    data class ThumbnailReady(
        val thumbnail: Bitmap?,
    ) : ViewfinderCaptureEvent

    data class Saved(
        val item: CapturedItem,
    ) : ViewfinderCaptureEvent
}
