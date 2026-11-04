package app.grapheneos.camera.ui.previewoverlays.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.ui.core.screen.StateHolder
import app.grapheneos.camera.ui.previewoverlays.screen.delegate.LevelDelegate
import app.grapheneos.camera.ui.previewoverlays.screen.mapper.PreviewOverlaysUiStateMapper
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelState
import app.grapheneos.camera.ui.previewoverlays.screen.model.LevelUiState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysAction as Action
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysState
import app.grapheneos.camera.ui.previewoverlays.screen.model.PreviewOverlaysUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal interface PreviewOverlaysScreenModel {

    val uiState: StateFlow<PreviewOverlaysUiState>

    val levelUiState: StateFlow<LevelUiState>

    fun onAction(action: Action)
}

@HiltViewModel
internal class PreviewOverlaysViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    uiStateMapper: PreviewOverlaysUiStateMapper,
    private val levelDelegate: LevelDelegate,
) : ViewModel(),
    PreviewOverlaysScreenModel {

    private val stateHolder = StateHolder(
        initial = settingsRepository.settings.value.let { settings ->
            PreviewOverlaysState(
                gridType = settings.gridType,
                level = LevelState(
                    isEnabled = settings.gyroscopeSuggestions,
                ),
            )
        },
    )

    override val uiState: StateFlow<PreviewOverlaysUiState> = stateHolder.derive { state ->
        uiStateMapper.map(state)
    }

    override val levelUiState: StateFlow<LevelUiState> = stateHolder.derive { state ->
        uiStateMapper.mapLevel(state)
    }

    private var levelTracking: Job? = null

    init {
        levelDelegate.bind(stateHolder)

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                stateHolder.update { state ->
                    state.copy(
                        gridType = settings.gridType,
                        level = state.level.copy(
                            isEnabled = settings.gyroscopeSuggestions,
                        ),
                    ).resetLevelIfUnavailable()
                }
            }
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.ScreenResumed -> onScreenResumed()
            is Action.ScreenPaused -> onScreenPaused()

            is Action.DisplayRotationChanged -> {
                stateHolder.update { state -> state.copy(displayRotation = action.rotation) }
            }

            is Action.PreviewContentChanged -> {
                stateHolder.update { state ->
                    val updated = state.copy(previewContent = action.content)
                    when {
                        state.previewContent?.mode != action.content.mode -> updated.resetLevel()
                        else -> updated.resetLevelIfUnavailable()
                    }
                }
            }

            is Action.PreviewFlashVisibilityChanged -> {
                stateHolder.update { state ->
                    state.copy(isPreviewFlashVisible = action.visible).resetLevelIfUnavailable()
                }
            }
        }
    }

    private fun onScreenResumed() {
        if (levelTracking?.isActive == true) return

        levelTracking = viewModelScope.launch { levelDelegate.trackLevel() }
    }

    private fun onScreenPaused() {
        levelTracking?.cancel()
        levelTracking = null
        stateHolder.update { state ->
            state.copy(isPreviewFlashVisible = false).resetLevel()
        }
    }
}
