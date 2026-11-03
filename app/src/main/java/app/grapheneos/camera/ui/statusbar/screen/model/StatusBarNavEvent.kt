package app.grapheneos.camera.ui.statusbar.screen.model

import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.domain.core.model.CameraEntryPoint

internal sealed interface StatusBarNavEvent {

    data class OpenCameraSettings(
        val entryPoint: CameraEntryPoint,
        val capabilities: CameraCapabilities,
    ) : StatusBarNavEvent
}
