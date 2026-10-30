package app.grapheneos.camera.data.settings.repository

import androidx.datastore.core.DataStore
import app.grapheneos.camera.data.core.model.ModeSlot
import app.grapheneos.camera.data.core.store.InMemoryDataStore
import app.grapheneos.camera.data.settings.mapper.CameraSettingsMapper
import app.grapheneos.camera.data.settings.mapper.ModeSettingsMapper
import app.grapheneos.camera.data.settings.mapper.StoredVideoQualityMapper
import app.grapheneos.camera.data.settings.model.CameraSettings
import app.grapheneos.camera.data.settings.model.ModeSettings
import app.grapheneos.camera.data.settings.store.SettingsPrefs
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

    /** Stores only the fields [transform] changes, so the others keep following their defaults. */
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

    private val prefs = MutableStateFlow(runBlocking { dataStore.data.first() })

    private val _settings = MutableStateFlow(cameraSettingsMapper.map(prefs.value.common))
    override val settings: StateFlow<CameraSettings> = _settings.asStateFlow()

    override fun update(transform: (CameraSettings) -> CameraSettings): CameraSettings {
        val updated = write { prefs ->
            val settings = transform(cameraSettingsMapper.map(prefs.common))
            prefs.copy(common = cameraSettingsMapper.map(settings))
        }

        return cameraSettingsMapper.map(updated.common)
    }

    override fun modeSettings(slot: ModeSlot): ModeSettings {
        return modeSettingsOf(prefs = prefs.value, slot = slot)
    }

    override fun observeModeSettings(slot: ModeSlot): Flow<ModeSettings> {
        return prefs
            .map { prefs -> modeSettingsOf(prefs = prefs, slot = slot) }
            .distinctUntilChanged()
    }

    override fun updateMode(
        slot: ModeSlot,
        transform: (ModeSettings) -> ModeSettings,
    ): ModeSettings {
        val current = modeSettings(slot)
        val updated = transform(current)
        if (updated == current) return current

        write { prefs ->
            prefs.withMode(
                mode = slot.mode,
                settings = storedChanges(
                    stored = prefs.mode(slot.mode),
                    current = current,
                    updated = updated,
                    isFrontFacing = slot.isFrontFacing,
                ),
            )
        }

        return updated
    }

    override fun sessionCopy(): SettingsRepository {
        return SettingsRepositoryImpl(
            dataStore = InMemoryDataStore(prefs.value),
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
        isFrontFacing: Boolean,
    ): StoredModeSettings {
        val framesPerSecond = updated.videoFrameRate.framesPerSecond
        val flashMode = when (updated.flashMode) {
            current.flashMode -> stored.flashMode
            else -> modeSettingsMapper.map(updated.flashMode)
        }
        val selfIllumination = when (updated.selfIllumination) {
            current.selfIllumination -> stored.selfIllumination
            else -> updated.selfIllumination
        }
        val withSwitches = stored.copy(
            flashMode = flashMode,
            selfIllumination = selfIllumination,
        )
        val withQuality = when {
            updated.videoQuality == current.videoQuality -> withSwitches

            isFrontFacing -> withSwitches.copy(
                videoQualityFront = storedVideoQualityMapper.map(updated.videoQuality),
            )

            else -> withSwitches.copy(
                videoQualityBack = storedVideoQualityMapper.map(updated.videoQuality),
            )
        }

        return when {
            updated.videoFrameRate == current.videoFrameRate -> withQuality
            isFrontFacing -> withQuality.copy(frameRateFront = framesPerSecond)
            else -> withQuality.copy(frameRateBack = framesPerSecond)
        }
    }

    private fun modeSettingsOf(
        prefs: SettingsPrefs,
        slot: ModeSlot,
    ): ModeSettings {
        return modeSettingsMapper.map(
            stored = prefs.mode(slot.mode),
            isFrontFacing = slot.isFrontFacing,
        )
    }

    private fun write(transform: (SettingsPrefs) -> SettingsPrefs): SettingsPrefs {
        val updated = prefs.updateAndGet(transform)

        _settings.value = cameraSettingsMapper.map(prefs.value.common)

        writeScope.launch(defaultDispatcher) {
            dataStore.updateData { prefs.value }
        }

        return updated
    }
}
