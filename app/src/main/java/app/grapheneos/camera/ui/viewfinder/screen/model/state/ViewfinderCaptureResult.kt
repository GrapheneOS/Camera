package app.grapheneos.camera.ui.viewfinder.screen.model.state

import android.graphics.Bitmap

sealed interface ViewfinderCaptureResult {

    val image: Bitmap?

    data class Photo(
        override val image: Bitmap,
    ) : ViewfinderCaptureResult

    data class Video(
        override val image: Bitmap?,
    ) : ViewfinderCaptureResult
}
