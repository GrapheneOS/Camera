package app.grapheneos.camera.data.camera.model

sealed interface CameraZoomEvent {

    data object Loaded : CameraZoomEvent

    data object Changed : CameraZoomEvent
}
