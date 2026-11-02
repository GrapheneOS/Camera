package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent as NavEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.state.ViewfinderState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class ViewfinderStateHolder(
    initial: ViewfinderState,
) : StateHolder<ViewfinderState>(initial = initial) {

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    private val _navigationEvents = Channel<NavEvent>(capacity = Channel.BUFFERED)
    val navigationEvents: Flow<NavEvent> = _navigationEvents.receiveAsFlow()

    fun postEffect(effect: Effect) {
        _effects.trySend(effect)
    }

    fun postNavEvent(event: NavEvent) {
        _navigationEvents.trySend(event)
    }
}
