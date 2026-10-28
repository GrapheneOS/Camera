package app.grapheneos.camera.ui.gallery.screen.model

import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.GeoCoordinates

internal sealed interface GalleryScreenEffect {

    /**
     * Opens a screen outside the secure session. A secure session must never send one: it would
     * open over the lock screen with the owner's apps and data behind it.
     */
    sealed interface OutsideSession : GalleryScreenEffect

    data object ItemDeleted : GalleryScreenEffect

    data object RestoreFailed : GalleryScreenEffect

    data object DeletionFailed : GalleryScreenEffect

    data object TextCopied : GalleryScreenEffect

    data object OpenSystemGallery : OutsideSession

    data class ShowInSystemGallery(
        val item: CapturedItem,
    ) : OutsideSession

    data class Edit(
        val item: CapturedItem,
        val usesDefaultEditor: Boolean,
    ) : OutsideSession

    data class OpenMap(
        val location: GeoCoordinates,
    ) : OutsideSession

    data class Share(
        val item: CapturedItem,
    ) : OutsideSession

    data class UseAs(
        val item: CapturedItem,
    ) : OutsideSession
}
