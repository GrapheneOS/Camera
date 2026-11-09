package app.grapheneos.camera.data.camera.model

sealed interface CameraFocusEvent {

    data class Started(
        val requestId: Long,
    ) : CameraFocusEvent

    data class Focused(
        val requestId: Long,
    ) : CameraFocusEvent

    data class Locked(
        val requestId: Long,
    ) : CameraFocusEvent

    data class Cancelled(
        val requestId: Long,
    ) : CameraFocusEvent
}
