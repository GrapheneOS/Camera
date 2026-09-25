package app.grapheneos.camera.ui.viewfinder.screen.model

sealed interface ViewfinderCameraEvent {

    data class ProviderReady(
        val forced: Boolean,
    ) : ViewfinderCameraEvent
}
