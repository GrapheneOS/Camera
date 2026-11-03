package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapper
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal interface PreviewOverlaysScreenModel {

    val uiState: StateFlow<PreviewOverlaysUiState>

    fun onAction(action: Action)
}

@HiltViewModel
internal class PreviewOverlaysViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    uiStateMapper: PreviewOverlaysUiStateMapper,
) : ViewModel(),
    PreviewOverlaysScreenModel {

    private val stateHolder = StateHolder(
        initial = PreviewOverlaysState(
            gridType = settingsRepository.settings.value.gridType,
        ),
    )

    override val uiState: StateFlow<PreviewOverlaysUiState> = stateHolder.derive {
        uiStateMapper.map(it)
    }

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { settings -> settings.gridType }
                .distinctUntilChanged()
                .collect { gridType ->
                    stateHolder.update { state ->
                        state.copy(gridType = gridType)
                    }
                }
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.PreviewContentChanged -> {
                stateHolder.update { state -> state.copy(previewContent = action.content) }
            }

            is Action.PreviewFlashVisibilityChanged -> {
                stateHolder.update { state -> state.copy(isPreviewFlashVisible = action.visible) }
            }
        }
    }
}
