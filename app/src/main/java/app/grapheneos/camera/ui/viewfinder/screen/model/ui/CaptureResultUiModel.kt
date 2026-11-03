package app.grapheneos.camera.ui.viewfinder.screen.model.ui

import android.graphics.Bitmap

data class CaptureResultUiModel(
    val visible: Boolean = false,
    val image: Bitmap? = null,
)
