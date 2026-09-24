package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

class ViewfinderStateHolder(
    initial: ViewfinderState,
    private val render: (ViewfinderState) -> ViewfinderUiState,
) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<ViewfinderState> = _state.asStateFlow()

    private val _uiState = MutableStateFlow(ViewfinderUiState())
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    fun update(transform: (ViewfinderState) -> ViewfinderState) {
        _state.update(transform)
        _uiState.value = render(_state.value)
    }

    fun postEffect(effect: Effect) {
        _effects.trySend(effect)
    }
}
