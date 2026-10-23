package app.grapheneos.camera.domain.gallery.model

internal sealed interface CapturedItemRestoring {

    data object Restored : CapturedItemRestoring

    data object Failed : CapturedItemRestoring
}
