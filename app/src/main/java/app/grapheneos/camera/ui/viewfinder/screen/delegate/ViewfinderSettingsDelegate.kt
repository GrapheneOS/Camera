package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent as NavEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

interface ViewfinderSettingsDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    suspend fun trackSettings()
    suspend fun trackModeSettings()
    suspend fun trackCaptureStarts()

    fun setSelfTimerDuration(seconds: Int)
    fun setAspectRatio(value: AspectRatio)
    fun toggleScanAllCodes()
    fun setIncludeAudio(enabled: Boolean)

    fun selectModeSlot(slot: ModeSlot)
    fun setFlashMode(value: FlashMode)
    fun setSelfIllumination(enabled: Boolean)
    fun setVideoQuality(quality: VideoQuality)

    fun openSettingsPanel()
    fun onSettingsPanelVisibilityChanged(visible: Boolean)

    fun openCameraSettings(capabilities: CameraCapabilities)
}

internal class ViewfinderSettingsDelegateImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewfinderSettingsDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private val currentSlot = MutableStateFlow<ModeSlot?>(null)

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder

        stateHolder.update { it.copy(settings = settingsRepository.settings.value) }
    }

    override suspend fun trackSettings() {
        settingsRepository.settings.collect { settings ->
            stateHolder.update { it.copy(settings = settings) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun trackModeSettings() {
        currentSlot
            .filterNotNull()
            .flatMapLatest(settingsRepository::observeModeSettings)
            .collect { modeSettings ->
                stateHolder.update { it.copy(modeSettings = modeSettings) }
            }
    }

    override suspend fun trackCaptureStarts() {
        stateHolder.state
            .map { state ->
                state.recording.isActive() || state.capture.isSelfTimerRunning
            }
            .distinctUntilChanged()
            .collect { isCapturing ->
                if (isCapturing && stateHolder.state.value.isSettingsPanelOpen) {
                    stateHolder.postNavEvent(NavEvent.CloseSettingsPanel)
                }
            }
    }

    override fun setSelfTimerDuration(seconds: Int) {
        update { it.copy(selfTimerDurationSeconds = seconds) }
    }

    override fun setAspectRatio(value: AspectRatio) {
        update { it.copy(aspectRatio = value) }
    }

    override fun toggleScanAllCodes() {
        update { it.copy(scanAllCodes = !it.scanAllCodes) }
    }

    override fun setIncludeAudio(enabled: Boolean) {
        update { it.copy(includeAudio = enabled) }
    }

    override fun selectModeSlot(slot: ModeSlot) {
        currentSlot.value = slot

        val modeSettings = settingsRepository.modeSettings(slot)

        stateHolder.update { it.copy(modeSettings = modeSettings) }
    }

    override fun setFlashMode(value: FlashMode) {
        val modeSettings = writeMode { slot ->
            settingsRepository.setFlashMode(
                slot = slot,
                value = value,
            )
        }

        stateHolder.update { it.copy(modeSettings = modeSettings) }
    }

    override fun setSelfIllumination(enabled: Boolean) {
        val modeSettings = writeMode { slot ->
            settingsRepository.setSelfIllumination(
                slot = slot,
                value = enabled,
            )
        }

        stateHolder.update { it.copy(modeSettings = modeSettings) }
    }

    override fun setVideoQuality(quality: VideoQuality) {
        val modeSettings = writeMode { slot ->
            settingsRepository.setVideoQuality(
                slot = slot,
                value = quality,
            )
        }

        stateHolder.update { it.copy(modeSettings = modeSettings) }
    }

    override fun openSettingsPanel() {
        val state = stateHolder.state.value
        if (state.isQrMode() || state.recording.isActive()) return

        stateHolder.postNavEvent(NavEvent.OpenSettingsPanel)
    }

    override fun onSettingsPanelVisibilityChanged(visible: Boolean) {
        stateHolder.update { it.copy(isSettingsPanelOpen = visible) }
    }

    override fun openCameraSettings(capabilities: CameraCapabilities) {
        val state = stateHolder.state.value
        val effect = when {
            state.recording.isActive() -> {
                Effect.ShowMessage(R.string.camera_settings_unavailable_during_recording)
            }

            else -> Effect.Settings.OpenCameraSettings(capabilities)
        }

        stateHolder.postEffect(effect)
    }

    private fun update(transform: (CameraSettings) -> CameraSettings) {
        val settings = settingsRepository.update(transform)

        stateHolder.update { it.copy(settings = settings) }
    }

    private fun writeMode(write: (ModeSlot) -> ModeSettings): ModeSettings {
        val current = currentSlot.value ?: return stateHolder.state.value.modeSettings

        return write(current)
    }
}
