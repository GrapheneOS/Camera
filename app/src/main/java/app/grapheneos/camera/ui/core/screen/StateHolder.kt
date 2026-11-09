package app.grapheneos.camera.ui.core.screen

import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class StateHolder<State>(
    initial: State,
) {

    private val derivations = mutableListOf<(previous: State, current: State) -> Unit>()

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * A flow of [map] applied to the state, recomputed within every [update], so it is current by
     * the time [update] returns. It only emits when its own value changes. A change of the state
     * that [isAffected] rejects leaves it as it was, without calling [map].
     */
    @MainThread
    fun <T> derive(
        isAffected: (previous: State, current: State) -> Boolean = { _, _ -> true },
        map: (State) -> T,
    ): StateFlow<T> {
        val derived = MutableStateFlow(map(_state.value))
        derivations += { previous, current ->
            if (isAffected(previous, current)) {
                derived.value = map(current)
            }
        }

        return derived.asStateFlow()
    }

    @MainThread
    fun update(transform: (State) -> State) {
        val previous = _state.value
        val current = transform(previous)
        if (current != previous) {
            _state.value = current
            derivations.forEach { derivation ->
                derivation(previous, current)
            }
        }
    }
}
