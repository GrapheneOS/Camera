package app.grapheneos.camera.ui.viewfinder.screen.model

import android.graphics.Bitmap
import app.grapheneos.camera.CapturedItem

data class ViewfinderGalleryState(
    val thumbnail: Bitmap? = null,
    val secureCapturedItems: List<CapturedItem> = emptyList(),
)
