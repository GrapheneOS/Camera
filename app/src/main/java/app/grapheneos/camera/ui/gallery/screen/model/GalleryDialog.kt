package app.grapheneos.camera.ui.gallery.screen.model

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.CapturedItemDetails

internal sealed interface GalleryDialog {

    data object DeleteConfirmation : GalleryDialog

    data class Details(
        val item: CapturedItem,
        val details: CapturedItemDetails,
    ) : GalleryDialog
}
