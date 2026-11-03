package app.grapheneos.camera.ui.viewfinder.screen.model.state

import app.grapheneos.camera.data.core.model.CameraMode

data class ViewfinderModeStripState(
    val pendingMode: CameraMode? = null,
    val selectionId: Int = 0,
    val isDragged: Boolean = false,
    val isSwitchPending: Boolean = false,
) {

    fun movedTo(pendingMode: CameraMode?): ViewfinderModeStripState {
        return copy(
            pendingMode = pendingMode,
            selectionId = selectionId + 1,
            isDragged = false,
        )
    }
}
