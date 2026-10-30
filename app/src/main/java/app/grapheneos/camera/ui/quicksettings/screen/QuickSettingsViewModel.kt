package app.grapheneos.camera.ui.quicksettings.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.grapheneos.camera.BuildConfig
import app.grapheneos.camera.data.camera.session.CameraSession
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoFrameRate
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.domain.clipboard.model.CopyTextResult
import app.grapheneos.camera.domain.clipboard.usecase.CopyText
import app.grapheneos.camera.domain.core.model.CameraEntryPoint
import app.grapheneos.camera.ui.core.screen.ScreenStateHolder
import app.grapheneos.camera.ui.quicksettings.screen.mapper.QuickSettingsUiStateMapper
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsAction as Action
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsScreenEffect as Effect
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsState
import app.grapheneos.camera.ui.quicksettings.screen.model.QuickSettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration
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
    uiStateMapper: QuickSettingsUiStateMapper,
    private val entryPoint: CameraEntryPoint,
    private val settingsRepository: SettingsRepository,
    private val session: CameraSession,
    private val copyText: CopyText,
    private val appLabel: AppLabel,
) : ViewModel(),
    QuickSettingsScreenModel {

    private val stateHolder = ScreenStateHolder<QuickSettingsState, Effect>(
        initial = QuickSettingsState(
            settings = settingsRepository.settings.value,
        ),
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
                    it.copy(boundCamera = boundCamera)
                }
            }
        }

        viewModelScope.launch { trackModeSettings() }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.CameraSettingsClicked -> openCameraSettings()
            is Action.CameraSettingsLongClicked -> copyVersion()
            is Action.FlashModeSelected -> setFlashMode(action.mode)
            is Action.VideoLightToggled -> setVideoLight(action.enabled)
            is Action.SelfIlluminationToggled -> setSelfIllumination(action.enabled)
            is Action.SelfTimerSelected -> setSelfTimerDuration(action.duration)
            is Action.AspectRatioSelected -> setAspectRatio(action.ratio)
            is Action.VideoQualitySelected -> setVideoQuality(action.quality)
            is Action.VideoFrameRateSelected -> setVideoFrameRate(action.frameRate)
            is Action.HighFrameRateLimitClicked -> showHighFrameRateLimit(true)
            is Action.DialogDismissed -> showHighFrameRateLimit(false)
            is Action.AudioToggled -> setIncludeAudio(action.enabled)
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
        stateHolder.send(
            Effect.OpenCameraSettings(
                entryPoint = entryPoint,
                capabilities = session.capabilities(),
            ),
        )
    }

    private fun copyVersion() {
        val version = "${appLabel.label()} version ${BuildConfig.VERSION_NAME}"

        if (copyText(version) == CopyTextResult.Unconfirmed) {
            stateHolder.send(Effect.TextCopied)
        }
    }

    private fun setAspectRatio(ratio: AspectRatio) {
        settingsRepository.update { it.copy(aspectRatio = ratio) }
    }

    private fun setIncludeAudio(enabled: Boolean) {
        settingsRepository.update { it.copy(includeAudio = enabled) }
    }

    private fun setSelfTimerDuration(duration: Duration) {
        settingsRepository.update { it.copy(selfTimerDuration = duration) }
    }

    private fun setFlashMode(mode: FlashMode) {
        val slot = state.boundCamera?.modeSlot() ?: return

        settingsRepository.updateMode(slot = slot) { it.copy(flashMode = mode) }
    }

    private fun setVideoLight(enabled: Boolean) {
        val flashMode = when {
            enabled -> FlashMode.ON
            else -> FlashMode.OFF
        }

        setFlashMode(flashMode)
    }

    private fun setSelfIllumination(enabled: Boolean) {
        val slot = state.boundCamera?.modeSlot() ?: return

        settingsRepository.updateMode(slot = slot) { it.copy(selfIllumination = enabled) }
    }

    private fun showHighFrameRateLimit(isShown: Boolean) {
        stateHolder.update { it.copy(isHighFrameRateLimitShown = isShown) }
    }

    private fun setVideoFrameRate(frameRate: VideoFrameRate) {
        val slot = state.boundCamera?.modeSlot() ?: return

        settingsRepository.updateMode(slot = slot) { it.copy(videoFrameRate = frameRate) }
    }

    private fun setVideoQuality(quality: VideoQuality) {
        val slot = state.boundCamera?.modeSlot() ?: return

        settingsRepository.updateMode(slot = slot) { it.copy(videoQuality = quality) }
    }
}
