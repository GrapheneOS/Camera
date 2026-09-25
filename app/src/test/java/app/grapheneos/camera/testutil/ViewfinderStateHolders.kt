package app.grapheneos.camera.testutil

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState

internal fun viewfinderStateHolder(
    mode: CameraMode,
    settings: CameraSettings = CameraSettings(),
): ViewfinderStateHolder {
    return ViewfinderStateHolder(
        initial = ViewfinderState(
            mode = mode,
            requiresVideoModeOnly = false,
            settings = settings,
        ),
    )
}
