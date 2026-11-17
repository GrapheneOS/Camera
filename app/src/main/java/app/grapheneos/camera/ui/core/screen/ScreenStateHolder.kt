package app.grapheneos.camera.ui.core.screen

import kotlinx.coroutines.flow.Flow

internal open class ScreenStateHolder<State, Effect>(
    initial: State,
) : StateHolder<State>(initial = initial) {

    private val _effects = ScreenEvents<Effect>()
    val effects: Flow<Effect> = _effects.flow

    open fun send(effect: Effect) {
        _effects.send(effect)
    }
}
