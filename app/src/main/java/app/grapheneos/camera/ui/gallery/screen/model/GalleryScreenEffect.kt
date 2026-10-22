package app.grapheneos.camera.ui.gallery.screen.model

import android.net.Uri
import app.grapheneos.camera.data.media.model.CapturedItem
import app.grapheneos.camera.data.media.model.GeoCoordinates

internal sealed interface GalleryScreenEffect {

    /**
     * Hands the item or its data to another app. A secure session must never send one: the other
     * app would open over the lock screen with the owner's apps and data behind it.
     */
    sealed interface OtherApp : GalleryScreenEffect

    data object ItemDeleted : GalleryScreenEffect

    data object ItemTrashed : GalleryScreenEffect

    data object RestoreFailed : GalleryScreenEffect

    data object DeletionFailed : GalleryScreenEffect

    data object OpenSystemGallery : OtherApp

    data class CopyText(
        val text: String,
    ) : GalleryScreenEffect

    data class OpenVideo(
        val uri: Uri,
    ) : GalleryScreenEffect

    data class Edit(
        val item: CapturedItem,
        val usesDefaultEditor: Boolean,
    ) : OtherApp

    data class OpenMap(
        val location: GeoCoordinates,
    ) : OtherApp

    data class Share(
        val item: CapturedItem,
    ) : OtherApp

    data class UseAs(
        val item: CapturedItem,
    ) : OtherApp
}
