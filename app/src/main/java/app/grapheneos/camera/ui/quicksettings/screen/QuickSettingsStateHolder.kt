package app.grapheneos.camera.ui.quicksettings.screen

import app.grapheneos.camera.ui.core.screen.ScreenEvents
import app.grapheneos.camera.ui.core.screen.ScreenStateHolder
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsNavEvent as NavEvent
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsScreenEffect as Effect
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import kotlinx.coroutines.flow.Flow

internal class QuickSettingsStateHolder(
    initial: QuickSettingsState,
) : ScreenStateHolder<QuickSettingsState, Effect>(initial = initial) {

    private val _navigationEvents = ScreenEvents<NavEvent>()
    val navigationEvents: Flow<NavEvent> = _navigationEvents.flow

    fun postNavEvent(event: NavEvent) {
        _navigationEvents.send(event)
    }
}
