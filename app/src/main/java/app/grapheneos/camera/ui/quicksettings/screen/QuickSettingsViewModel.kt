package app.grapheneos.camera.ui.quicksettings.screen

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.camera.session.TorchControl
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.core.screen.ScreenStateHolder
import app.grapheneos.camera.ui.quicksettings.screen.mapper.QuickSettingsUiStateMapper
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsScreenEffect as Effect
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

internal interface QuickSettingsScreenModel {

    val uiState: StateFlow<QuickSettingsUiState>

    val effects: Flow<Effect>

    fun onAction(action: Action)
}

@HiltViewModel
internal class QuickSettingsViewModel @Inject constructor(
    private val entryPoint: CameraEntryPoint,
    private val settingsRepository: SettingsRepository,
    private val session: CameraSession,
    private val torch: TorchControl,
    uiStateMapper: QuickSettingsUiStateMapper,
) : ViewModel(),
    QuickSettingsScreenModel {

    private val stateHolder = ScreenStateHolder<QuickSettingsState, Effect>(
        initial = QuickSettingsState(settings = settingsRepository.settings.value),
    )

    override val uiState: StateFlow<QuickSettingsUiState> = stateHolder.derive(uiStateMapper::map)

    override val effects: Flow<Effect> = stateHolder.effects

    private val state: QuickSettingsState
        get() = stateHolder.state.value

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
                    it.copy(
                        boundCamera = boundCamera,
                        isTorchOn = torch.isOn,
                    )
                }
            }
        }

        viewModelScope.launch { trackModeSettings() }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.FlashToggleClicked -> toggleFlashMode()
            is Action.TorchToggleClicked -> toggleTorch()
            is Action.AspectRatioToggleClicked -> toggleAspectRatio()
            is Action.CameraSettingsClicked -> openCameraSettings()
            is Action.AudioToggled -> setIncludeAudio(action.enabled)
            is Action.SelfTimerSelected -> setSelfTimerDuration(action.seconds)
            is Action.SelfIlluminationToggled -> setSelfIllumination(action.enabled)
            is Action.VideoQualitySelected -> setVideoQuality(action.quality)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun trackModeSettings() {
        stateHolder.state
            .mapNotNull { state -> state.modeSlot() }
            .distinctUntilChanged()
            .flatMapLatest(settingsRepository::observeModeSettings)
            .collect { modeSettings ->
                stateHolder.update { it.copy(modeSettings = modeSettings) }
            }
    }

    private fun toggleTorch() {
        torch.enable(!torch.isOn)

        stateHolder.update { it.copy(isTorchOn = torch.isOn) }
    }

    private fun openCameraSettings() {
        stateHolder.send(Effect.OpenCameraSettings(session.capabilities()))
    }

    private fun toggleAspectRatio() {
        settingsRepository.update { settings ->
            val next = when (settings.aspectRatio) {
                AspectRatio.RATIO_16_9 -> AspectRatio.RATIO_4_3
                AspectRatio.RATIO_4_3 -> AspectRatio.RATIO_16_9
            }

            settings.copy(aspectRatio = next)
        }
    }

    private fun setIncludeAudio(enabled: Boolean) {
        settingsRepository.update { it.copy(includeAudio = enabled) }
    }

    private fun setSelfTimerDuration(seconds: Int) {
        settingsRepository.update { it.copy(selfTimerDurationSeconds = seconds) }
    }

    private fun toggleFlashMode() {
        when {
            entryPoint.requiresVideoModeOnly -> showMessage(R.string.flash_switch_unsupported)
            state.boundCamera?.hasFlashUnit == true -> cycleFlashMode()
            else -> showMessage(R.string.flash_unavailable_in_selected_mode)
        }
    }

    private fun cycleFlashMode() {
        val slot = state.modeSlot() ?: return

        settingsRepository.updateMode(slot = slot) { settings ->
            val next = when (settings.flashMode) {
                FlashMode.OFF -> FlashMode.ON
                FlashMode.ON -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.OFF
            }

            settings.copy(flashMode = next)
        }
    }

    private fun setSelfIllumination(enabled: Boolean) {
        val slot = state.modeSlot() ?: return

        settingsRepository.updateMode(slot = slot) { it.copy(selfIllumination = enabled) }
    }

    private fun setVideoQuality(quality: VideoQuality) {
        val slot = state.modeSlot() ?: return

        settingsRepository.updateMode(slot = slot) { it.copy(videoQuality = quality) }
    }

    private fun showMessage(@StringRes message: Int) {
        stateHolder.send(Effect.ShowMessage(message))
    }
}
