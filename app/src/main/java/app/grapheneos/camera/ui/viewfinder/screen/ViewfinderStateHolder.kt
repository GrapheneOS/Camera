package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

class ViewfinderStateHolder(
    initial: ViewfinderState,
) {

    private val derivations = mutableListOf<(ViewfinderState) -> Unit>()

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<ViewfinderState> = _state.asStateFlow()

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    /**
     * A flow of [map] applied to the state, recomputed within every [update], so it is current by
     * the time [update] returns. It only emits when its own value changes, which is what keeps a
     * frequently changing part of the state from redrawing everything else.
     */
    fun <T> derive(map: (ViewfinderState) -> T): StateFlow<T> {
        val derived = MutableStateFlow(map(_state.value))
        derivations += { state -> derived.value = map(state) }

        return derived.asStateFlow()
    }

    fun update(transform: (ViewfinderState) -> ViewfinderState) {
        _state.update(transform)

        val state = _state.value
        derivations.forEach { derivation -> derivation(state) }
    }

    fun postEffect(effect: Effect) {
        _effects.trySend(effect)
    }
}
