package app.grapheneos.camera.ui.viewfinder.screen

import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderState
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ViewfinderStateHolder(
    initial: ViewfinderState,
    private val render: (ViewfinderState) -> ViewfinderUiState,
) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<ViewfinderState> = _state.asStateFlow()

    private val _uiState = MutableStateFlow(ViewfinderUiState())
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    fun update(transform: (ViewfinderState) -> ViewfinderState) {
        _state.update(transform)
        _uiState.value = render(_state.value)
    }
}
