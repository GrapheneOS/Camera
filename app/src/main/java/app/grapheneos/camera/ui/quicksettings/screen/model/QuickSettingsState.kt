package app.grapheneos.camera.ui.quicksettings.screen.model

import app.grapheneos.camera.data.camera.model.BoundCamera
import app.grapheneos.camera.data.camera.model.LensFacing
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot

data class QuickSettingsState(
    val settings: CameraSettings = CameraSettings(),
    val modeSettings: ModeSettings = ModeSettings(),
    val boundCamera: BoundCamera? = null,
    val isTorchOn: Boolean = false,
) {

    fun modeSlot(): ModeSlot? {
        return boundCamera?.let { camera ->
            ModeSlot(
                mode = camera.mode,
                isFrontFacing = camera.lensFacing == LensFacing.FRONT,
            )
        }
    }
}
