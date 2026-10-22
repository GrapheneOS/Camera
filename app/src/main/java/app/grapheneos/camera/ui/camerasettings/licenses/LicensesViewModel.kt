package app.grapheneos.camera.ui.camerasettings.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.licenses.repository.LicensesRepository
import app.grapheneos.camera.ui.camerasettings.licenses.mapper.LicensesUiStateMapper
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesAction as Action
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesScreenEffect as Effect
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesState
import app.grapheneos.camera.ui.camerasettings.licenses.model.LicensesUiState
import app.grapheneos.camera.ui.core.screen.ScreenStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal interface LicensesScreenModel {

    val uiState: StateFlow<LicensesUiState>

    val effects: Flow<Effect>

    fun onAction(action: Action)
}

@HiltViewModel
internal class LicensesViewModel @Inject constructor(
    licensesRepository: LicensesRepository,
    uiStateMapper: LicensesUiStateMapper,
) : ViewModel(),
    LicensesScreenModel {

    private val stateHolder = ScreenStateHolder<LicensesState, Effect>(
        LicensesState(),
    )

    override val uiState: StateFlow<LicensesUiState> = stateHolder.derive(uiStateMapper::map)

    override val effects: Flow<Effect> = stateHolder.effects

    init {
        viewModelScope.launch {
            val licenses = licensesRepository.licenses()

            stateHolder.update { it.copy(licenses = licenses) }
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.LibraryClicked -> stateHolder.update {
                it.copy(openCoordinates = action.coordinates)
            }

            is Action.LicenseClosed -> stateHolder.update {
                it.copy(openCoordinates = null)
            }

            is Action.UrlClicked -> stateHolder.send(Effect.OpenUrl(action.url))
        }
    }
}
