package app.grapheneos.camera.ui.core.screen

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal open class ScreenStateHolder<State, Effect>(
    initial: State,
) : StateHolder<State>(initial = initial) {

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    open fun send(effect: Effect) {
        _effects.trySend(effect)
    }
}
