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
import app.grapheneos.camera.ui.viewfinder.screen.model.ViewfinderScreenEffect as Effect
import javax.inject.Inject

interface ViewfinderSettingsDelegate {

    fun bind(stateHolder: ViewfinderStateHolder)

    suspend fun trackSettings()

    fun setSelfTimerDuration(seconds: Int)
    fun setAspectRatio(value: AspectRatio)
    fun toggleScanAllCodes()
    fun setIncludeAudio(enabled: Boolean)

    fun selectModeSlot(slot: ModeSlot)
    fun setFlashMode(value: FlashMode)
    fun setSelfIllumination(enabled: Boolean)
    fun setVideoQuality(quality: VideoQuality)

    fun openCameraSettings(capabilities: CameraCapabilities)
}

internal class ViewfinderSettingsDelegateImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewfinderSettingsDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var slot: ModeSlot? = null

    override fun bind(stateHolder: ViewfinderStateHolder) {
        this.stateHolder = stateHolder

        stateHolder.update { it.copy(settings = settingsRepository.settings.value) }
    }

    override suspend fun trackSettings() {
        settingsRepository.settings.collect { settings ->
            stateHolder.update { it.copy(settings = settings) }
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
        this.slot = slot

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
        val current = slot ?: return stateHolder.state.value.modeSettings

        return write(current)
    }
}
