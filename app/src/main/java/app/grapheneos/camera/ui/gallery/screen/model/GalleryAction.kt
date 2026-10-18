package app.grapheneos.camera.ui.gallery.screen.model

internal sealed interface GalleryAction {

    data object ScreenTapped : GalleryAction

    data object VideoClicked : GalleryAction

    data object SystemGalleryClicked : GalleryAction

    data object EditClicked : GalleryAction

    data object EditWithClicked : GalleryAction

    data object DeleteClicked : GalleryAction

    data object DeleteConfirmed : GalleryAction

    data object InfoClicked : GalleryAction

    data object ShareClicked : GalleryAction

    data object DialogDismissed : GalleryAction

    data class ZoomChanged(
        val isZoomed: Boolean,
    ) : GalleryAction

    data class PageSelected(
        val page: Int,
    ) : GalleryAction
}
