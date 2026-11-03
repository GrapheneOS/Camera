package app.grapheneos.camera.ui.viewfinder.screen.model.state

import android.graphics.Bitmap
import android.net.Uri

sealed interface ViewfinderCaptureResult {

    val image: Bitmap?

    data class Photo(
        override val image: Bitmap,
    ) : ViewfinderCaptureResult

    data class Video(
        val uri: Uri,
        override val image: Bitmap?,
    ) : ViewfinderCaptureResult
}
