package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.core.screen.ScreenEvents
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent as NavEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import kotlinx.coroutines.flow.Flow

class ViewfinderStateHolder(
    initial: ViewfinderState,
) : StateHolder<ViewfinderState>(initial = initial) {

    private val _effects = ScreenEvents<Effect>()
    val effects: Flow<Effect> = _effects.flow

    private val _navigationEvents = ScreenEvents<NavEvent>()
    val navigationEvents: Flow<NavEvent> = _navigationEvents.flow

    fun postEffect(effect: Effect) {
        _effects.send(effect)
    }

    fun postNavEvent(event: NavEvent) {
        _navigationEvents.send(event)
    }
}
