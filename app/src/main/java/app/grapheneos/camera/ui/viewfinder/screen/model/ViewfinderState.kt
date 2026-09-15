package app.grapheneos.camera.ui.viewfinder.screen.model

import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults

data class ViewfinderState(
    val mode: CameraMode,
    val requiresVideoModeOnly: Boolean,
    val settings: CameraSettings = CameraSettings(),
    val modeSettings: ModeSettings = ModeSettings(),
    // Settled against the location permission; never read back from the stored preference.
    val requireLocation: Boolean = false,
    val session: ViewfinderSessionState = ViewfinderSessionState(),
    val flashMode: Int = SettingsDefaults.FLASH_MODE,
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

    fun aspectRatio(): Int {
        return when {
            isVideoMode() -> AspectRatio.RATIO_16_9
            isQrMode() -> AspectRatio.RATIO_4_3
            else -> settings.aspectRatio
        }
    }

    fun selfIlluminate(): Boolean {
        return modeSettings.selfIllumination &&
            session.lensFacing == CameraSelector.LENS_FACING_FRONT
    }
}
