package app.grapheneos.camera.ui.quicksettings.screen.model

import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.domain.core.model.CameraEntryPoint

internal sealed interface QuickSettingsNavEvent {

    data class OpenCameraSettings(
        val entryPoint: CameraEntryPoint,
        val capabilities: CameraCapabilities,
    ) : QuickSettingsNavEvent
}
