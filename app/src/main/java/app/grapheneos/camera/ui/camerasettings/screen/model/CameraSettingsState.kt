package app.grapheneos.camera.ui.camerasettings.screen.model

import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.settings.model.CameraSettings

internal data class CameraSettingsState(
    val isSecureSession: Boolean = false,
    val isCaptureSession: Boolean = false,
    val capabilities: CameraCapabilities = CameraCapabilities(),
    val settings: CameraSettings = CameraSettings(),
    val storageLocation: String? = null,
    val dialog: CameraSettingsDialog? = null,
)
