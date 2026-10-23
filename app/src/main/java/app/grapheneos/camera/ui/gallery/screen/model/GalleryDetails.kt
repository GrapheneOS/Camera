package app.grapheneos.camera.ui.gallery.screen.model

import app.grapheneos.camera.data.media.model.CapturedItemDetails

internal sealed interface GalleryDetails {

    data object Loading : GalleryDetails

    data class Loaded(
        val details: CapturedItemDetails,
    ) : GalleryDetails

    data object Unavailable : GalleryDetails
}
