package app.grapheneos.camera.ui.camerasettings.screen

import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsScreenEffect as Effect
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.core.screen.ScreenStateHolder

internal class CameraSettingsStateHolder(
    initial: CameraSettingsState,
) : ScreenStateHolder<CameraSettingsState, Effect>(initial) {

    override fun send(effect: Effect) {
        if (effect is Effect.OutsideSession && state.value.isSecureSession) return

        super.send(effect)
    }
}
