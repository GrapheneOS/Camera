package app.grapheneos.camera.ui.previewoverlays.screen.model.state

import app.grapheneos.camera.data.camera.model.CameraZoom
import app.grapheneos.camera.ui.previewoverlays.screen.model.input.ZoomDirection

internal data class ZoomState(
    val value: CameraZoom? = null,
    val isExpanded: Boolean = false,
    val isControlPressed: Boolean = false,
    val isPreviewPinching: Boolean = false,
    val interactionId: Long = 0,
    val pendingPreset: Preset? = null,
    val keyStepDirection: ZoomDirection? = null,
) {

    fun isAdjustable(): Boolean {
        return value?.isAdjustable() == true
    }

    fun idle(): ZoomState {
        return ZoomState(
            value = value,
            interactionId = interactionId,
        )
    }

    data class Preset(
        val ratio: Float,
        val requestId: Long,
    )
}
