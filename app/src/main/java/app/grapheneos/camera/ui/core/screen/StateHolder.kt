package app.grapheneos.camera.ui.core.screen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

open class StateHolder<State>(
    initial: State,
) {

    private val derivations = mutableListOf<(State) -> Unit>()

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * A flow of [map] applied to the state, recomputed within every [update], so it is current by
     * the time [update] returns. It only emits when its own value changes.
     */
    fun <T> derive(map: (State) -> T): StateFlow<T> {
        val derived = MutableStateFlow(map(_state.value))
        derivations += { state -> derived.value = map(state) }

        return derived.asStateFlow()
    }

    fun update(transform: (State) -> State) {
        _state.update(transform)

        val state = _state.value
        derivations.forEach { derivation -> derivation(state) }
    }
}
