package app.grapheneos.camera.ui.camerasettings.screen.model

import app.grapheneos.camera.data.camera.model.PhotoResolutions
import app.grapheneos.camera.data.settings.model.CameraSettings

internal data class CameraSettingsState(
    val isSecureSession: Boolean = false,
    val isCaptureSession: Boolean = false,
    val isZslSupported: Boolean = false,
    val photoResolutions: PhotoResolutions? = null,
    val settings: CameraSettings = CameraSettings(),
    val storageLocation: String? = null,
    val dialog: CameraSettingsDialog? = null,
)
