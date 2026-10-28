package app.grapheneos.camera.ui.viewfinder.screen.model

sealed interface ViewfinderDialog {

    data object StorageLocationNotFound : ViewfinderDialog

    data class CameraPermission(
        val offersSettings: Boolean,
    ) : ViewfinderDialog

    data class MicrophonePermission(
        val offersSettings: Boolean,
    ) : ViewfinderDialog

    data class PictureFailure(
        val cause: PictureFailureCause,
        val details: String,
        val showsDetails: Boolean = false,
    ) : ViewfinderDialog
}
