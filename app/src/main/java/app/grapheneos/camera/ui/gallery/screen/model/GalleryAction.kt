package app.grapheneos.camera.ui.gallery.screen.model

internal sealed interface GalleryAction {

    sealed interface Details : GalleryAction

    data object OpenMapClicked : Details

    sealed interface Deletion : GalleryAction

    data object DeleteClicked : Deletion

    data object DeleteConfirmed : Deletion

    data object UndoClicked : Deletion

    data object UndoDismissed : Deletion

    data object ScreenTapped : GalleryAction

    data object VideoClicked : GalleryAction

    data object SystemGalleryClicked : GalleryAction

    data object EditClicked : GalleryAction

    data object EditWithClicked : GalleryAction

    data object ShareClicked : GalleryAction

    data object DialogDismissed : GalleryAction

    data class ZoomChanged(
        val pageKey: String,
        val isZoomed: Boolean,
    ) : GalleryAction

    data class PageSelected(
        val page: Int,
    ) : GalleryAction

    data class DetailsShownChanged(
        val isShown: Boolean,
    ) : Details

    data class DetailLongPressed(
        val text: String,
    ) : Details
}
