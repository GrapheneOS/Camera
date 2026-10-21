package app.grapheneos.camera.ui.gallery.screen.model

internal sealed interface GalleryDialog {

    data object DeleteConfirmation : GalleryDialog
}
