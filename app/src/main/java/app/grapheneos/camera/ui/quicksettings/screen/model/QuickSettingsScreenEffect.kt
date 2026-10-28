package app.grapheneos.camera.ui.quicksettings.screen.model

import androidx.annotation.StringRes
import app.grapheneos.camera.data.camera.model.CameraCapabilities

sealed interface QuickSettingsScreenEffect {

    data class ShowMessage(
        @StringRes val message: Int,
    ) : QuickSettingsScreenEffect

    data class OpenCameraSettings(
        val capabilities: CameraCapabilities,
    ) : QuickSettingsScreenEffect
}
