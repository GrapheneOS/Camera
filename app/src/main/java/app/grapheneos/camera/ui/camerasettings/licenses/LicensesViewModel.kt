package app.grapheneos.camera.ui.camerasettings.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.licenses.repository.LicensesRepository
import app.grapheneos.camera.ui.camerasettings.licenses.mapper.LicensesUiStateMapper
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesAction as Action
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesScreenEffect as Effect
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesState
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal interface LicensesScreenModel {

    val uiState: StateFlow<LicensesUiState>

    val effects: Flow<Effect>

    fun onAction(action: Action)
}

@HiltViewModel
internal class LicensesViewModel @Inject constructor(
    licensesRepository: LicensesRepository,
    private val uiStateMapper: LicensesUiStateMapper,
) : ViewModel(),
    LicensesScreenModel {

    private var state = LicensesState()

    private val _uiState = MutableStateFlow(uiStateMapper.map(state))
    override val uiState: StateFlow<LicensesUiState> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(capacity = Channel.BUFFERED)
    override val effects: Flow<Effect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val licenses = licensesRepository.licenses()

            update { it.copy(licenses = licenses) }
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.LibraryClicked -> update { it.copy(openCoordinates = action.coordinates) }
            is Action.LicenseClosed -> update { it.copy(openCoordinates = null) }
            is Action.UrlClicked -> _effects.trySend(Effect.OpenUrl(action.url))
        }
    }

    private fun update(transform: (LicensesState) -> LicensesState) {
        state = transform(state)
        _uiState.value = uiStateMapper.map(state)
    }
}
