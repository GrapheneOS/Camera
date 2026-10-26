package app.grapheneos.camera.ui.gallery.screen.model

import app.grapheneos.camera.data.media.model.CapturedItem

internal data class GalleryFullPhoto(
    val item: CapturedItem,
    val image: GalleryPreview,
)
