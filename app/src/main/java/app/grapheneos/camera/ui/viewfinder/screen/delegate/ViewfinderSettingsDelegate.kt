package app.grapheneos.camera.ui.viewfinder.screen.delegate

import app.grapheneos.camera.data.core.model.AspectRatio
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.VideoQuality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import app.grapheneos.camera.di.core.MainImmediateDispatcher
import app.grapheneos.camera.ui.viewfinder.screen.ViewfinderStateHolder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface ViewfinderSettingsDelegate {

    fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    )

    fun cycleGridType()
    fun setFocusTimeout(seconds: Long)
    fun setSelfTimerDuration(seconds: Int)
    fun setEnableEis(enabled: Boolean)
    fun setWaitForFocusLock(enabled: Boolean)
    fun setAspectRatio(value: AspectRatio)
    fun toggleScanAllCodes()
    fun setIncludeAudio(enabled: Boolean)

    fun selectModeSlot(slot: ModeSlot)
    fun setFlashMode(value: FlashMode)
    fun setGeoTagging(enabled: Boolean)
    fun setSelfIllumination(enabled: Boolean)
    fun setVideoQuality(quality: VideoQuality)
}

internal class ViewfinderSettingsDelegateImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewfinderSettingsDelegate {

    private lateinit var stateHolder: ViewfinderStateHolder

    private var isBound = false

    private var slot: ModeSlot? = null

    override fun bind(
        scope: CoroutineScope,
        stateHolder: ViewfinderStateHolder,
    ) {
        if (isBound) return
        isBound = true

        this.stateHolder = stateHolder

        stateHolder.update { it.copy(settings = settingsRepository.settings.value) }

        scope.launch(mainDispatcher) {
            settingsRepository.settings.collect { settings ->
                stateHolder.update { it.copy(settings = settings) }
            }
        }
    }

    override fun cycleGridType() {
        val next = when (stateHolder.state.value.settings.gridType) {
            GridType.NONE -> GridType.THREE_BY_THREE
            GridType.THREE_BY_THREE -> GridType.FOUR_BY_FOUR
            GridType.FOUR_BY_FOUR -> GridType.GOLDEN_RATIO
            GridType.GOLDEN_RATIO -> GridType.NONE
        }

        update { it.copy(gridType = next) }
    }

    override fun setFocusTimeout(seconds: Long) {
        update { it.copy(focusTimeoutSeconds = seconds) }
    }

    override fun setSelfTimerDuration(seconds: Int) {
        update { it.copy(selfTimerDurationSeconds = seconds) }
    }

    override fun setEnableEis(enabled: Boolean) {
        update { it.copy(enableEis = enabled) }
    }

    override fun setWaitForFocusLock(enabled: Boolean) {
        update { it.copy(waitForFocusLock = enabled) }
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

    override fun setGeoTagging(enabled: Boolean) {
        // A permission result is delivered before the first onResume of an activity the system
        // recreated, so this can run before a mode has been slotted — see selectModeSlot().
        val modeSettings = writeMode { slot ->
            settingsRepository.setGeoTagging(
                slot = slot,
                value = enabled,
            )
        }

        stateHolder.update {
            it.copy(
                modeSettings = modeSettings,
                requireLocation = enabled,
            )
        }
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

    private fun update(transform: (CameraSettings) -> CameraSettings) {
        val settings = settingsRepository.update(transform)

        stateHolder.update { it.copy(settings = settings) }
    }

    private fun writeMode(write: (ModeSlot) -> ModeSettings): ModeSettings {
        val current = slot ?: return stateHolder.state.value.modeSettings

        return write(current)
    }
}
