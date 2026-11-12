package app.grapheneos.camera.ui.quicksettings.screen.model

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings

internal data class QuickSettingsState(
    val settings: CameraSettings = CameraSettings(),
    val modeSettings: ModeSettings = ModeSettings(),
    val boundCamera: BoundCamera? = null,
    val isRecording: Boolean = false,
    val shownLimit: OptionLimit? = null,
)
