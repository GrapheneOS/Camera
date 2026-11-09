package app.grapheneos.camera.ui.previewoverlays.screen.model.state

import app.grapheneos.camera.data.core.model.DeviceOrientation

internal data class LevelState(
    val visible: Boolean = false,
    val pose: LevelPose? = null,
    val tiltDegrees: Float = 0f,
    val horizonDegrees: Float = 0f,
    val orientation: DeviceOrientation? = null,
)
