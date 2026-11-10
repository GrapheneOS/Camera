package app.grapheneos.camera.ui.previewoverlays.screen.model.state

import app.grapheneos.camera.data.camera.model.CameraExposure

internal data class ExposureState(
    val value: CameraExposure? = null,
    val isPressed: Boolean = false,
    val isAdjusted: Boolean = false,
    val interactionId: Long = 0,
) {

    fun idle(): ExposureState {
        return copy(isPressed = false)
    }

    fun reset(): ExposureState {
        return ExposureState(interactionId = interactionId)
    }
}
