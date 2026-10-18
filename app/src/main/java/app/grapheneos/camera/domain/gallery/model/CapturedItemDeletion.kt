package app.grapheneos.camera.domain.gallery.model

internal sealed interface CapturedItemDeletion {

    data object Deleted : CapturedItemDeletion

    data object Failed : CapturedItemDeletion
}
