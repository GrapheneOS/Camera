package app.grapheneos.camera.ui.camerasettings.screen

import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsNavEvent as NavEvent
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsScreenEffect as Effect
import app.grapheneos.camera.ui.camerasettings.screen.model.CameraSettingsState
import app.grapheneos.camera.ui.core.screen.ScreenEvents
import app.grapheneos.camera.ui.core.screen.ScreenStateHolder
import kotlinx.coroutines.flow.Flow

internal class CameraSettingsStateHolder(
    initial: CameraSettingsState,
) : ScreenStateHolder<CameraSettingsState, Effect>(initial) {

    private val _navigationEvents = ScreenEvents<NavEvent>()
    val navigationEvents: Flow<NavEvent> = _navigationEvents.flow

    fun postNavEvent(event: NavEvent) {
        if (state.value.isSecureSession) return

        _navigationEvents.send(event)
    }

    override fun send(effect: Effect) {
        if (effect is Effect.OutsideSession && state.value.isSecureSession) return

        super.send(effect)
    }
}
