package app.grapheneos.camera.ui.viewfinder.screen.delegate

import androidx.camera.video.Quality
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.GridType
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.model.ModeSlot
import app.grapheneos.camera.data.settings.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

interface ViewfinderSettingsDelegate {
    val settings: CameraSettings
    val modeSettings: ModeSettings
    val requireLocation: Boolean

    fun cycleGridType()
    fun setFocusTimeout(seconds: Long)
    fun setSelfTimerDuration(seconds: Int)
    fun setEnableEis(enabled: Boolean)
    fun setWaitForFocusLock(enabled: Boolean)
    fun setAspectRatio(value: Int)
    fun toggleScanAllCodes()
    fun setIncludeAudio(enabled: Boolean)

    fun selectModeSlot(slot: ModeSlot)
    fun setFlashMode(value: Int)
    fun setGeoTagging(enabled: Boolean)
    fun setSelfIllumination(enabled: Boolean)
    fun setVideoQuality(quality: Quality)
}

internal class ViewfinderSettingsDelegateImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewfinderSettingsDelegate {

    override val settings: CameraSettings
        get() {
            return settingsRepository.settings.value
        }

    override var modeSettings: ModeSettings = ModeSettings()
        private set

    // Session state rather than the stored value: geo-tagging is only ever on once the permission
    // is actually granted, and reloadSettings() is what settles a stored "on" against that. Reading
    // the preference back here would resurrect the very stale "on" the coercion exists to drop.
    override var requireLocation: Boolean = false
        private set

    private var slot: ModeSlot? = null

    override fun cycleGridType() {
        val next = when (settings.gridType) {
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

    override fun setAspectRatio(value: Int) {
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

        modeSettings = runBlocking { settingsRepository.modeSettings(slot) }
    }

    override fun setFlashMode(value: Int) {
        writeMode { slot ->
            settingsRepository.setFlashMode(
                slot = slot,
                value = value,
            )
        }
    }

    override fun setGeoTagging(enabled: Boolean) {
        // A permission result is delivered before the first onResume of an activity the system
        // recreated, so this can run before a mode has been slotted — see modeSettings.
        writeMode { slot ->
            settingsRepository.setGeoTagging(
                slot = slot,
                value = enabled,
            )
        }

        requireLocation = enabled
    }

    override fun setSelfIllumination(enabled: Boolean) {
        writeMode { slot ->
            settingsRepository.setSelfIllumination(
                slot = slot,
                value = enabled,
            )
        }
    }

    override fun setVideoQuality(quality: Quality) {
        writeMode { slot ->
            settingsRepository.setVideoQuality(
                slot = slot,
                value = quality,
            )
        }
    }

    private fun update(transform: (CameraSettings) -> CameraSettings) {
        runBlocking { settingsRepository.update(transform) }
    }

    private fun writeMode(write: suspend (ModeSlot) -> ModeSettings) {
        val current = slot ?: return

        modeSettings = runBlocking { write(current) }
    }
}
