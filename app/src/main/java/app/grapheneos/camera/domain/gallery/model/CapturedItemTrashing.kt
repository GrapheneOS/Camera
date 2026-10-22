package app.grapheneos.camera.domain.gallery.model

internal sealed interface CapturedItemTrashing {

    data object Trashed : CapturedItemTrashing

    data object NotTrashable : CapturedItemTrashing

    data object Failed : CapturedItemTrashing
}
