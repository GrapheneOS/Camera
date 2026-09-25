package app.grapheneos.camera.testutil

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState

internal fun viewfinderStateHolder(mode: CameraMode): ViewfinderStateHolder {
    return ViewfinderStateHolder(
        initial = ViewfinderState(mode = mode, requiresVideoModeOnly = false),
        render = { ViewfinderUiState() },
    )
}
