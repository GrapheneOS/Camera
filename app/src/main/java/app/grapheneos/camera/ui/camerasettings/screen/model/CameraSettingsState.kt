package app.grapheneos.camera.ui.camerasettings.screen.model

import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults

internal data class CameraSettingsState(
    val isSecureSession: Boolean = false,
    val isCaptureSession: Boolean = false,
    val capabilities: CameraCapabilities = CameraCapabilities(),
    val isLocationPermissionGranted: Boolean = false,
    val settings: CameraSettings = CameraSettings(),
    val storageLocation: String? = null,
    val dialog: CameraSettingsDialog? = null,
) {

    // Only what this screen shows: the viewfinder's own choices are kept.
    fun settingsAfterReset(): CameraSettings {
        val removeExifAfterCapture = when {
            isCaptureSession -> settings.removeExifAfterCapture
            else -> SettingsDefaults.REMOVE_EXIF_AFTER_CAPTURE
        }

        return CameraSettings(
            aspectRatio = settings.aspectRatio,
            selfTimerDurationSeconds = settings.selfTimerDurationSeconds,
            includeAudio = settings.includeAudio,
            removeExifAfterCapture = removeExifAfterCapture,
            scanAllCodes = settings.scanAllCodes,
            enabledBarcodeFormats = settings.enabledBarcodeFormats,
        )
    }
}
