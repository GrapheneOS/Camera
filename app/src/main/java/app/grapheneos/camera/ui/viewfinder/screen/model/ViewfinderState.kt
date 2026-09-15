package app.grapheneos.camera.ui.viewfinder.screen.model

import app.grapheneos.camera.data.core.model.CameraMode
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.SettingsDefaults

data class ViewfinderState(
    val mode: CameraMode,
    val settings: CameraSettings = CameraSettings(),
    val modeSettings: ModeSettings = ModeSettings(),
    val requireLocation: Boolean = false,
    val session: ViewfinderSessionState = ViewfinderSessionState(),
    val flashMode: Int = SettingsDefaults.FLASH_MODE,
)
