package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.R
import app.grapheneos.camera.data.camera.model.CameraCapabilities
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderNavEvent as NavEvent
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

interface ViewfinderSettingsDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    suspend fun trackSettings()
    suspend fun trackModeSettings()
    suspend fun trackSettingsPanelAvailability()

    /** Emits after each write of a setting the camera binds with, once the state holds it. */
    fun rebindRequests(): Flow<Unit>

    /** Emits whether the video light is enabled after each write of it to the current mode. */
    fun videoLightWrites(): Flow<Boolean>

    fun toggleScanAllCodes()
    fun setIncludeAudio(enabled: Boolean)
    fun selectModeSlot(slot: ModeSlot)

    fun openSettingsPanel()
    fun closeSettingsPanel()
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

    override suspend fun trackSettingsPanelAvailability() {
        stateHolder.state
            .map { state -> state.isSettingsPanelOpen && !state.isSettingsPanelAvailable() }
            .distinctUntilChanged()
            .collect { isOpenWhileUnavailable ->
                if (isOpenWhileUnavailable) {
                    stateHolder.postNavEvent(NavEvent.CloseSettingsPanel)
                }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun rebindRequests(): Flow<Unit> {
        val aspectRatioWrites = settingsRepository.settings
            .map { settings -> settings.aspectRatio }
            .distinctUntilChanged()
            .drop(1)
        val videoFormatWrites = currentSlot
            .filterNotNull()
            .flatMapLatest { slot ->
                settingsRepository.observeModeSettings(slot)
                    .map { modeSettings ->
                        modeSettings.videoQuality to modeSettings.videoFrameRate
                    }
                    .distinctUntilChanged()
                    .drop(1)
            }

        return merge(
            aspectRatioWrites,
            videoFormatWrites,
        ).map { reload() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun videoLightWrites(): Flow<Boolean> {
        return currentSlot
            .filterNotNull()
            .flatMapLatest { slot ->
                settingsRepository.observeModeSettings(slot)
                    .map { modeSettings -> modeSettings.isVideoLightOn() }
                    .distinctUntilChanged()
                    .drop(1)
            }
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

    override fun openSettingsPanel() {
        if (!stateHolder.state.value.isSettingsPanelAvailable()) return

        stateHolder.postNavEvent(NavEvent.OpenSettingsPanel)
    }

    override fun closeSettingsPanel() {
        stateHolder.postNavEvent(NavEvent.CloseSettingsPanel)
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

    private fun reload() {
        val slot = currentSlot.value

        stateHolder.update {
            it.copy(
                settings = settingsRepository.settings.value,
                modeSettings = slot?.let(settingsRepository::modeSettings) ?: it.modeSettings,
            )
        }
    }

    private fun update(transform: (CameraSettings) -> CameraSettings) {
        val settings = settingsRepository.update(transform)

        stateHolder.update { it.copy(settings = settings) }
    }
}
