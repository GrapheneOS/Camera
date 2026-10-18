package app.grapheneos.camera.ui.gallery.screen.model

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem

internal sealed interface GalleryScreenEffect {

    data object NothingToShow : GalleryScreenEffect

    data object OpenSystemGallery : GalleryScreenEffect

    data object ItemDeleted : GalleryScreenEffect

    data object LastItemDeleted : GalleryScreenEffect

    data object DeletionFailed : GalleryScreenEffect

    data object DetailsUnavailable : GalleryScreenEffect

    data class OpenVideo(
        val uri: Uri,
    ) : GalleryScreenEffect

    data class Edit(
        val item: CapturedItem,
        val usesDefaultEditor: Boolean,
    ) : GalleryScreenEffect

    data class Share(
        val item: CapturedItem,
    ) : GalleryScreenEffect
}
