package app.grapheneos.camera.ui.statusbar.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.VideoRecordingSession
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.core.screen.ScreenStateHolder
import app.grapheneos.camera.ui.statusbar.screen.mapper.StatusBarUiStateMapper
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarAction as Action
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarScreenEffect as Effect
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarState
import app.grapheneos.camera.ui.statusbar.screen.model.StatusBarUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

internal interface StatusBarScreenModel {

    val uiState: StateFlow<StatusBarUiState>

    val effects: Flow<Effect>

    fun onAction(action: Action)
}

@HiltViewModel
internal class StatusBarViewModel @Inject constructor(
    uiStateMapper: StatusBarUiStateMapper,
    private val entryPoint: CameraEntryPoint,
    private val settingsRepository: SettingsRepository,
    private val session: CameraSession,
    private val recordingSession: VideoRecordingSession,
) : ViewModel(),
    StatusBarScreenModel {

    private val stateHolder = ScreenStateHolder<StatusBarState, Effect>(
        initial = StatusBarState(
            settings = settingsRepository.settings.value,
        ),
    )

    override val uiState: StateFlow<StatusBarUiState> = stateHolder.derive(uiStateMapper::map)

    override val effects: Flow<Effect> = stateHolder.effects

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                stateHolder.update {
                    it.copy(settings = settings)
                }
            }
        }

        viewModelScope.launch {
            session.boundCamera.collect { boundCamera ->
                stateHolder.update {
                    it.copy(boundCamera = boundCamera)
                }
            }
        }

        viewModelScope.launch { trackModeSettings() }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.CameraSettingsClicked -> openCameraSettings()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun trackModeSettings() {
        stateHolder.state
            .mapNotNull { state -> state.boundCamera?.modeSlot() }
            .distinctUntilChanged()
            .flatMapLatest(settingsRepository::observeModeSettings)
            .collect { modeSettings ->
                stateHolder.update { it.copy(modeSettings = modeSettings) }
            }
    }

    private fun openCameraSettings() {
        if (recordingSession.isRecording.value) return

        stateHolder.send(
            Effect.OpenCameraSettings(
                entryPoint = entryPoint,
                capabilities = session.capabilities(),
            ),
        )
    }
}
