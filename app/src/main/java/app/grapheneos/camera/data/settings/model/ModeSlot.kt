package app.grapheneos.camera.data.settings.model

import app.grapheneos.camera.data.core.model.CameraMode

data class ModeSlot(
    val mode: CameraMode,
    val isFrontFacing: Boolean,
)
