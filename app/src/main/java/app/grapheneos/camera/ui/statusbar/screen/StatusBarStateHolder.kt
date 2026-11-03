package app.grapheneos.camera.ui.statusbar.screen

import app.grapheneos.camera.ui.core.screen.ScreenEvents
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarNavEvent as NavEvent
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarState
import kotlinx.coroutines.flow.Flow

internal class StatusBarStateHolder(
    initial: StatusBarState,
) : StateHolder<StatusBarState>(initial = initial) {

    private val _navigationEvents = ScreenEvents<NavEvent>()
    val navigationEvents: Flow<NavEvent> = _navigationEvents.flow

    fun postNavEvent(event: NavEvent) {
        _navigationEvents.send(event)
    }
}
