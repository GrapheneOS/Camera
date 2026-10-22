package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.ui.graphics.ImageBitmap

internal sealed interface GalleryPreview {

    data object Loading : GalleryPreview

    data class Loaded(
        val image: ImageBitmap,
        val isHdr: Boolean = false,
    ) : GalleryPreview

    data object Unavailable : GalleryPreview
}
