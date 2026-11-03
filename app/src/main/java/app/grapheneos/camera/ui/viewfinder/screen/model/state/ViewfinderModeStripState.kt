package app.grapheneos.camera.ui.viewfinder.screen.model.state

import app.grapheneos.camera.data.core.model.CameraMode

data class ViewfinderModeStripState(
    val pendingMode: CameraMode? = null,
    val selectionId: Int = 0,
)
