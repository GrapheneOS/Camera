package app.grapheneos.camera.ui.gallery.screen.model

import androidx.compose.ui.graphics.ImageBitmap

internal sealed interface GalleryPreview {

    data object Loading : GalleryPreview

    data class Loaded(
        val image: ImageBitmap,
    ) : GalleryPreview

    data object Unavailable : GalleryPreview
}
