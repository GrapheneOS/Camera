package app.grapheneos.camera.data.settings.repository

import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.model.FlashMode
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.store.SettingsPrefs
import app.grapheneos.camera.data.settings.store.StoredFlashMode
import app.grapheneos.camera.data.settings.store.StoredModeSettings
import app.grapheneos.camera.di.core.ApplicationScope
import app.grapheneos.camera.di.core.DefaultDispatcher
import app.grapheneos.camera.di.core.DurablePreferences
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * One instance per storage, shared by every screen reading it, and the only writer of that storage:
 * [settings] is settled before [update] returns, so a write made on one screen is readable on
 * another without waiting for a dispatch, while the storage itself catches up in the background.
 * A screen holding its own copy would never see the write.
 */
interface SettingsRepository {
    val settings: StateFlow<CameraSettings>

    fun update(transform: (CameraSettings) -> CameraSettings): CameraSettings

    fun modeSettings(slot: ModeSlot): ModeSettings
    fun observeModeSettings(slot: ModeSlot): Flow<ModeSettings>

    /**
     * Stores only the fields [transform] changes, so the others keep following their defaults. A
     * light that shines while the camera is open is kept for the slot until the process ends, and
     * never saved.
     */
    fun updateMode(
        slot: ModeSlot,
        transform: (ModeSettings) -> ModeSettings,
    ): ModeSettings

    fun sessionCopy(): SettingsRepository
}

internal class SettingsRepositoryImpl @Inject constructor(
    @DurablePreferences private val dataStore: DataStore<SettingsPrefs>,
    private val cameraSettingsMapper: CameraSettingsMapper,
    private val modeSettingsMapper: ModeSettingsMapper,
    private val storedVideoQualityMapper: StoredVideoQualityMapper,
    @ApplicationScope private val writeScope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SettingsRepository {

    private val contents = MutableStateFlow(
        Contents(prefs = runBlocking { dataStore.data.first() }),
    )

    private val _settings = MutableStateFlow(cameraSettingsMapper.map(contents.value.prefs.common))
    override val settings: StateFlow<CameraSettings> = _settings.asStateFlow()

    override fun update(transform: (CameraSettings) -> CameraSettings): CameraSettings {
        val updated = write { contents ->
            val settings = transform(cameraSettingsMapper.map(contents.prefs.common))
            contents.copy(
                prefs = contents.prefs.copy(common = cameraSettingsMapper.map(settings)),
            )
        }

        return cameraSettingsMapper.map(updated.prefs.common)
    }

    override fun modeSettings(slot: ModeSlot): ModeSettings {
        return modeSettingsOf(contents = contents.value, slot = slot)
    }

    override fun observeModeSettings(slot: ModeSlot): Flow<ModeSettings> {
        return contents
            .map { contents -> modeSettingsOf(contents = contents, slot = slot) }
            .distinctUntilChanged()
    }

    override fun updateMode(
        slot: ModeSlot,
        transform: (ModeSettings) -> ModeSettings,
    ): ModeSettings {
        val current = modeSettings(slot)
        val updated = transform(current)
        if (updated == current) return current

        val persistableFlashMode = modeSettingsMapper.map(
            flashMode = updated.flashMode,
            mode = slot.mode,
        )

        write { contents ->
            contents.copy(
                prefs = contents.prefs.withMode(
                    mode = slot.mode,
                    settings = storedChanges(
                        stored = contents.prefs.mode(slot.mode),
                        current = current,
                        updated = updated,
                        persistableFlashMode = persistableFlashMode,
                        isFrontFacing = slot.isFrontFacing,
                    ),
                ),
                unsavedFlashModes = unsavedChanges(
                    unsaved = contents.unsavedFlashModes,
                    slot = slot,
                    current = current,
                    updated = updated,
                    persistableFlashMode = persistableFlashMode,
                ),
            )
        }

        return updated
    }

    override fun sessionCopy(): SettingsRepository {
        return SettingsRepositoryImpl(
            dataStore = InMemoryDataStore(contents.value.prefs),
            cameraSettingsMapper = cameraSettingsMapper,
            modeSettingsMapper = modeSettingsMapper,
            storedVideoQualityMapper = storedVideoQualityMapper,
            writeScope = writeScope,
            defaultDispatcher = defaultDispatcher,
        )
    }

    private fun storedChanges(
        stored: StoredModeSettings,
        current: ModeSettings,
        updated: ModeSettings,
        persistableFlashMode: StoredFlashMode?,
        isFrontFacing: Boolean,
    ): StoredModeSettings {
        val framesPerSecond = updated.videoFrameRate.framesPerSecond
        val withFlash = when {
            updated.flashMode == current.flashMode || persistableFlashMode == null -> stored
            isFrontFacing -> stored.copy(flashModeFront = persistableFlashMode)
            else -> stored.copy(flashModeBack = persistableFlashMode)
        }
        val withQuality = when {
            updated.videoQuality == current.videoQuality -> withFlash

            isFrontFacing -> withFlash.copy(
                videoQualityFront = storedVideoQualityMapper.map(updated.videoQuality),
            )

            else -> withFlash.copy(
                videoQualityBack = storedVideoQualityMapper.map(updated.videoQuality),
            )
        }

        return when {
            updated.videoFrameRate == current.videoFrameRate -> withQuality
            isFrontFacing -> withQuality.copy(frameRateFront = framesPerSecond)
            else -> withQuality.copy(frameRateBack = framesPerSecond)
        }
    }

    private fun unsavedChanges(
        unsaved: Map<ModeSlot, FlashMode>,
        slot: ModeSlot,
        current: ModeSettings,
        updated: ModeSettings,
        persistableFlashMode: StoredFlashMode?,
    ): Map<ModeSlot, FlashMode> {
        return when {
            updated.flashMode == current.flashMode -> unsaved
            persistableFlashMode == null -> unsaved + (slot to updated.flashMode)
            else -> unsaved - slot
        }
    }

    private fun modeSettingsOf(
        contents: Contents,
        slot: ModeSlot,
    ): ModeSettings {
        val saved = modeSettingsMapper.map(
            stored = contents.prefs.mode(slot.mode),
            slot = slot,
        )

        return when (val unsavedFlashMode = contents.unsavedFlashModes[slot]) {
            null -> saved
            else -> saved.copy(flashMode = unsavedFlashMode)
        }
    }

    private fun write(transform: (Contents) -> Contents): Contents {
        val updated = contents.updateAndGet(transform)

        _settings.value = cameraSettingsMapper.map(updated.prefs.common)

        writeScope.launch(defaultDispatcher) {
            dataStore.updateData { contents.value.prefs }
        }

        return updated
    }

    private data class Contents(
        val prefs: SettingsPrefs,
        val unsavedFlashModes: Map<ModeSlot, FlashMode> = emptyMap(),
    )
}
