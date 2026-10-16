package app.grapheneos.camera.domain.gallery.model

sealed interface CapturedItemDeletion {

    data object Deleted : CapturedItemDeletion

    data object Failed : CapturedItemDeletion
}
