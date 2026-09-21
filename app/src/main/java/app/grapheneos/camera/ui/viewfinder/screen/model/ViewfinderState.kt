package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults
import com.google.zxing.BarcodeFormat

data class ViewfinderState(
    val mode: CameraMode,
    val requiresVideoModeOnly: Boolean,
    val isCaptureSession: Boolean = false,
    val showsCameraModeTabs: Boolean = false,
    val settings: CameraSettings = CameraSettings(),
    val modeSettings: ModeSettings = ModeSettings(),
    // Settled against the location permission; never read back from the stored preference.
    val requireLocation: Boolean = false,
    val session: ViewfinderSessionState = ViewfinderSessionState(),
    val flashMode: FlashMode = SettingsDefaults.FLASH_MODE,
    val capture: ViewfinderCaptureState = ViewfinderCaptureState(),
) {

    fun isQrMode(): Boolean {
        return mode.isQr
    }

    fun isVideoMode(): Boolean {
        return mode.isVideo || requiresVideoModeOnly
    }

    fun isInPhotoMode(): Boolean {
        return !isQrMode() && !isVideoMode()
    }

    fun aspectRatio(): AspectRatio {
        return when {
            isVideoMode() -> AspectRatio.RATIO_16_9
            isQrMode() -> AspectRatio.RATIO_4_3
            else -> settings.aspectRatio
        }
    }

    fun barcodeFormats(): Set<BarcodeFormat> {
        return when {
            settings.scanAllCodes -> BarcodeFormat.entries.toSet()
            else -> BarcodeFormat.entries.filterTo(mutableSetOf()) {
                it.name in settings.enabledBarcodeFormats
            }
        }
    }

    fun selfIlluminate(): Boolean {
        return modeSettings.selfIllumination &&
            session.lensFacing == LensFacing.FRONT
    }
}
